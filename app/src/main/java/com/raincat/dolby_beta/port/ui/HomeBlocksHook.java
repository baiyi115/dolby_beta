package com.raincat.dolby_beta.port.ui;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.content.Context;

public final class HomeBlocksHook {

    private static final String KEY_HIDE = "β_ui_home_blocks_filter_key";
    private static final int MAX_JSON_CHARS = 16 * 1024 * 1024;

    private HomeBlocksHook() {
    }

    public static void install(Context context, Set<String> hideSet) {
        if (hideSet == null || hideSet.isEmpty()) {
            XposedCompat.logInfo("UI-03 hide list empty; filtering skipped");
            return;
        }
        ClassLoader cl = context.getClassLoader();
        hookMixContainer(cl, hideSet);
        hookDiscoveryBlock(cl, hideSet);
    }

    private static void hookMixContainer(ClassLoader cl, Set<String> hideSet) {
        Class<?> cls = PortKit.findClass(Profile9581.MIX_CONTAINER, cl);
        if (cls == null) {
            return;
        }

        Method order = PortKit.findMethod(cls, "getBlockCodeOrderList");
        PortKit.install("UI-03 MixContainerData#getBlockCodeOrderList", order, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.result instanceof String)) {
                        return;
                    }
                    String json = (String) param.result;
                    String filtered = filterCodeList(json, hideSet);
                    if (filtered != null) {
                        param.result = filtered;
                    }
                } catch (Throwable t) {
                    XposedCompat.logDebug("UI-03 filter order list failed: " + t);
                }
            }
        });

        Method resources = PortKit.findMethod(cls, "getResources");
        PortKit.install("UI-03 MixContainerData#getResources", resources, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.result instanceof List)) {
                        return;
                    }
                    List<Object> out = filterResourceList((List<?>) param.result, hideSet);
                    if (out != null) {
                        param.result = out;
                    }
                } catch (Throwable t) {
                    XposedCompat.logDebug("UI-03 filter resources failed: " + t);
                }
            }
        });
    }

    private static void hookDiscoveryBlock(ClassLoader cl, Set<String> hideSet) {
        Class<?> cls = PortKit.findClass(Profile9581.DISCOVERY_BLOCK, cl);
        if (cls == null) {
            return;
        }
        Method getBlockList = PortKit.findMethod(cls, "getBlockList");
        PortKit.install("UI-03 DiscoveryBlockData#getBlockList", getBlockList, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!(param.result instanceof List)) {
                        return;
                    }
                    List<?> list = (List<?>) param.result;
                    List<Object> out = new ArrayList<>(list.size());
                    int removed = 0;
                    for (Object item : list) {
                        String code = blockCodeOf(item);
                        if (code != null && hideSet.contains(code)) {
                            removed++;
                            continue;
                        }
                        out.add(item);
                    }
                    if (removed > 0) {
                        PortKit.hit("UI-03 getBlockList removed " + removed);
                        param.result = out;
                    }
                } catch (Throwable t) {
                    XposedCompat.logDebug("UI-03 getBlockList failed: " + t);
                }
            }
        });
    }

    // pure logic

    /** Returns a JSON array string such as ["PAGE_RECOMMEND_X", ...]. */
    static String filterCodeList(String json, Set<String> hideSet) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray out = new JSONArray();
            int removed = 0;
            for (int i = 0; i < arr.length(); i++) {
                String code = arr.optString(i, "");
                if (code != null && hideSet.contains(code)) {
                    removed++;
                    continue;
                }
                out.put(code);
            }
            if (removed == 0) {
                return null;
            }
            PortKit.hit("UI-03 order list removed " + removed + "/" + arr.length());
            return out.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<Object> filterResourceList(List<?> list, Set<String> hideSet) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<Object> out = new ArrayList<>(list.size());
        int removed = 0;
        for (Object item : list) {
            if (matchesHide(item, hideSet)) {
                removed++;
                continue;
            }
            out.add(item);
        }
        if (removed == 0) {
            return null;
        }
        PortKit.hit("UI-03 resources removed " + removed + "/" + list.size());
        return out;
    }

    private static boolean matchesHide(Object item, Set<String> hideSet) {
        if (item == null) {
            return false;
        }
        if (item instanceof JSONObject) {
            JSONObject o = (JSONObject) item;
            String code = o.optString("blockCode", null);
            if (code == null || code.isEmpty()) {
                code = o.optString("block_code", null);
            }
            return code != null && hideSet.contains(code);
        }
        for (String getter : new String[]{"getBlockCode", "getCode", "getBlockCodeStr"}) {
            Object v = invokeNoArg(item, getter);
            if (v != null && hideSet.contains(v.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    private static String blockCodeOf(Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof JSONObject) {
            return ((JSONObject) item).optString("blockCode", null);
        }
        Object v = invokeNoArg(item, "getBlockCode");
        return v == null ? null : v.toString();
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
        }
        try {
            Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Parses the comma-separated hide list. */
    public static Set<String> parseHideSet(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
