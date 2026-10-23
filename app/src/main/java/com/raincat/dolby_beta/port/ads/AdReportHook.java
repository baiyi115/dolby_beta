package com.raincat.dolby_beta.port.ads;

import android.content.Context;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;

public final class AdReportHook {

    private AdReportHook() {
    }

    public static void install(Context context) {
        ClassLoader cl = context.getClassLoader();

        // 1. ad report task
        Class<?> reportTask = PortKit.findClass(Profile9581.AD_REPORT_TASK, cl);
        if (reportTask != null) {
            Method execute = PortKit.findMethod(reportTask, "execute");
            PortKit.installBool("AD-03 AdCloudReportTask#execute", execute, true);
        }

        // 2. silent external launch; startApp is 4-arg on 9.5.81
        Class<?> startAppB = PortKit.findClass(Profile9581.START_APP_B, cl);
        if (startAppB != null) {
            int hooked = 0;
            for (Method m : startAppB.getDeclaredMethods()) {
                if (!"startApp".equals(m.getName())) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                if (rt != boolean.class && rt != Boolean.class) {
                    continue;
                }
                if (PortKit.installBool("AD-03 StartAppHelper$b#startApp("
                        + m.getParameterTypes().length + ")", m, false)) {
                    hooked++;
                }
            }
            if (hooked == 0) {
                XposedCompat.logDebug("AD-03 StartAppHelper$b.startApp not found");
            }
        }

        // 3. exposure deeplink pre-request
        Class<?> deeplinkPre = PortKit.findClass(Profile9581.DEEPLINK_PRE, cl);
        if (deeplinkPre != null) {
            Method init = PortKit.findMethod(deeplinkPre, "init");
            PortKit.install("AD-03 AdExposureDeeplinkPreRequestTask#init", init,
                    PortKit.skipVoid("AdExposureDeeplinkPreRequestTask.init"));
        }

        // 4. exposure deeplink
        Class<?> deeplink = PortKit.findClass(Profile9581.DEEPLINK_TASK, cl);
        if (deeplink != null) {
            Method init = PortKit.findMethod(deeplink, "init");
            PortKit.install("AD-03 AdExposureDeeplinkTask#init", init,
                    PortKit.skipVoid("AdExposureDeeplinkTask.init"));
        }
    }
}
