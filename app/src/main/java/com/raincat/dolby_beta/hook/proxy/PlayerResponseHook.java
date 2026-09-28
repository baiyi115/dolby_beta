package com.raincat.dolby_beta.hook.proxy;

import android.content.Context;

import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

/** Player API response replacement: resolves the API request class and merges full-quality data over trials. */
public final class PlayerResponseHook {
    private final Context context;

    public PlayerResponseHook(Context context) {
        this.context = context;
    }

    public void install() {
        final Context context = this.context;
        final Class<?> requestClass = resolveApiRequestClass(context);
        if (requestClass == null) {
            XposedCompat.logError("ProxyHook API request class not found");
            return;
        }
        int installed = 0;
        for (final Method method : requestClass.getDeclaredMethods()) {
            if (!"p".equals(method.getName()))
                continue;
            if (method.getParameterTypes().length != 0
                    || !"org.json.JSONObject".equals(method.getReturnType().getName()))
                continue;
            XposedCompat.hookMethod(method, new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object result = param.getResult();
                    if (!(result instanceof JSONObject) || !isPlayerApiRequest(param.thisObject))
                        return;
                    Object replaced;
                    try {
                        replaced = replacePlayerJson(context, param.thisObject, (JSONObject) result);
                    } catch (Throwable t) {
                        XposedCompat.log("ProxyHook player JSON replace failed");
                        XposedCompat.log(t);
                        return;
                    }
                    if (replaced != null) {
                        param.setResult(replaced);
                    }
                }
            });
            installed++;
        }
        XposedCompat.logInfo("ProxyHook API response hook installed: " + requestClass.getName()
                + ", methods=" + installed);
        if (installed == 0)
            XposedCompat.logError("ProxyHook API response method p() not found");
    }

    private Class<?> resolveApiRequestClass(Context context) {
        Class<?> known = findClassIfExists("g42.a", context.getClassLoader());
        String source = "known-class";
        if (known == null) {
            for (String name : DexKitHelper.findApiRequestCandidates(context)) {
                Class<?> candidate = findClassIfExists(name, context.getClassLoader());
                if (candidate != null
                        && hasNoArgMethod(candidate, "p", "org.json.JSONObject")) {
                    known = candidate;
                    source = "dexkit";
                    break;
                }
            }
        }
        if (known != null)
            XposedCompat.logInfo("ProxyHook API request resolved: " + known.getName() + " (" + source + ")");
        return known;
    }

    private static boolean hasNoArgMethod(Class<?> clazz, String name, String returnType) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName())
                    && method.getParameterTypes().length == 0
                    && returnType.equals(method.getReturnType().getName()))
                return true;
        }
        return false;
    }

    private static boolean isPlayerApiRequest(Object apiRequest) {
        if (apiRequest == null)
            return false;
        Object request = XposedCompat.callMethod(apiRequest, "K");
        if (request == null) {
            Object uriText = XposedCompat.callMethod(apiRequest, "R");
            return uriText != null && uriText.toString().contains("song/enhance/player/url");
        }
        String path = ProxyTransport.requestPath(request);
        return path.contains("/eapi/song/enhance/player/url")
                || path.contains("song/enhance/player/url");
    }

    private JSONObject replacePlayerJson(Context context, Object apiRequest, JSONObject original) throws Exception {
        JSONArray data = original.optJSONArray("data");
        if (data == null || data.length() == 0 || !hasTrialData(data) || !ProxyTransport.isProxyActive())
            return null;

        Object originalRequest = XposedCompat.callMethod(apiRequest, "K");
        if (originalRequest == null) {
            logPlayerReplaceOnce("ProxyHook original Request unavailable, id/skip");
            return null;
        }
        Object request = buildProxiedPlayerRequest(context, originalRequest);
        if (request == null) {
            logPlayerReplaceOnce("ProxyHook proxied EAPI Request build failed");
            return null;
        }
        Object client = ProxyTransport.getProxiedOkHttpClient(context);
        Object call = XposedCompat.callMethod(client, "newCall", request);
        Object response = XposedCompat.callMethod(call, "execute");
        try {
            int code = TrialStateHook.intValue(XposedCompat.callMethod(response, "code"));
            if (code != 200) {
                logPlayerReplaceOnce("ProxyHook UNM response code=" + code);
                return null;
            }
            Object body = XposedCompat.callMethod(response, "body");
            String text = body == null ? null : (String) XposedCompat.callMethod(body, "string");
            if (text == null || text.length() == 0) {
                logPlayerReplaceOnce("ProxyHook UNM response empty");
                return null;
            }
            JSONObject proxied = new JSONObject(text);
            JSONObject merged = mergePlayerData(original, proxied);
            if (merged != null)
                logPlayerReplaceOnce("ProxyHook full player data merged: " + playerSummary(merged));
            return merged;
        } finally {
            XposedCompat.callMethod(response, "close");
        }
    }

    private static Object buildProxiedPlayerRequest(Context context, Object originalRequest) throws Exception {
        Object cleanRequest = ProxyTransport.removeTrialParams(originalRequest);
        Object url = XposedCompat.callMethod(cleanRequest, "url");
        if (url == null)
            return null;

        Object encodedPath = XposedCompat.callMethod(url, "encodedPath");
        String path = encodedPath instanceof String ? (String) encodedPath : url.toString();
        int queryStart = path.indexOf('?');
        if (queryStart >= 0)
            path = path.substring(0, queryStart);
        int fragmentStart = path.indexOf('#');
        if (fragmentStart >= 0)
            path = path.substring(0, fragmentStart);
        if (!path.startsWith("/eapi/"))
            return null;
        String apiPath = path.replaceFirst("^/eapi/", "/api/");

        JSONObject plainJson = new JSONObject();
        Object names = XposedCompat.callMethod(url, "queryParameterNames");
        if (names instanceof Iterable) {
            for (Object name : (Iterable<?>) names) {
                if (name == null)
                    continue;
                String key = name.toString();
                Object value = XposedCompat.callMethod(url, "queryParameter", key);
                if (value != null)
                    plainJson.put(key, value.toString());
            }
        } else {

            String fullUrl = url.toString();
            int q = fullUrl.indexOf('?');
            if (q >= 0) {
                String query = fullUrl.substring(q + 1);
                int hash = query.indexOf('#');
                if (hash >= 0)
                    query = query.substring(0, hash);
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0)
                        continue;
                    plainJson.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        plainJson.put("e_r", false);
        plainJson.put("header", "{}");

        ClassLoader loader = context.getClassLoader();
        Class<?> utilsClass = findClassIfExists(
                "com.netease.cloudmusic.utils.NeteaseMusicUtils", loader);
        if (utilsClass == null)
            return null;
        Object encrypted = XposedCompat.callStaticMethod(
                utilsClass, "serialdata", apiPath, plainJson.toString());
        if (!(encrypted instanceof String) || ((String) encrypted).length() == 0)
            return null;

        Class<?> formBuilderClass = findClassIfExists("okhttp3.FormBody$Builder", loader);
        if (formBuilderClass == null)
            return null;
        Object formBuilder = XposedCompat.newInstance(formBuilderClass);
        if (formBuilder == null)
            return null;
        XposedCompat.callMethod(formBuilder, "add", "params", encrypted);
        Object formBody = XposedCompat.callMethod(formBuilder, "build");
        if (formBody == null)
            return null;

        Object requestBuilder = XposedCompat.callMethod(cleanRequest, "newBuilder");
        if (requestBuilder == null)
            return null;
        XposedCompat.callMethod(requestBuilder, "post", formBody);
        Object request = XposedCompat.callMethod(requestBuilder, "build");
        if (request != null)
            logPlayerReplaceOnce("ProxyHook proxied EAPI request built: path="
                    + path + ", params=" + plainJson);
        return request;
    }

    private static boolean hasTrialData(JSONArray data) {
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null)
                continue;
            if (!item.isNull("freeTrialInfo")
                    || !item.isNull("freeTimeTrialPrivilege")
                    || !item.isNull("freeTrialPrivilege")
                    || item.optInt("br", 0) <= 1000)
                return true;
        }
        return false;
    }

    private static JSONObject mergePlayerData(JSONObject original, JSONObject proxied) throws Exception {
        JSONArray originalData = original.optJSONArray("data");
        JSONArray proxiedData = proxied.optJSONArray("data");
        if (originalData == null || proxiedData == null || originalData.length() == 0)
            return null;

        Map<Long, JSONObject> fullBySongId = new HashMap<>();
        for (int i = 0; i < proxiedData.length(); i++) {
            JSONObject item = proxiedData.optJSONObject(i);
            if (isFullPlayerData(item))
                fullBySongId.put(item.optLong("id"), copyPlayerData(item));
        }

        boolean changed = false;
        for (int i = 0; i < originalData.length(); i++) {
            JSONObject item = originalData.optJSONObject(i);
            if (item == null)
                continue;
            boolean trial = !item.isNull("freeTrialInfo")
                    || !item.isNull("freeTimeTrialPrivilege")
                    || !item.isNull("freeTrialPrivilege")
                    || item.optInt("br", 0) <= 1000;
            if (!trial)
                continue;
            JSONObject full = fullBySongId.get(item.optLong("id"));
            if (full == null)
                continue;
            originalData.put(i, full);
            TrialStateHook.rememberReplacedId(item.optLong("id"));
            changed = true;
        }
        return changed ? original : null;
    }

    private static boolean isFullPlayerData(JSONObject item) {
        if (item == null || item.optInt("code", 200) != 200)
            return false;
        String url = item.optString("url", null);
        if (url == null || url.length() == 0)
            return false;
        long size = item.optLong("size", 0);
        int br = item.optInt("br", 0);
        return item.isNull("freeTrialInfo")
                && item.isNull("freeTimeTrialPrivilege")
                && item.isNull("freeTrialPrivilege")
                && size > 600_000
                && br >= 128_000;
    }

    private static JSONObject copyPlayerData(JSONObject item) throws Exception {
        JSONObject out = new JSONObject(item.toString());
        out.remove("freeTrialInfo");
        out.remove("freeTimeTrialPrivilege");
        out.remove("freeTrialPrivilege");
        out.remove("trialMode");
        out.remove("trialModes");
        out.put("fee", 0);
        out.put("payed", 0);
        out.put("flag", item.optInt("flag") & 0x8);
        return out;
    }

    private static String playerSummary(JSONObject json) {
        try {
            JSONArray data = json.optJSONArray("data");
            if (data == null || data.length() == 0)
                return "no-data";
            JSONObject item = data.optJSONObject(0);
            if (item == null)
                return "no-item";
            String url = item.optString("url", "");
            int slash = url.indexOf("//");
            int first = url.indexOf('/', slash + 2);
            String host = slash >= 0 && first > slash ? url.substring(slash + 2, first) : url;
            return String.format(Locale.US,
                    "id=%d,urlHost=%s,size=%d,br=%d,free=%b",
                    item.optLong("id"), host, item.optLong("size"), item.optInt("br"),
                    !item.isNull("freeTrialInfo"));
        } catch (Throwable t) {
            return "summary-failed";
        }
    }

    private static void logPlayerReplaceOnce(String message) {
        if (TrialStateHook.markOnce(message))
            XposedCompat.logInfo(message);
    }
}
