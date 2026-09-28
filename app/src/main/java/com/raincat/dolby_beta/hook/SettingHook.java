package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.utils.Tools;
import com.raincat.dolby_beta.view.BaseDialogInputItem;
import com.raincat.dolby_beta.view.BaseDialogItem;
import com.raincat.dolby_beta.view.beauty.BeautyTabHideView;
import com.raincat.dolby_beta.view.beauty.BeautyTitleView;
import com.raincat.dolby_beta.view.proxy.*;
import com.raincat.dolby_beta.view.proxy.configuration.*;
import com.raincat.dolby_beta.view.setting.AboutView;
import com.raincat.dolby_beta.view.setting.BeautyView;
import com.raincat.dolby_beta.view.setting.BlackView;
import com.raincat.dolby_beta.view.setting.MasterView;
import com.raincat.dolby_beta.view.setting.ProxyView;
import com.raincat.dolby_beta.view.setting.ResetModuleView;
import com.raincat.dolby_beta.view.setting.TitleView;

import java.lang.reflect.Field;
import java.util.List;

public class SettingHook {
    private final String SettingActivity;
    private List<String> switchViewNames = new java.util.ArrayList<>();
    private TextView titleView, subView;
    private LinearLayout dialogRoot, dialogProxyRoot, dialogBeautyRoot;

    private BroadcastReceiver broadcastReceiver;
    private Context broadcastReceiverContext;

    private static SettingHook instance;

    public static void showFromEntry(Context context) {
        if (instance == null) {
            XposedCompat.log("dolby_beta SettingHook instance not ready");
            return;
        }
        instance.registerBroadcastReceiver(context);
        instance.showSettingDialog(context);
    }

    static void releaseFromEntry(Context context) {
        if (instance != null)
            instance.unregisterBroadcastReceiverFor(context);
    }

    public SettingHook(Context context,int versionCode) {
        instance = this;

        SettingActivity = versionCode >= 8007000
                ? "com.netease.cloudmusic.music.biz.setting.activity.SettingActivity"
                : "com.netease.cloudmusic.activity.SettingActivity";
        Class<?> settingActivityClass = findClassIfExists(SettingActivity, context.getClassLoader());
        if (settingActivityClass == null) {
            XposedCompat.log("dolby_beta SettingHook: SettingActivity not found " + SettingActivity);
            return;
        }

        // Some Switch fields are never assigned on certain versions, so read every one.
        for (Field field : settingActivityClass.getDeclaredFields()) {
            if (field.getType().getName().contains("Switch"))
                switchViewNames.add(field.getName());
        }

        findAndHookMethod(settingActivityClass, "onCreate", Bundle.class, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                try {
                    Context c = (Context) param.thisObject;

                    registerBroadcastReceiver(c);

                    initView(c);
                } catch (Throwable t) {
                    XposedCompat.log("dolby_beta SettingHook initView: " + android.util.Log.getStackTraceString(t));
                }
            }
        });

        findAndHookMethod(settingActivityClass, "onResume", new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (titleView != null)
                    return;
                if (!param.thisObject.getClass().getName().equals(SettingActivity))
                    return;
                try {
                    initView((Context) param.thisObject);
                    XposedCompat.logInfo("dolby_beta SettingHook entry injected on onResume");
                } catch (Throwable t) {
                    XposedCompat.log("dolby_beta SettingHook onResume initView: " + android.util.Log.getStackTraceString(t));
                }
            }
        });

        findAndHookMethod(settingActivityClass, "onDestroy", new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                unregisterBroadcastReceiverFor((Context) param.thisObject);
            }
        });
    }

    private void initView(final Context context) {
        TextView originalText = null;

        ViewGroup container = null;
        View styleRef = null;
        int rootId = context.getResources().getIdentifier("preferenceRoot", "id", context.getPackageName());
        if (rootId != 0 && context instanceof Activity) {
            Object root = ((Activity) context).findViewById(rootId);
            if (root instanceof ViewGroup)
                container = (ViewGroup) root;
        }
        if (container != null) {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0) {
                    styleRef = ((ViewGroup) child).getChildAt(i == 0 ? 1 : 0);
                    break;
                }
            }
        } else {
            View switchCompat = null;
            for (String name : switchViewNames) {
                Object v = XposedCompat.getObjectField(context, name);
                if (v instanceof View) {
                    switchCompat = (View) v;
                    break;
                }
            }
            if (switchCompat == null) {
                XposedCompat.log("dolby_beta SettingHook: no injection point found");
                return;
            }
            ViewGroup parent = (ViewGroup) switchCompat.getParent();
            container = (ViewGroup) parent.getParent();
            styleRef = parent;
        }

        LinearLayout linearLayout = new LinearLayout(context);
        if (styleRef != null) {
            linearLayout.setBackground(styleRef.getBackground());
            linearLayout.setLayoutParams(styleRef.getLayoutParams());
        } else {
            linearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        linearLayout.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(linearLayout, 0);

        titleView = new TextView(context);
        linearLayout.addView(titleView);
        subView = new TextView(context);
        linearLayout.addView(subView);
        refresh();
        start:
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i) instanceof TextView) {
                originalText = (TextView) container.getChildAt(i);
                break;
            } else if (container.getChildAt(i) instanceof ViewGroup) {
                for (int j = 0; j < ((ViewGroup) container.getChildAt(i)).getChildCount(); j++) {
                    if (((ViewGroup) container.getChildAt(i)).getChildAt(j) instanceof TextView) {
                        originalText = (TextView) ((ViewGroup) container.getChildAt(i)).getChildAt(j);
                        break start;
                    }
                }
            }
        }

        if (originalText != null) {
            titleView.setTextColor(originalText.getTextColors());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalText.getTextSize());
            titleView.setPadding(originalText.getPaddingLeft() == 0 ? Tools.dp2px(context, 10) : originalText.getPaddingLeft(), 0, 0, 0);
            subView.setTextColor(originalText.getTextColors());
            subView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (int) (originalText.getTextSize() / 3.0 * 2.0));
        }

        linearLayout.setOnClickListener(view -> showSettingDialog(context));
        XposedCompat.logInfo("dolby_beta SettingHook entry injected into " + container.getClass().getName());
    }

    @SuppressLint("SetTextI18n")
    private void refresh() {

        if (titleView == null || subView == null)
            return;
        titleView.setText("杜比大喇叭β");
        if (ExtraHelper.getExtraDate(ExtraHelper.USER_ID).equals("-1")) {
            subView.setText("（USERID获取失败）");
        } else if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key))
            subView.setText("（已关闭）");
        else if (ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS).equals("1"))
            subView.setText("（UnblockNeteaseMusic正在运行）");
        else
            subView.setText("（UnblockNeteaseMusic停止运行）");
    }

    private void registerBroadcastReceiver(final Context context) {
        if (isReceiverUsable(context))
            return;
        if (broadcastReceiver != null && isRegisteredContextInactive())
            unregisterBroadcastReceiverFor(broadcastReceiverContext);
        if (broadcastReceiver != null)
            return;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SettingHelper.refresh_setting);
        intentFilter.addAction(SettingHelper.proxy_setting);
        intentFilter.addAction(SettingHelper.beauty_setting);
        intentFilter.addAction(SettingHelper.proxy_configuration_setting);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                try {
                    String action = intent == null ? null : intent.getAction();
                    if (SettingHelper.refresh_setting.equals(action)) {
                        if (dialogRoot == null)
                            return;
                        for (int i = 0; i < dialogRoot.getChildCount(); i++) {
                            if (dialogRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogRoot.getChildAt(i)).refresh();
                        }
                        if (dialogProxyRoot != null)
                            for (int i = 0; i < dialogProxyRoot.getChildCount(); i++) {
                                if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogItem)
                                    ((BaseDialogItem) dialogProxyRoot.getChildAt(i)).refresh();
                                else if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogInputItem)
                                    ((BaseDialogInputItem) dialogProxyRoot.getChildAt(i)).refresh();
                            }
                        if (dialogBeautyRoot != null)
                            for (int i = 0; i < dialogBeautyRoot.getChildCount(); i++) {
                                if (dialogBeautyRoot.getChildAt(i) instanceof BaseDialogItem)
                                    ((BaseDialogItem) dialogBeautyRoot.getChildAt(i)).refresh();
                            }
                    } else if (SettingHelper.proxy_setting.equals(action)) {
                        showProxyDialog(context);
                    } else if (SettingHelper.beauty_setting.equals(action)) {
                        showBeautyDialog(context);
                    } else if (SettingHelper.proxy_configuration_setting.equals(action)) {
                        showProxyConfigurationDialog(context);
                    }
                } catch (Throwable t) {
                    XposedCompat.log("dolby_beta SettingHook refresh broadcast failed");
                    XposedCompat.log(t);
                }
            }
        };
        broadcastReceiverContext = context;
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private boolean isReceiverUsable(Context context) {
        return broadcastReceiver != null && broadcastReceiverContext == context;
    }

    private boolean isRegisteredContextInactive() {
        if (!(broadcastReceiverContext instanceof Activity))
            return false;
        Activity activity = (Activity) broadcastReceiverContext;
        return activity.isFinishing() || activity.isDestroyed();
    }

    private void unregisterBroadcastReceiverFor(Context context) {
        if (broadcastReceiver == null)
            return;
        if (context != null && broadcastReceiverContext != context)
            return;
        try {
            if (broadcastReceiverContext != null)
                broadcastReceiverContext.unregisterReceiver(broadcastReceiver);
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta SettingHook unregisterReceiver: " + android.util.Log.getStackTraceString(t));
        }
        broadcastReceiver = null;
        broadcastReceiverContext = null;
    }

    /** Rows are appended in argument order; dependency wiring stays with each view's construction. */
    private static void addRows(LinearLayout root, View... rows) {
        for (View row : rows)
            root.addView(row);
    }

    /** Sub-pages share the same two buttons: save only, or save and restart the host app. */
    private void showSubPageDialog(Context context, View content) {
        new AlertDialog.Builder(context)
                .setView(content)
                .setCancelable(true)
                .setPositiveButton("仅保存", (dialogInterface, i) -> refresh())
                .setNegativeButton("保存并重启", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showSettingDialog(final Context context) {
        dialogRoot = new BaseDialogItem(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(dialogRoot);

        MasterView masterView = new MasterView(context);
        BlackView blackView = new BlackView(context);
        blackView.setBaseOnView(masterView);
        ProxyView proxyView = new ProxyView(context);
        proxyView.setBaseOnView(masterView);
        BeautyView beautyView = new BeautyView(context);
        beautyView.setBaseOnView(masterView);

        addRows(dialogRoot,
                new TitleView(context),
                masterView,
                blackView,
                proxyView,
                beautyView,
                new ResetModuleView(context),
                new AboutView(context));

        new AlertDialog.Builder(context)
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("确定", (dialogInterface, i) -> refresh())
                .setNegativeButton("重启网易云", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showProxyDialog(final Context context) {
        dialogProxyRoot = new BaseDialogItem(context);
        dialogProxyRoot.setOrientation(LinearLayout.VERTICAL);
        ProxyMasterView proxyMasterView = new ProxyMasterView(context);
        ProxyCoverView proxyCoverView = new ProxyCoverView(context);
        proxyCoverView.setBaseOnView(proxyMasterView);
        ProxyServerView proxyServerView = new ProxyServerView(context);
        proxyServerView.setBaseOnView(proxyMasterView);
        ProxyPriorityView proxyPriorityView = new ProxyPriorityView(context);
        proxyPriorityView.setBaseOnView(proxyMasterView);
        ProxyFlacView proxyFlacView = new ProxyFlacView(context);
        proxyFlacView.setBaseOnView(proxyMasterView);
        ProxyConfigurationView proxyConfigurationView = new ProxyConfigurationView(context);
        proxyConfigurationView.setBaseOnView(proxyMasterView);

        addRows(dialogProxyRoot,
                new ProxyTitleView(context),
                proxyMasterView,
                proxyCoverView,
                proxyServerView,
                proxyPriorityView,
                proxyFlacView,
                proxyConfigurationView);

        showSubPageDialog(context, dialogProxyRoot);
    }

    private void showProxyConfigurationDialog(final Context context) {
        dialogProxyRoot = new BaseDialogItem(context);
        dialogProxyRoot.setOrientation(LinearLayout.VERTICAL);
        addRows(dialogProxyRoot,
                new ProxyConfigurationTitleView(context),
                new ProxyHttpView(context),
                new ProxyPortView(context),
                new ProxyOriginalView(context),
                new ProxyQqView(context));
        showSubPageDialog(context, dialogProxyRoot);
    }

    private void showBeautyDialog(final Context context) {
        dialogBeautyRoot = new BaseDialogItem(context);
        dialogBeautyRoot.setOrientation(LinearLayout.VERTICAL);
        addRows(dialogBeautyRoot,
                new BeautyTitleView(context),
                new BeautyTabHideView(context));
        showSubPageDialog(context, dialogBeautyRoot);
    }

    private void restartApplication(Context context) {
        ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoListist = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessInfoListist) {
            if (runningAppProcessInfo.processName.contains(":play")) {
                android.os.Process.killProcess(runningAppProcessInfo.pid);
                break;
            }
        }
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
