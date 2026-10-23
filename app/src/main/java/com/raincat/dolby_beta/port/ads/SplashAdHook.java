package com.raincat.dolby_beta.port.ads;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;

public final class SplashAdHook {

    private SplashAdHook() {
    }

    public static void install(Context context) {
        ClassLoader cl = context.getClassLoader();
        hookManager(cl);
        hookActivity(cl);
        hookFragment(cl);
        hookShake(cl);
        hookNoAdAbTest(cl);
    }

    private static void hookManager(ClassLoader cl) {
        Class<?> manager = PortKit.findClass(Profile9581.AD_MANAGER, cl);
        Class<?> adInfo = PortKit.findClass(Profile9581.AD_INFO, cl);
        if (manager == null) {
            return;
        }

        // 1. final display check; the only (AdInfo)Z in the class
        if (adInfo != null) {
            Method e = PortKit.findMethod(manager, "E", adInfo);
            PortKit.installBool("AD-01 LoadingAdManager#E", e, false);
        }

        // 2. warm start
        Method g = PortKit.findMethod(manager, "G", Activity.class);
        PortKit.install("AD-01 LoadingAdManager#G", g,
                PortKit.skipVoid("LoadingAdManager.G"));

        // 3. data source
        Method o = PortKit.findMethod(manager, "o", boolean.class);
        PortKit.install("AD-01 LoadingAdManager#o", o, new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                PortKit.hit("LoadingAdManager.o -> empty list");
                param.setResult(new ArrayList<>());
            }
        });
    }

    private static void hookActivity(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.AD_ACTIVITY, cl);
        if (cls == null) {
            return;
        }
        Method onCreate = PortKit.findMethod(cls, "onCreate", Bundle.class);
        PortKit.install("AD-01 LoadingAdActivity#onCreate", onCreate, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject instanceof Activity) {
                        Activity activity = (Activity) param.thisObject;
                        if (!activity.isFinishing()) {
                            PortKit.hit("LoadingAdActivity finish");
                            activity.finish();
                        }
                    }
                } catch (Throwable t) {
                    XposedCompat.logDebug("AD-01 finish failed: " + t);
                }
            }
        });
    }

    /**
     * LoadingAdFragment 继承自 Fragment，onViewCreated(View, Bundle) 是最终实现。
     * 只按名字找会因为重载歧义返回 null，所以按参数类型精确定位。
     */
    private static void hookFragment(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.AD_FRAGMENT, cl);
        if (cls == null) {
            return;
        }
        Method onViewCreated = PortKit.findMethod(cls, "onViewCreated", View.class, Bundle.class);
        PortKit.install("AD-01 LoadingAdFragment#onViewCreated", onViewCreated, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object[] args = param.args;
                    if (args == null || args.length == 0 || !(args[0] instanceof View)) {
                        return;
                    }
                    View view = (View) args[0];
                    view.setVisibility(View.GONE);
                    ViewParent parent = view.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(view);
                        PortKit.hit("LoadingAdFragment removed from parent");
                    }
                } catch (Throwable t) {
                    XposedCompat.logDebug("AD-01 fragment hide failed: " + t);
                }
            }
        });
    }

    private static void hookShake(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.AD_SHAKE, cl);
        if (cls == null) {
            return;
        }
        Method onSensorChanged = PortKit.findMethod(cls, "onSensorChanged",
                android.hardware.SensorEvent.class);
        PortKit.install("AD-01 LoadingAdShakeHelper#onSensorChanged", onSensorChanged,
                PortKit.skipVoid("LoadingAdShakeHelper.onSensorChanged"));
    }

    /**
     * o60.w0.g() = areEqual(d(), "c")，d() 读免广 AB 分组。
     * 返回 true = 在 c 组 = 看广告；false = 免广。hook 成 false 是正确极性。
     */
    private static void hookNoAdAbTest(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.NO_AD_AB, cl);
        if (cls == null) {
            return;
        }
        Method g = PortKit.findMethod(cls, "g");
        PortKit.installBool("AD-01 o60.w0#g", g, false);
    }
}
