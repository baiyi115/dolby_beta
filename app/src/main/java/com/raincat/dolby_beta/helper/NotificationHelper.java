package com.raincat.dolby_beta.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.raincat.dolby_beta.xposed.XposedCompat;

public class NotificationHelper {
    private NotificationManager mNotificationManager;
    private static NotificationHelper mNotificationHelper;

    public static NotificationHelper getInstance(Context context) {
        if (mNotificationHelper == null) {
            mNotificationHelper = new NotificationHelper(context);
        }
        return mNotificationHelper;
    }

    private NotificationHelper(Context context) {
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void sendUnLockNotification(Context context, int appId, String ticker, String title, String content) {
        try {
            Notification.Builder builder = new Notification.Builder(context);
            ApplicationInfo applicationInfo = context.getApplicationInfo();
            Drawable drawable = applicationInfo.loadIcon(context.getPackageManager());
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                    drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            Icon icon = Icon.createWithBitmap(bitmap);

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(),
                    PendingIntent.FLAG_IMMUTABLE);
            builder.setSmallIcon(icon)
                    .setContentIntent(contentIntent)
                    .setContentTitle(title)
                    .setTicker(ticker)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_LIGHTS);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel notificationChannel = new NotificationChannel(context.getPackageName() + appId, "UnblockNeteaseMusic", NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.enableLights(true);
                notificationChannel.enableVibration(true);
                notificationChannel.setVibrationPattern(new long[]{200L, 200L, 200L, 200L});
                notificationChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), Notification.AUDIO_ATTRIBUTES_DEFAULT);
                builder.setChannelId(context.getPackageName() + appId);
                mNotificationManager.createNotificationChannel(notificationChannel);
            } else {
                builder.setVibrate(new long[]{200L, 200L, 200L, 200L});
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            }

            Notification notification = new Notification.BigTextStyle(builder).bigText(content).build();
            mNotificationManager.notify(appId, notification);
        } catch (Throwable t) {
            XposedCompat.log("sendUnLockNotification failed: " + t);
            XposedCompat.log(t);
        }
    }
}
