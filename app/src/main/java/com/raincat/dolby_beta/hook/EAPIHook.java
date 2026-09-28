package com.raincat.dolby_beta.hook;

import android.content.Context;
import android.text.TextUtils;

import com.raincat.dolby_beta.helper.EAPIHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

public class EAPIHook {
    private final Context context;
    private Class<?> bufferClass, mediaTypeClass, responseBodyClass;

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

            XposedCompat.logDebug("EAPIHook matched path: " + path);
            String original;
            try {
                Object peekBody = XposedCompat.callMethod(response, "peekBody", 20 * 1024 * 1024L);
                original = (String) XposedCompat.callMethod(peekBody, "string");
            } catch (Throwable t) {
                XposedCompat.log("EAPIHook peek body failed: " + path);
                XposedCompat.log(t);
                return;
            }
            original = original == null ? "" : original.trim();
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
            JSONObject jsonObject = new JSONObject(original);
            JSONObject object = jsonObject.getJSONObject("data");
            JSONArray array = new JSONArray();
            array.put(object);
            jsonObject.put("data", array);
            return EAPIHelper.modifyPlayer(jsonObject.toString())
                    .replace("[", "").replace("]", "");
        } else if (path.contains("sound/mobile") || path.contains("page=audio_effect")) {
            return EAPIHelper.modifyEffect(original);
        }
        return null;
    }

    private HashMap<String, String> getRequestParams(Object request) throws IOException {
        HashMap<String, String> params = new LinkedHashMap<>();
        try {
            Object requestBody = XposedCompat.callMethod(request, "body");
            if (requestBody == null)
                return params;
            if (bufferClass == null)
                bufferClass = XposedCompat.findClass("okio.Buffer", context.getClassLoader());
            Object buffer = XposedCompat.newInstance(bufferClass);
            XposedCompat.callMethod(requestBody, "writeTo", buffer);
            String body = (String) XposedCompat.callMethod(buffer, "readUtf8");
            for (String pair : body.split("&")) {
                int idx = pair.indexOf('=');
                if (idx <= 0)
                    continue;
                String name = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                params.put(name, value);
            }
        } catch (Throwable t) {
            XposedCompat.log("EAPIHook getRequestParams failed");
            XposedCompat.log(t);
        }
        return params;
    }
}
