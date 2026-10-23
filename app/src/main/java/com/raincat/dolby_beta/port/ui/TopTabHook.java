package com.raincat.dolby_beta.port.ui;

import android.content.Context;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TopTabHook {

    private TopTabHook() {
    }

    public static void install(Context context, Set<String> keepSet) {
        if (keepSet == null || keepSet.isEmpty()) {
            XposedCompat.logInfo("UI-04 keep list empty; filtering skipped");
            return;
        }
        ClassLoader cl = context.getClassLoader();

        // 1. data-source write point
        Class<?> adapter = PortKit.findClass(Profile9581.TOP_TAB_ADAPTER, cl);
        if (adapter != null) {
            Method t0 = PortKit.findMethod(adapter, "T0", List.class);
            PortKit.install("UI-04 dr.h#T0", t0, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object[] args = param.args;
                        if (args == null || args.length == 0 || !(args[0] instanceof List)) {
                            return;
                        }
                        List<Object> filtered = filter((List<?>) args[0], keepSet);
                        if (filtered != null) {
                            args[0] = filtered;
                        }
                    } catch (Throwable t) {
                        XposedCompat.logDebug("UI-04 T0 filter failed: " + t);
                    }
                }
            });
        }

        // 2. unlock delete/drag in the tab manager
        Class<?> tabInfo = PortKit.findClass(Profile9581.TOP_TAB_INFO, cl);
        if (tabInfo != null) {
            PortKit.installBool("UI-04 HomeTopTabInfo#getCanDelete",
                    PortKit.findMethod(tabInfo, "getCanDelete"), true);
            PortKit.installBool("UI-04 HomeTopTabInfo#getCanDrag",
                    PortKit.findMethod(tabInfo, "getCanDrag"), true);
        }
    }

    /**
     * 按标题保留。返回 null 表示无需改动（无匹配时保持原样，避免把 tab 全删光）。
     */
    static List<Object> filter(List<?> list, Set<String> keepSet) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            String title = titleOf(item);
            if (title != null && keepSet.contains(title)) {
                out.add(item);
            }
        }
        if (out.isEmpty() || out.size() >= list.size()) {
            if (out.isEmpty()) {
                XposedCompat.logInfo("UI-04 keepSet matched nothing; filtering skipped");
            }
            return null;
        }
        PortKit.hit("UI-04 filtered " + list.size() + " -> " + out.size());
        return out;
    }

    private static String titleOf(Object item) {
        if (item == null) {
            return null;
        }
        for (String getter : new String[]{"getTitle", "getTabName", "getName", "getCode"}) {
            try {
                Method m = item.getClass().getMethod(getter);
                m.setAccessible(true);
                Object v = m.invoke(item);
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isEmpty()) {
                        return s;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static Set<String> parseKeepSet(String raw) {
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
