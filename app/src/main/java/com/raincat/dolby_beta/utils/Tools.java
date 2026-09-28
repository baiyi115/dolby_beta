package com.raincat.dolby_beta.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Tools {

    public static String getCurrentProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (mActivityManager != null) {
            for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager.getRunningAppProcesses()) {
                if (appProcess.pid == pid) {
                    return appProcess.processName;
                }
            }
        }
        return "";
    }

    public static void showToastOnLooper(final Context context, final String message) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            XposedCompat.log("showToastOnLooper failed: " + message);
            XposedCompat.log(e);
        }
    }

    public static int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static void shell(Command command) {
        try {
            RootTools.closeAllShells();
            RootTools.getShell(false).add(command);
        } catch (TimeoutException | RootDeniedException | IOException e) {
            XposedCompat.log("shell command submit failed: " + command);
            XposedCompat.log(e);
        } catch (Exception e) {
            XposedCompat.log("shell command unexpected failure: " + command);
            XposedCompat.log(e);
        }
    }
}
