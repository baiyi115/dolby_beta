package com.raincat.dolby_beta.helper;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/**
 * Broadcast names and helpers shared by the main-process and {@code :play}-process installers.
 * The two processes are separate hook scopes that must agree on these strings, so they live here
 * instead of being spelled out in each entry class.
 */
public final class ProcessBroadcast {

    /** Main process -> play process: the class cache is ready, install the deferred hooks. */
    public static final String HOOK_PLAY_PROCESS = "hookPlayProcess";

    /** Play process -> main process: immediate hooks are installed. */
    public static final String PLAY_PROCESS_READY = "playProcessInitFinish";

    /** Any process -> main process: show the unlock notification. */
    public static final String SEND_NOTIFICATION = "sendNotification";

    private ProcessBroadcast() {
    }

    public static IntentFilter mainProcessFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(PLAY_PROCESS_READY);
        filter.addAction(SEND_NOTIFICATION);
        return filter;
    }

    public static IntentFilter playProcessFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(HOOK_PLAY_PROCESS);
        return filter;
    }

    public static void sendHookPlayProcess(Context context) {
        context.sendBroadcast(new Intent(HOOK_PLAY_PROCESS));
    }

    public static void sendPlayProcessReady(Context context) {
        context.sendBroadcast(new Intent(PLAY_PROCESS_READY));
    }

    public static void handleNotification(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            NotificationHelper.getInstance(context).sendUnLockNotification(context,
                    intent.getIntExtra("code", 0x10),
                    intent.getStringExtra("title"), intent.getStringExtra("title"),
                    intent.getStringExtra("message"));
    }
}
