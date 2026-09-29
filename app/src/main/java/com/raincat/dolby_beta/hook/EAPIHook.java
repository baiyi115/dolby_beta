package com.raincat.dolby_beta.hook;

import android.content.Context;
import android.text.TextUtils;

import com.raincat.dolby_beta.helper.EAPIHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

public class EAPIHook {
    /**
     * Upper bound for the response body we are willing to buffer. This runs on every /api/ and
     * /eapi/ response while the proxy or black-VIP switch is on, and the previous 20 MB peek could
     * buffer that much per in-flight request in both the main and :play processes.
     */
    private static final long MAX_BODY_BYTES = 2 * 1024 * 1024L;

    private final Context context;
    private Class<?> mediaTypeClass, responseBodyClass;

    public EAPIHook(final Context context) {
        this.context = context;
        Class<?> realCallClass = findClassIfExists("okhttp3.internal.connection.RealCall", context.getClassLoader());
        if (realCallClass == null)
            realCallClass = findClassIfExists("okhttp3.RealCall", context.getClassLoader());
        if (realCallClass == null) {
            XposedCompat.logError("EAPIHook RealCall class not found");
            return;
        }

        int installed = 0;
        MethodHook hook = new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                modifyResponse(param);
            }
        };
        installed += hookNamedMethod(realCallClass, "getResponseWithInterceptorChain$okhttp", hook);
        installed += hookNamedMethod(realCallClass, "getResponseWithInterceptorChain", hook);
        if (installed == 0)
            XposedCompat.logError("EAPIHook RealCall response method not found");

    }

    private int hookNamedMethod(Class<?> clazz, String name, MethodHook hook) {
        int count = 0;
        try {
            for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                if (!name.equals(method.getName()))
                    continue;
                XposedCompat.hookMethod(method, hook);
                count++;
            }
        } catch (Throwable t) {
            XposedCompat.log("EAPIHook install " + name + " failed");
            XposedCompat.log(t);
        }
        return count;
    }

    private void modifyResponse(MethodHook.MethodHookParam param) {
        Object response;
        try {
            response = param.getResult();
            if (response == null)
                return;
            Object request = XposedCompat.callMethod(response, "request");
            String path = requestPath(request);
            if (TextUtils.isEmpty(path) || !isSupportedPath(path))
                return;
            if (!SettingHelper.getInstance().isEnable(SettingHelper.black_key)
                    && !SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key))
                return;

            if (!needsFullBody(path))
                return;

            long declaredLength = contentLength(response);
            if (declaredLength > MAX_BODY_BYTES) {
                XposedCompat.logDebug("EAPIHook skipped oversized body: " + path
                        + " (" + declaredLength + "B)");
                return;
            }

            XposedCompat.logDebug("EAPIHook matched path: " + path);
            String raw;
            try {
                Object peekBody = XposedCompat.callMethod(response, "peekBody", MAX_BODY_BYTES);
                raw = (String) XposedCompat.callMethod(peekBody, "string");
            } catch (Throwable t) {
                XposedCompat.log("EAPIHook peek body failed: " + path);
                XposedCompat.log(t);
                return;
            }
            if (raw == null)
                return;
            if (raw.length() >= MAX_BODY_BYTES) {

                // peekBody() returned exactly the cap, so the real body is longer. Parsing the
                // truncated JSON cannot succeed, and reading on would defeat the memory bound.
                XposedCompat.logDebug("EAPIHook body hit the size cap, skipped: " + path);
                return;
            }
            String original = raw.trim();
            if (TextUtils.isEmpty(original) || original.charAt(0) != '{')
                return;

            String modified;
            try {
                modified = modifyByPath(path, request, original);
            } catch (Throwable t) {
                XposedCompat.log("EAPIHook modify failed: " + path);
                XposedCompat.log(t);
                return;
            }
            if (modified == null || modified.equals(original))
                return;
            XposedCompat.logDebug("EAPIHook response modified: " + path);

            Object rebuilt = rebuildResponse(response, modified);
            if (rebuilt != null)
                param.setResult(rebuilt);
        } catch (Throwable t) {
            XposedCompat.log("EAPIHook modifyResponse failed");
            XposedCompat.log(t);
        }
    }

    private Object rebuildResponse(Object response, String body) {
        try {
            Object builder = XposedCompat.callMethod(response, "newBuilder");
            Object mediaType = null;
            try {
                Object oldBody = XposedCompat.callMethod(response, "body");
                if (oldBody != null)
                    mediaType = XposedCompat.callMethod(oldBody, "contentType");
            } catch (Throwable ignored) {
            }
            if (mediaType == null) {
                if (mediaTypeClass == null)
                    mediaTypeClass = XposedCompat.findClass("okhttp3.MediaType", context.getClassLoader());
                mediaType = XposedCompat.callStaticMethod(mediaTypeClass, "get", "application/json; charset=utf-8");
            }
            if (responseBodyClass == null)
                responseBodyClass = XposedCompat.findClass("okhttp3.ResponseBody", context.getClassLoader());
            if (mediaType == null)
                return null;
            Object newBody = XposedCompat.callStaticMethod(responseBodyClass, "create", mediaType, body);
            if (newBody == null)
                return null;
            XposedCompat.callMethod(builder, "body", newBody);
            return XposedCompat.callMethod(builder, "build");
        } catch (Throwable t) {
            XposedCompat.log("EAPIHook rebuild response failed");
            XposedCompat.log(t);
            return null;
        }
    }

    private boolean isSupportedPath(String path) {
        return path.contains("/eapi/") || path.contains("/api/");
    }

    /**
     * Mirrors {@link #modifyByPath}: only endpoints that are actually rewritten may have their body
     * read. Everything else returns before peekBody(), which is what keeps ordinary API traffic
     * (banners, playlists, comments, ...) off the buffering path entirely.
     */
    private static boolean needsFullBody(String path) {
        return path.contains("song/enhance/player/url")
                || path.contains("song/enhance/download/url")
                || path.contains("sound/mobile")
                || path.contains("page=audio_effect");
    }

    /** Declared {@code Content-Length}, or -1 when the response does not advertise one. */
    private static long contentLength(Object response) {
        try {
            Object value = XposedCompat.callMethod(response, "header", "Content-Length");
            if (value instanceof String)
                return Long.parseLong(((String) value).trim());
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private String requestPath(Object request) {
        if (request == null)
            return "";
        Object url = XposedCompat.callMethod(request, "url");
        Object path = url == null ? null : XposedCompat.callMethod(url, "encodedPath");
        return path == null ? request.toString() : path.toString();
    }

    private String modifyByPath(String path, Object request, String original) throws Throwable {
        if (path.contains("song/enhance/player/url")) {
            return EAPIHelper.modifyPlayer(original);
        } else if (path.contains("song/enhance/download/url")) {
            // modifyPlayer() needs an array, so the single object is wrapped and unwrapped again.
            // The old code stripped every "[" / "]" from the serialized JSON instead, which also
            // removed brackets belonging to nested arrays and string values.
            JSONObject wrapped = new JSONObject(original);
            JSONObject dataObject = wrapped.getJSONObject("data");
            JSONArray single = new JSONArray();
            single.put(dataObject);
            wrapped.put("data", single);
            JSONObject modified = new JSONObject(EAPIHelper.modifyPlayer(wrapped.toString()));
            JSONArray modifiedArray = modified.optJSONArray("data");
            if (modifiedArray != null && modifiedArray.length() > 0) {
                JSONObject first = modifiedArray.optJSONObject(0);
                if (first != null)
                    modified.put("data", first);
            }
            return modified.toString();
        } else if (path.contains("sound/mobile") || path.contains("page=audio_effect")) {
            return EAPIHelper.modifyEffect(original);
        }
        return null;
    }
}
