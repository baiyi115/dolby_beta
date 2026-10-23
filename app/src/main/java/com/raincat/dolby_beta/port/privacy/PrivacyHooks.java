package com.raincat.dolby_beta.port.privacy;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;

public final class PrivacyHooks {

    private PrivacyHooks() {
    }

    public static void install(Context context) {
        SettingHelper s = SettingHelper.getInstance();
        if (s == null) {
            return;
        }
        ClassLoader cl = context.getClassLoader();

        if (s.isPrivacyEnabled(SettingHelper.pv_oaid_key)) {
            installOaid(cl);
        }
        if (s.isPrivacyEnabled(SettingHelper.pv_log_tasks_key)) {
            installLogTasks(cl);
        }
        if (s.isPrivacyEnabled(SettingHelper.pv_apm_key)) {
            installApm(cl);
        }
        if (s.isPrivacyEnabled(SettingHelper.pv_stats_key)) {
            installStats(cl);
        }
        if (s.isPrivacyEnabled(SettingHelper.pv_clipboard_key)) {
            installClipboard();
        }
        // PV-03 needs an exact taskName list; not implemented.
    }

    // PV-01 OAID
    private static void installOaid(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.OAID_MANAGER, cl);
        if (cls != null) {
            // o(String) is the real entry: AdOaidUploadTask.init() calls it.
            
            Method o = PortKit.findMethod(cls, "o", String.class);
            PortKit.install("PV-01 MusicOaidUploadManager#o", o,
                    PortKit.skipVoid("MusicOaidUploadManager.o"));

            // low-level uploaders, kept as backstop
            for (String name : new String[]{"v", "p", "r"}) {
                Method m = PortKit.findMethod(cls, name, String.class);
                PortKit.install("PV-01 MusicOaidUploadManager#" + name, m,
                        PortKit.skipVoid("MusicOaidUploadManager." + name));
            }

            Method u = PortKit.findMethod(cls, "u");
            PortKit.install("PV-01 MusicOaidUploadManager#u", u,
                    PortKit.skipVoid("MusicOaidUploadManager.u"));
        }
        Class<?> task = PortKit.findClass(Profile9581.OAID_TASK, cl);
        if (task != null) {
            Method init = PortKit.findMethod(task, "init");
            PortKit.install("PV-01 AdOaidUploadTask#init", init,
                    PortKit.skipVoid("AdOaidUploadTask.init"));
        }
    }

    // PV-02 log-to-file tasks
    private static void installLogTasks(ClassLoader cl) {
        String[] tasks = {Profile9581.LOG_TO_FILE_TASK, Profile9581.NB_LOG_TASK};
        for (String name : tasks) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            Method init = PortKit.findMethod(cls, "init");
            PortKit.install("PV-02 " + simple(name) + "#init", init,
                    PortKit.skipVoid(simple(name) + ".init"));
        }
    }

    // PV-04 APM
    private static void installApm(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.STATISTIC_IMPL, cl);
        if (cls == null) {
            return;
        }
        // logApm / forceApmUpload only; init left intact.
        // fastjson is not a compile-time dependency; match by type name.
        Method logApm = PortKit.findMethodByParamTypeName(cls, "logApm", "com.alibaba.fastjson.JSONObject");
        PortKit.install("PV-04 StatisticImpl#logApm", logApm,
                PortKit.skipVoid("StatisticImpl.logApm"));
        Method force = PortKit.findMethod(cls, "forceApmUpload");
        PortKit.install("PV-04 StatisticImpl#forceApmUpload", force,
                PortKit.skipVoid("StatisticImpl.forceApmUpload"));
    }

    // PV-05 statistics
    private static void installStats(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.STATISTIC_IMPL, cl);
        if (cls != null) {
            // real uploads only; init and local logs untouched
            installNoArgVoid(cls, "forceUpload", "PV-05");
            installObjVoid(cls, "logMonitor", "PV-05");
            installObjVoid(cls, "logRealTimeMonitor", "PV-05");
        }
        // disable the sampling switch
        Class<?> monitor = PortKit.findClass(Profile9581.LOG_MONITOR, cl);
        if (monitor != null) {
            Method b = PortKit.findMethod(monitor, "b");
            PortKit.installBool("PV-05 LogMonitorConfigManager#b", b, false);
        }
    }

    // PV-06 clipboard
    private static void installClipboard() {
        try {
            Method m = ClipboardManager.class.getMethod("getPrimaryClip");
            PortKit.install("PV-06 ClipboardManager#getPrimaryClip", m, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    PortKit.hit("PV-06 clipboard blocked");
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedCompat.logDebug("PV-06 clipboard hook failed: " + t);
        }
    }

    // helpers
    private static void installNoArgVoid(Class<?> cls, String name, String tag) {
        Method m = PortKit.findMethod(cls, name);
        PortKit.install(tag + " " + cls.getSimpleName() + "#" + name, m,
                PortKit.skipVoid(cls.getSimpleName() + "." + name));
    }

    private static void installObjVoid(Class<?> cls, String name, String tag) {
        Method m = PortKit.findMethodByParamTypeName(cls, name, "com.alibaba.fastjson.JSONObject");
        PortKit.install(tag + " " + cls.getSimpleName() + "#" + name, m,
                PortKit.skipVoid(cls.getSimpleName() + "." + name));
    }

    private static String simple(String className) {
        int i = className.lastIndexOf('.');
        return i >= 0 ? className.substring(i + 1) : className;
    }
}
