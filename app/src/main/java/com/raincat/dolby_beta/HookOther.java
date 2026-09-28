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
import com.raincat.dolby_beta.hook.BlackHook;
import com.raincat.dolby_beta.hook.CdnHook;
import com.raincat.dolby_beta.hook.DownloadMD5Hook;
import com.raincat.dolby_beta.hook.EAPIHook;
import com.raincat.dolby_beta.hook.HideTabHook;
import com.raincat.dolby_beta.hook.ProxyHook;
import com.raincat.dolby_beta.hook.SettingHook;
import com.raincat.dolby_beta.hook.UserProfileHook;
import com.raincat.dolby_beta.utils.Tools;

import java.io.File;
import java.io.IOException;

public class HookOther {
    private static String PACKAGE_NAME;
    int versionCode = 0;

    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;

    private final String msg_hook_play_process = "hookPlayProcess";

    private final String msg_play_process_init_finish = "playProcessInitFinish";

    public static final String msg_send_notification = "sendNotification";

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
                                deleteAdAndTinker();
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
                                    context.sendBroadcast(new Intent(msg_hook_play_process));
                            });
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_play_process_init_finish);
                            intentFilter.addAction(msg_send_notification);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_play_process_init_finish.equals(intent.getAction())) {
                                        playProcessInit = true;
                                        if (mainProcessInit && playProcessInit)
                                            context.sendBroadcast(new Intent(msg_hook_play_process));
                                    } else if (msg_send_notification.equals(intent.getAction())) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            NotificationHelper.getInstance(context).sendUnLockNotification(context, intent.getIntExtra("code", 0x10),
                                                    intent.getStringExtra("title"), intent.getStringExtra("title"), intent.getStringExtra("message"));
                                        XposedCompat.logInfo(intent.getStringExtra("title") + "：" + intent.getStringExtra("message"));
                                    }
                                }
                            }, intentFilter);
                        } else if (processName.equals(PACKAGE_NAME + ":play") && SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {

                            new ProxyHook(context, true);
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_hook_play_process);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_hook_play_process.equals(intent.getAction())) {
                                        ClassHelper.getCacheClassList(context, versionCode, () -> {
                                            new EAPIHook(context);
                                            new CdnHook(context, versionCode);
                                        });
                                    }
                                }
                            }, intentFilter);
                            context.sendBroadcast(new Intent(msg_play_process_init_finish));
                        }
                    }
                });
    }

    private void deleteAdAndTinker() throws IOException {

        String CACHE_PATH3 = Environment.getExternalStorageDirectory() + "/netease/cloudmusic/lite/Ad";
        if(PACKAGE_NAME.equals("com.hihonor.cloudmusic"))
        {
            CACHE_PATH3 = Environment.getExternalStorageDirectory() + "/hihonor/cloudmusic/Ad";
        }
        String CACHE_PATH4 = Environment.getExternalStorageDirectory() + "/Android/data/"+PACKAGE_NAME+"cache/Ad";
        String TINKER_PATH = "data/data/" + PACKAGE_NAME + "/tinker";

        FileHelper.deleteDirectory(CACHE_PATH3);
        FileHelper.deleteDirectory(CACHE_PATH4);

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
