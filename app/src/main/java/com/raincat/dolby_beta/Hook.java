package com.raincat.dolby_beta;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.FileHelper;
import com.raincat.dolby_beta.helper.NotificationHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.hook.AdAndUpdateHook;
import com.raincat.dolby_beta.hook.AdExtraHook;
import com.raincat.dolby_beta.hook.BlackHook;
import com.raincat.dolby_beta.hook.CdnHook;
import com.raincat.dolby_beta.hook.DownloadMD5Hook;
import com.raincat.dolby_beta.hook.EAPIHook;
import com.raincat.dolby_beta.hook.HideTabHook;
import com.raincat.dolby_beta.hook.ProxyHook;
import com.raincat.dolby_beta.hook.RnSettingEntryHook;
import com.raincat.dolby_beta.hook.SettingHook;
import com.raincat.dolby_beta.hook.UserProfileHook;
import com.raincat.dolby_beta.utils.Tools;

import java.io.File;
import java.io.IOException;

public class Hook {
    private final static String PACKAGE_NAME = "com.netease.cloudmusic";

    private static void safeHook(String name, Runnable init) {
        try {
            init.run();
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("init " + name, t);
        }
    }

    public static void installCrashLogger() {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            XposedCompat.log("PROCESS UNCAUGHT EXCEPTION on thread " + thread.getName());
            XposedCompat.log(throwable);
            if (prev != null)
                prev.uncaughtException(thread, throwable);
        });
    }

    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;

    private final String msg_hook_play_process = "hookPlayProcess";

    private final String msg_play_process_init_finish = "playProcessInitFinish";

    public static final String msg_send_notification = "sendNotification";

    public Hook(ClassLoader classLoader) {
        XposedCompat.findAndHookMethod(XposedCompat.findClass("com.netease.cloudmusic.NeteaseMusicApplication", classLoader),
                "attachBaseContext", Context.class, new MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        final long hookStart = System.currentTimeMillis();
                        final int versionCode = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionCode;

                        ExtraHelper.init(context);

                        SettingHelper.init(context);

                        // Runtime tracing switch: dropping this file keeps full debug logs available
                        // on a device build without rebuilding the module.
                        java.io.File debugFlag = new java.io.File(
                                context.getExternalFilesDir(null), "dolby_beta_debug");
                        if (debugFlag.exists()) {
                            XposedCompat.setDebug(true);
                            XposedCompat.logInfo("debug tracing enabled: " + debugFlag.getAbsolutePath());
                        }

                        final String processName = Tools.getCurrentProcessName(context);
                        XposedCompat.logInfo("attachBaseContext: process=" + processName
                                + " versionCode=" + versionCode
                                + " master=" + SettingHelper.getInstance().getSetting(SettingHelper.master_key));
                        if (processName.equals(PACKAGE_NAME)) {
                            installCrashLogger();

                            installAll(alwaysSpecs(context, versionCode));

                            if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                                XposedCompat.logInfo("master switch off, hooks skipped");
                                return;
                            }
                            installAll(immediateSpecs(context, versionCode));
                            XposedCompat.logInfo("main-process immediate hooks installed, elapsed="
                                    + (System.currentTimeMillis() - hookStart) + "ms");
                            XposedCompat.logSummary("main-process immediate hooks");
                            ClassHelper.getCacheClassList(context, versionCode, () -> {
                                installAll(deferredSpecs(context, versionCode));

                                XposedCompat.logInfo("main-process deferred hooks ready, elapsed="
                                        + (System.currentTimeMillis() - hookStart) + "ms");
                                XposedCompat.logSummary("main-process deferred hooks");
                                mainProcessInit = true;
                                if (mainProcessInit && playProcessInit)
                                    context.sendBroadcast(new Intent(msg_hook_play_process));
                            });
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_play_process_init_finish);
                            intentFilter.addAction(msg_send_notification);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_play_process_init_finish.equals(intent.getAction())) {
                                        XposedCompat.logInfo("broadcast received: " + msg_play_process_init_finish);
                                        playProcessInit = true;
                                        if (mainProcessInit && playProcessInit)
                                            context.sendBroadcast(new Intent(msg_hook_play_process));
                                    } else if (msg_send_notification.equals(intent.getAction())) {
                                        XposedCompat.logInfo("notification requested: code=" + intent.getIntExtra("code", 0x10)
                                                + " title=" + intent.getStringExtra("title"));
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            NotificationHelper.getInstance(context).sendUnLockNotification(context, intent.getIntExtra("code", 0x10),
                                                    intent.getStringExtra("title"), intent.getStringExtra("title"), intent.getStringExtra("message"));
                                        XposedCompat.logInfo(intent.getStringExtra("title") + "：" + intent.getStringExtra("message"));
                                    }
                                }
                            }, intentFilter);
                        } else if (processName.equals(PACKAGE_NAME + ":play") && SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                            installCrashLogger();
                            XposedCompat.logInfo("play-process installing");

                            installAll(playSpecs(context));
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_hook_play_process);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_hook_play_process.equals(intent.getAction())) {
                                        XposedCompat.logInfo("broadcast received: " + msg_hook_play_process);
                                        ClassHelper.getCacheClassList(context, versionCode, () -> {
                                            installAll(playDeferredSpecs(context, versionCode));
                                            XposedCompat.logInfo("play-process immediate hooks ready before deferred hooks");
                                            XposedCompat.logSummary("play-process hooks");
                                        });
                                    }
                                }
                            }, intentFilter);
                            XposedCompat.logInfo("play-process immediate hooks ready, init finish broadcast sent");
                            context.sendBroadcast(new Intent(msg_play_process_init_finish));
                        }
                    }
                });

        Class<?> tinkerClass = XposedCompat.findClassIfExists("com.tencent.tinker.loader.app.TinkerApplication", classLoader);
        if (tinkerClass != null)
            XposedCompat.hookAllConstructors(tinkerClass, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.args[0] = 0;
                }
            });
    }

    // ------------------------------------------------------------------ hook registry

    /**
     * Registry row: a name for the log, an optional enable condition, and the factory. Adding a
     * hook is one row in the lists below instead of another branch in the install flow.
     */
    private static final class HookSpec {
        final String name;
        final Condition condition;
        final Runnable installer;

        HookSpec(String name, Condition condition, Runnable installer) {
            this.name = name;
            this.condition = condition;
            this.installer = installer;
        }
    }

    private interface Condition {
        boolean met();
    }

    private static void installAll(java.util.List<HookSpec> specs) {
        for (HookSpec spec : specs) {
            if (spec.condition != null && !spec.condition.met())
                continue;
            safeHook(spec.name, spec.installer);
        }
    }

    /** Entry points, installed before the master switch is consulted. */
    private java.util.List<HookSpec> alwaysSpecs(Context context, int versionCode) {
        return java.util.Arrays.asList(
                new HookSpec("SettingHook", null, () -> new SettingHook(context, versionCode)),
                new HookSpec("RnSettingEntryHook", null, () -> new RnSettingEntryHook(context)));
    }

    private java.util.List<HookSpec> immediateSpecs(Context context, int versionCode) {
        return java.util.Arrays.asList(
                new HookSpec("ProxyHook", null, () -> new ProxyHook(context, false)),
                new HookSpec("BlackHook",
                        () -> SettingHelper.getInstance().isEnable(SettingHelper.black_key),
                        () -> {
                            new BlackHook(context, versionCode);
                            try {
                                deleteAdAndTinker();
                            } catch (IOException e) {
                                XposedCompat.noteHookFailed("deleteAdAndTinker", e);
                            }
                        }),
                new HookSpec("AdAndUpdateHook", null, () -> new AdAndUpdateHook(context, versionCode)));
    }

    /** Needs the decompiled class list, so it runs after ClassHelper's cache is ready. */
    private java.util.List<HookSpec> deferredSpecs(Context context, int versionCode) {
        return java.util.Arrays.asList(
                new HookSpec("UserProfileHook", null, () -> new UserProfileHook(context)),
                new HookSpec("EAPIHook", null, () -> new EAPIHook(context)),
                new HookSpec("DownloadMD5Hook", null, () -> new DownloadMD5Hook(context)),
                new HookSpec("HideTabHook", null, () -> new HideTabHook(context, versionCode)),
                new HookSpec("CdnHook", null, () -> new CdnHook(context, versionCode)),
                new HookSpec("AdExtraHook", null, () -> new AdExtraHook(context)));
    }

    private java.util.List<HookSpec> playSpecs(Context context) {
        return java.util.Collections.singletonList(
                new HookSpec("play:ProxyHook", null, () -> new ProxyHook(context, true)));
    }

    private java.util.List<HookSpec> playDeferredSpecs(Context context, int versionCode) {
        return java.util.Arrays.asList(
                new HookSpec("play:EAPIHook", null, () -> new EAPIHook(context)),
                new HookSpec("play:CdnHook", null, () -> new CdnHook(context, versionCode)));
    }

    private void deleteAdAndTinker() throws IOException {

        String CACHE_PATH = Environment.getExternalStorageDirectory() + "/netease/cloudmusic/Ad";
        String CACHE_PATH2 = Environment.getExternalStorageDirectory() + "/Android/data/com.netease.cloudmusic/cache/Ad";

        String TINKER_PATH = "data/data/" + PACKAGE_NAME + "/tinker";

        FileHelper.deleteDirectory(CACHE_PATH);
        FileHelper.deleteDirectory(CACHE_PATH2);

        File tinkerFile = new File(TINKER_PATH);
        if (tinkerFile.exists() && tinkerFile.isDirectory())
            FileHelper.deleteDirectory(TINKER_PATH);
        if (!tinkerFile.exists())
            tinkerFile.createNewFile();

        String command = "chmod 000 " + tinkerFile.getAbsolutePath();
        Runtime runtime = Runtime.getRuntime();
        runtime.exec(command);
    }
}
