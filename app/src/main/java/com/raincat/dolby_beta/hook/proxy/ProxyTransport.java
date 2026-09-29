package com.raincat.dolby_beta.hook.proxy;

import android.content.Context;

import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.net.HTTPSTrustManager;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;
import static com.raincat.dolby_beta.xposed.XposedCompat.hookAllConstructors;
import static com.raincat.dolby_beta.xposed.XposedCompat.hookAllMethods;

/** Network path of the proxy hook: RealCall, OkHttp builders, Cronet fallback and per-client field switching. */
public final class ProxyTransport {
    private static final String KNOWN_CRONET_INTERCEPTOR = "com.netease.cloudmusic.network.cronet.d";

    private static SSLSocketFactory socketFactory;
    private static boolean trialParamsLogged;
    private static boolean cronetProxyLogged;
    private static final Map<String, Object> PROXIED_OKHTTP_CLIENTS =
            Collections.synchronizedMap(new HashMap<>());
    private static final int PROXIED_CLIENT_LIMIT = 4;
    private static final Map<Object, Object> ORIGINAL_PROXIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> ORIGINAL_SSL_FACTORIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> ORIGINAL_PROXY_SELECTORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Context context;
    private final String fieldSSLSocketFactory;
    private final String fieldProxy;
    private final Class<?> cronetInterceptorClass;
    private static final List<String> whiteUrlList = Arrays.asList(
            "song/enhance/player/url",
            "song/enhance/download/url",
            "/package"
    );

    public ProxyTransport(Context context, String fieldSSLSocketFactory, String fieldProxy,
                          Class<?> cronetInterceptorClass) {
        this.context = context;
        this.fieldSSLSocketFactory = fieldSSLSocketFactory;
        this.fieldProxy = fieldProxy;
        this.cronetInterceptorClass = cronetInterceptorClass;
    }

    public void install(Class<?> realCallClass) {
        hookRealCallConstructor(context, realCallClass);
        hookOkHttpBuilderInterceptor(context);
        hookCronetIntercept(context);
    }

    private void hookRealCallConstructor(final Context context, Class<?> realCallClass) {
        if (realCallClass == null) {
            XposedCompat.logError("ProxyHook RealCall class not found");
            return;
        }
        hookAllConstructors(realCallClass, new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // This hook fires once per HTTP request in the whole app, so bail out on the
                // cheapest possible check before touching the request URL at all.
                if (!SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key))
                    return;
                if (param.args.length != 3)
                    return;
                Object client = param.args[0];
                Object request = param.args[1];
                logSidebarRequestOnce(request);
                if (!isMediaRequest(request))
                    return;
                logProxyStateForRequest();
                // Only rewrite the request while the proxy is actually serving. setProxy() is still
                // called unconditionally: its inactive branch restores the client's original
                // proxy/SSL fields, which is what un-breaks networking after the switch is turned off.
                if (isProxyActive())
                    param.args[1] = removeTrialParams(request);
                try {
                    setProxy(context, client);
                } catch (Throwable t) {
                    XposedCompat.log("ProxyHook setProxy failed");
                    XposedCompat.log(t);
                }
            }
        });
    }

    private void hookOkHttpBuilderInterceptor(Context context) {
        Class<?> builderClass = findClassIfExists("okhttp3.OkHttpClient$Builder", context.getClassLoader());
        if (builderClass == null) {
            XposedCompat.logError("ProxyHook OkHttpClient.Builder not found");
            return;
        }
        MethodHook cronetSkipHook = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
            if (param.args == null || param.args.length != 1 || param.args[0] == null)
                return;
                Object interceptor = param.args[0];
                if (!isCronetInterceptor(interceptor.getClass()))
                    return;
                Object builder = param.thisObject;
                if (builder != null) {
                    param.setResult(builder);
                    XposedCompat.logDebug("ProxyHook cronet interceptor skipped: class=" + interceptor.getClass().getName());
                }
            }
        };
        hookAllMethods(builderClass, "addInterceptor", cronetSkipHook);
        hookAllMethods(builderClass, "addNetworkInterceptor", cronetSkipHook);
    }

    private void hookCronetIntercept(final Context context) {
        if (cronetInterceptorClass == null) {
            XposedCompat.logError("ProxyHook Cronet interceptor class not found; proxy fallback disabled");
            return;
        }
        int installed = 0;
        for (final Method method : cronetInterceptorClass.getDeclaredMethods()) {
            if (!"intercept".equals(method.getName()))
                continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 1 || !"okhttp3.Interceptor$Chain".equals(types[0].getName()))
                continue;
            if (!"okhttp3.Response".equals(method.getReturnType().getName()))
                continue;
            final Method interceptedMethod = method;
            XposedCompat.hookMethod(interceptedMethod, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Object chain = param.args == null || param.args.length == 0 ? null : param.args[0];
                    Object request = chain == null ? null : XposedCompat.callMethod(chain, "request");
                    if (!isMediaRequest(request))
                        return;
                    if (!isProxyActive()) {
                        logProxyStateForRequest();
                        return;
                    }

                    Object proxiedRequest = removeTrialParams(request);
                    Object response = null;
                    try {
                        Object client = getProxiedOkHttpClient(context);
                        Object call = XposedCompat.callMethod(client, "newCall", proxiedRequest);
                        response = XposedCompat.callMethod(call, "execute");
                    } catch (Throwable t) {
                        XposedCompat.log("ProxyHook Cronet media fallback failed: "
                                + interceptedMethod);
                        XposedCompat.log(t);
                    }
                    if (response != null) {
                        param.setResult(response);
                        if (!cronetProxyLogged) {
                            cronetProxyLogged = true;
                            XposedCompat.logInfo("ProxyHook Cronet media request executed by proxy client");
                        }
                    }
                }
            });
            installed++;
        }
        if (installed == 0)
            XposedCompat.logError("ProxyHook Cronet intercept method not found");
    }

    public static Class<?> resolveCronetInterceptor(Context context) {

        Class<?> clazz = findClassIfExists(KNOWN_CRONET_INTERCEPTOR, context.getClassLoader());
        String source = "known-class";
        if (clazz == null) {
            List<String> candidates = DexKitHelper.findCronetInterceptorCandidates(context);
            for (String name : candidates) {
                Class<?> candidate = findClassIfExists(name, context.getClassLoader());
                if (isCronetInterceptorClass(candidate)) {
                    clazz = candidate;
                    source = "dexkit";
                    break;
                }
            }
        }
        if (clazz != null)
            XposedCompat.logInfo("ProxyHook Cronet interceptor resolved: " + clazz.getName() + " (" + source + ")");
        return clazz;
    }

    private static boolean isCronetInterceptorClass(Class<?> clazz) {
        if (clazz == null || clazz.isInterface())
            return false;
        boolean implementsInterceptor = false;
        for (Class<?> i : clazz.getInterfaces()) {
            if ("okhttp3.Interceptor".equals(i.getName()))
                implementsInterceptor = true;
        }
        if (!implementsInterceptor)
            return false;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!"intercept".equals(method.getName()))
                continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1
                    && "okhttp3.Interceptor$Chain".equals(types[0].getName())
                    && "okhttp3.Response".equals(method.getReturnType().getName()))
                return true;
        }
        return false;
    }

    private boolean isCronetInterceptor(Class<?> clazz) {
        if (clazz == null) return false;

        return clazz == cronetInterceptorClass || KNOWN_CRONET_INTERCEPTOR.equals(clazz.getName());
    }

    static boolean isProxyActive() {
        return SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key)
                && "1".equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS));
    }

    static Object removeTrialParams(Object request) {
        try {
            Object oldUrl = XposedCompat.callMethod(request, "url");
            if (oldUrl == null)
                return request;
            String oldUrlString = oldUrl.toString();
            // Cheap pre-check: the loop below splits on a regex pattern, so skip it when there is
            // nothing to remove (the common case for every media request).
            if (!oldUrlString.contains("trialMode"))
                return request;
            int queryStart = oldUrlString.indexOf('?');
            if (queryStart < 0)
                return request;
            String oldQuery = oldUrlString.substring(queryStart + 1);
            if (oldQuery == null || oldQuery.isEmpty())
                return request;
            String fragment = "";
            int fragmentStart = oldQuery.indexOf('#');
            if (fragmentStart >= 0) {
                fragment = oldQuery.substring(fragmentStart);
                oldQuery = oldQuery.substring(0, fragmentStart);
            }
            StringBuilder newQuery = new StringBuilder();
            boolean changed = false;
            for (String pair : oldQuery.split("&")) {
                if (pair == null || pair.isEmpty())
                    continue;
                int eq = pair.indexOf('=');
                String name = eq > 0 ? pair.substring(0, eq) : pair;
                String value = eq > 0 ? pair.substring(eq + 1) : "";
                if ("trialMode".equals(name) || "trialModes".equals(name)) {
                    changed = true;
                    continue;
                }
                if (newQuery.length() > 0)
                    newQuery.append('&');
                newQuery.append(pair);
            }
            if (!changed)
                return request;
            if (!trialParamsLogged) {
                trialParamsLogged = true;
                XposedCompat.logInfo("ProxyHook trial params removed, url=" + oldUrlString);
            }
            String newUrlString = oldUrlString.substring(0, queryStart)
                    + (newQuery.length() > 0 ? "?" + newQuery : "")
                    + fragment;
            Object newRequest = XposedCompat.callMethod(request, "newBuilder");
            XposedCompat.callMethod(newRequest, "url", newUrlString);
            return XposedCompat.callMethod(newRequest, "build");
        } catch (Throwable t) {
            XposedCompat.log("ProxyHook removeTrialParams failed");
            XposedCompat.log(t);
            return request;
        }
    }

    private static boolean isMediaRequest(Object request) {
        if (request == null)
            return false;
        String path = requestPath(request);
        for (String url : whiteUrlList)
            if (path.contains(url))
                return true;
        return false;
    }

    /** Flipped after the first sidebar request is reported: later requests skip URL work entirely. */
    private static volatile boolean sidebarRequestLogged;

    private static void logSidebarRequestOnce(Object request) {
        if (sidebarRequestLogged)
            return;
        try {
            String path = requestPath(request);
            if (path == null || !path.toLowerCase(java.util.Locale.US).contains("sidebar"))
                return;
            sidebarRequestLogged = true;
            XposedCompat.logInfo("ProxyHook sidebar request: " + path);
        } catch (Throwable ignored) {
        }
    }

    static String requestPath(Object request) {
        if (request == null)
            return "";
        try {
            Object url = XposedCompat.callMethod(request, "url");
            if (url != null)
                return url.toString();
        } catch (Throwable ignored) {
        }
        return request.toString();
    }

    /** Cached per client class: resolving these walks every declared field of the hierarchy. */
    private static final Map<Class<?>, Field[]> PROXY_FIELDS =
            Collections.synchronizedMap(new HashMap<>());

    private Field[] proxyFields(Class<?> clientClass) {
        Field[] cached = PROXY_FIELDS.get(clientClass);
        if (cached != null)
            return cached;
        Field[] resolved = new Field[]{
                findFieldInHierarchy(clientClass, fieldSSLSocketFactory),
                findFieldInHierarchy(clientClass, fieldProxy),
                findFieldByExactType(clientClass, ProxySelector.class)
        };
        PROXY_FIELDS.put(clientClass, resolved);
        return resolved;
    }

    /** Rebuilt only when the target changes: setProxy runs on every media request. */
    private static final class ProxyTarget {
        final String host;
        final int port;
        final Proxy proxy;
        final FixedProxySelector selector;

        ProxyTarget(String host, int port) {
            this.host = host;
            this.port = port;
            InetSocketAddress address = new InetSocketAddress(host, port);
            this.proxy = new Proxy(Proxy.Type.HTTP, address);
            this.selector = new FixedProxySelector(address);
        }
    }

    private static ProxyTarget cachedProxyTarget;
    private static boolean setProxyLogged;

    private static synchronized ProxyTarget proxyTarget(String host, int port) {
        ProxyTarget current = cachedProxyTarget;
        if (current == null || !current.host.equals(host) || current.port != port)
            cachedProxyTarget = current = new ProxyTarget(host, port);
        return current;
    }

    private void setProxy(Context context, Object client) throws Exception {
        Field[] fields = proxyFields(client.getClass());
        Field sslSocketFactoryField = fields[0];
        Field proxyField = fields[1];
        Field proxySelectorField = fields[2];
        if (sslSocketFactoryField == null || proxyField == null) {
            XposedCompat.log("ProxyHook okhttp fields not found: client=" + client.getClass().getName()
                    + ", ssl=" + fieldSSLSocketFactory + ", proxy=" + fieldProxy);
            return;
        }
        if (!ORIGINAL_PROXIES.containsKey(client))
            ORIGINAL_PROXIES.put(client, proxyField.get(client));
        if (!ORIGINAL_SSL_FACTORIES.containsKey(client))
            ORIGINAL_SSL_FACTORIES.put(client, sslSocketFactoryField.get(client));
        if (proxySelectorField != null && !ORIGINAL_PROXY_SELECTORS.containsKey(client))
            ORIGINAL_PROXY_SELECTORS.put(client, proxySelectorField.get(client));

        if (isProxyActive()) {
            String host = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)
                    ? SettingHelper.getInstance().getHttpProxy() : "127.0.0.1";
            int port = SettingHelper.getInstance().getProxyPort();
            ProxyTarget target = proxyTarget(host, port);
            proxyField.set(client, target.proxy);
            if (proxySelectorField != null)
                proxySelectorField.set(client, target.selector);
            if (socketFactory == null)
                socketFactory = ScriptHelper.getSSLSocketFactory(context);
            if (socketFactory != null)
                sslSocketFactoryField.set(client, socketFactory);
            if (!setProxyLogged) {
                setProxyLogged = true;
                XposedCompat.logInfo("ProxyHook setProxy host=" + host + ", port=" + port
                        + ", scriptStatus=" + ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS));
            }
        } else {
            proxyField.set(client, ORIGINAL_PROXIES.get(client));
            Object originalFactory = ORIGINAL_SSL_FACTORIES.get(client);
            if (originalFactory != null)
                sslSocketFactoryField.set(client, originalFactory);
            if (proxySelectorField != null) {
                Object originalSelector = ORIGINAL_PROXY_SELECTORS.get(client);
                if (originalSelector != null)
                    proxySelectorField.set(client, originalSelector);
            }
        }
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Field findFieldByExactType(Class<?> clazz, Class<?> type) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    static Object getProxiedOkHttpClient(Context context) throws Exception {
        String host = SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)
                ? SettingHelper.getInstance().getHttpProxy() : "127.0.0.1";
        int port = SettingHelper.getInstance().getProxyPort();
        final String key = host + ":" + port;
        synchronized (PROXIED_OKHTTP_CLIENTS) {
            Object cached = PROXIED_OKHTTP_CLIENTS.get(key);
            if (cached != null)
                return cached;

            ClassLoader loader = context.getClassLoader();
            Class<?> builderClass = findClassIfExists("okhttp3.OkHttpClient$Builder", loader);
            if (builderClass == null)
                throw new IllegalStateException("okhttp3.OkHttpClient$Builder not found");
            Object builder = XposedCompat.newInstance(builderClass);
            XposedCompat.callMethod(builder, "proxy",
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));

            // This client only replays the player request against the local/node proxy. Without a
            // call timeout a black-holed proxy would block the hooked response thread until the
            // platform default, so bound every stage explicitly.
            XposedCompat.callMethod(builder, "connectTimeout", 5L, java.util.concurrent.TimeUnit.SECONDS);
            XposedCompat.callMethod(builder, "readTimeout", 10L, java.util.concurrent.TimeUnit.SECONDS);
            XposedCompat.callMethod(builder, "writeTimeout", 10L, java.util.concurrent.TimeUnit.SECONDS);
            XposedCompat.callMethod(builder, "callTimeout", 15L, java.util.concurrent.TimeUnit.SECONDS);

            SSLSocketFactory socketFactory = ScriptHelper.getSSLSocketFactory(context);
            if (socketFactory != null) {
                XposedCompat.callMethod(builder, "sslSocketFactory",
                        socketFactory, new HTTPSTrustManager());
            }
            XposedCompat.callMethod(builder, "hostnameVerifier", (HostnameVerifier) (hostname, session) -> true);
            Object client = XposedCompat.callMethod(builder, "build");
            if (client == null)
                throw new IllegalStateException("proxied OkHttpClient build failed");
            // Keyed by host:port, so a user editing the proxy setting would otherwise accumulate one
            // live OkHttpClient (with its dispatcher threads) per distinct value.
            synchronized (PROXIED_OKHTTP_CLIENTS) {
                if (PROXIED_OKHTTP_CLIENTS.size() >= PROXIED_CLIENT_LIMIT)
                    PROXIED_OKHTTP_CLIENTS.clear();
                PROXIED_OKHTTP_CLIENTS.put(key, client);
            }
            XposedCompat.logInfo("ProxyHook proxy client ready: " + key);
            return client;
        }
    }

    /** Warned already, or the proxy is up: skip the diagnostic without touching settings/extras. */
    private static volatile boolean proxyStateWarned;

    private static void logProxyStateForRequest() {
        if (proxyStateWarned || isProxyActive())
            return;
        proxyStateWarned = true;
        XposedCompat.logInfo("ProxyHook media request skipped: scriptStatus="
                + ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS)
                + ", proxyMaster=" + SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)
                + ", master=" + SettingHelper.getInstance().getSetting(SettingHelper.master_key));
    }

    private static final class FixedProxySelector extends ProxySelector {
        private final InetSocketAddress address;

        private FixedProxySelector(InetSocketAddress address) {
            this.address = address;
        }

        @Override
        public java.util.List<Proxy> select(URI uri) {
            if (uri == null)
                throw new IllegalArgumentException("uri must not be null");
            return Collections.singletonList(new Proxy(Proxy.Type.HTTP, address));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        }
    }
}
