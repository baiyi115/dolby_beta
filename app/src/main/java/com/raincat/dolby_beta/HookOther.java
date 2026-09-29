package com.raincat.dolby_beta;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import com.raincat.dolby_beta.helper.AdCleanupHelper;
import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.ProcessBroadcast;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.hook.AdAndUpdateHook;
import com.raincat.dolby_beta.hook.BlackHook;
import com.raincat.dolby_beta.hook.CdnHook;
import com.raincat.dolby_beta.hook.DownloadMD5Hook;
import com.raincat.dolby_beta.hook.EAPIHook;
import com.raincat.dolby_beta.hook.HideTabHook;
import com.raincat.dolby_beta.hook.ProxyHook;
import com.raincat.dolby_beta.hook.SettingHook;
import com.raincat.dolby_beta.hook.UserProfileHook;
import com.raincat.dolby_beta.utils.Tools;

import java.io.IOException;

public class HookOther {
    private static String PACKAGE_NAME;
    int versionCode = 0;

    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;

    public HookOther(ClassLoader classLoader, String packageName) {
        PACKAGE_NAME=packageName;
        Hook.installCrashLogger();
        XposedCompat.findAndHookMethod(XposedCompat.findClass("com.netease.cloudmusic.NeteaseMusicApplication", classLoader),
                "attachBaseContext", Context.class, new MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        if(PACKAGE_NAME.equals("com.netease.cloudmusic.lite"))
                        {
                            versionCode = 140;
                        }else {
                            versionCode = 8010050;
                        }

                        ExtraHelper.init(context);

                        SettingHelper.init(context);

                        final String processName = Tools.getCurrentProcessName(context);
                        if (processName.equals(PACKAGE_NAME)) {

                            new SettingHook(context, versionCode);

                            if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key))
                                return;

                            new ProxyHook(context, false);

                            if (SettingHelper.getInstance().isEnable(SettingHelper.black_key)) {
                                new BlackHook(context, versionCode);
                                // Never let ad/tinker cleanup abort the rest of the install: this
                                // runs inside attachBaseContext, so a throw would also skip
                                // AdAndUpdateHook, the deferred hooks and the play-process handshake.
                                try {
                                    deleteAdAndTinker();
                                } catch (Throwable t) {
                                    XposedCompat.noteHookFailed("deleteAdAndTinker", t);
                                }
                            }

                            new AdAndUpdateHook(context, versionCode);
                            ClassHelper.getCacheClassList(context, versionCode, () -> {

                                new UserProfileHook(context);

                                new EAPIHook(context);

                                new DownloadMD5Hook(context);

                                new HideTabHook(context, versionCode);
                                new CdnHook(context, versionCode);
                                mainProcessInit = true;
                                if (mainProcessInit && playProcessInit)
                                    ProcessBroadcast.sendHookPlayProcess(context);
                            });
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    String action = intent.getAction();
                                    if (ProcessBroadcast.PLAY_PROCESS_READY.equals(action)) {
                                        playProcessInit = true;
                                        if (mainProcessInit && playProcessInit)
                                            ProcessBroadcast.sendHookPlayProcess(context);
                                    } else if (ProcessBroadcast.SEND_NOTIFICATION.equals(action)) {
                                        ProcessBroadcast.handleNotification(context, intent);
                                        XposedCompat.logInfo(intent.getStringExtra("title") + "：" + intent.getStringExtra("message"));
                                    }
                                }
                            }, ProcessBroadcast.mainProcessFilter());
                        } else if (processName.equals(PACKAGE_NAME + ":play") && SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {

                            new ProxyHook(context, true);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (ProcessBroadcast.HOOK_PLAY_PROCESS.equals(intent.getAction())) {
                                        ClassHelper.getCacheClassList(context, versionCode, () -> {
                                            new EAPIHook(context);
                                            new CdnHook(context, versionCode);
                                        });
                                    }
                                }
                            }, ProcessBroadcast.playProcessFilter());
                            ProcessBroadcast.sendPlayProcessReady(context);
                        }
                    }
                });
    }

    private void deleteAdAndTinker() throws IOException {
        String adCache = Environment.getExternalStorageDirectory() + "/netease/cloudmusic/lite/Ad";
        if (PACKAGE_NAME.equals("com.hihonor.cloudmusic"))
            adCache = Environment.getExternalStorageDirectory() + "/hihonor/cloudmusic/Ad";

        AdCleanupHelper.deleteAdAndTinker(PACKAGE_NAME, adCache,
                AdCleanupHelper.externalAdCache(PACKAGE_NAME));
    }
}
