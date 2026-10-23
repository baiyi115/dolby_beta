package com.raincat.dolby_beta.port.ads;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;

/**
 * AD-02 / 04 / 05 / 06 / 07 / 08 / 09 —— 除开屏外其余广告入口。
 *
 * 全部坐标来自 MT 对 9.5.81 的逐条实测（PORT_VERIFY_9581.md）。
 * 曾经的错误实现「扫所有返回 View 的无参方法」已删除：这些类里根本不存在这种签名。
 */
public final class AdMiscHooks {

    private AdMiscHooks() {
    }

    // AD-02 launch dialog

    
    public static void installLaunchDialog(Context context) {
        Class<?> cls = PortKit.findClass(Profile9581.LAUNCH_DIALOG, context.getClassLoader());
        if (cls == null) {
            return;
        }
        Class<?> data = PortKit.findClass(Profile9581.LAUNCH_DIALOG_DATA,
                context.getClassLoader());

        skipOn("AD-02 LaunchDialog#showCompactDialog", cls, "showCompactDialog", Activity.class, data);
        skipOn("AD-02 LaunchDialog#showSinglePicDialog", cls, "showSinglePicDialog", Activity.class, data);
        skipOn("AD-02 LaunchDialog#showMultiModulePopUpDialog", cls, "showMultiModulePopUpDialog",
                Activity.class, data);

        // three-arg overload carries an OnDismissListener
        for (Method m : cls.getDeclaredMethods()) {
            if (!"showPictureDialog".equals(m.getName())) {
                continue;
            }
            if (m.getParameterTypes().length != 3) {
                continue;
            }
            PortKit.install("AD-02 LaunchDialog#showPictureDialog(3)", m,
                    PortKit.skipVoid("LaunchDialog.showPictureDialog"));
        }
    }

    // AD-04 search ads

    /**
     * RNSearchAdViewManger 没有 View getter。真实入口是 createViewInstance，
     * 它返回 RNSearchAdView；返回一个空 View 即可让 RN 侧拿到占位而不渲染广告。
     * 同时钩掉三个 show*View，阻止已有广告位刷新内容。
     */
    public static void installSearchAd(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> cls = PortKit.findClass(Profile9581.SEARCH_AD_MANAGER, cl);
        if (cls == null) {
            return;
        }

        for (Method m : cls.getDeclaredMethods()) {
            if (!"createViewInstance".equals(m.getName())) {
                continue;
            }
            if (m.getParameterTypes().length != 1) {
                continue;
            }
            PortKit.install("AD-04 RNSearchAdViewManger#createViewInstance", m, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object ctx = param.args[0];
                        if (ctx instanceof Context) {
                            PortKit.hit("AD-04 search ad view -> empty");
                            param.setResult(new View((Context) ctx));
                        }
                    } catch (Throwable t) {
                        XposedCompat.logDebug("AD-04 createViewInstance failed: " + t);
                    }
                }
            });
        }

        
        
        
        PortKit.findClass(Profile9581.SEARCH_AD_LARGE, cl);
        PortKit.findClass(Profile9581.SEARCH_AD_MEDIUM, cl);
    }

    // AD-05 preload

    /** Startup tasks; init() verified present on 9.5.81. */
    public static void installPreload(Context context) {
        ClassLoader cl = context.getClassLoader();
        String[] tasks = {
                Profile9581.AD_COLD_BOOT,
                Profile9581.PODCAST_REWARD_AD,
                Profile9581.AD_PRELOAD_RECOMMEND,
        };
        for (String task : tasks) {
            Class<?> cls = PortKit.findClass(task, cl);
            if (cls == null) {
                continue;
            }
            Method init = PortKit.findMethod(cls, "init");
            PortKit.install("AD-05 " + simple(task) + "#init", init,
                    PortKit.skipVoid(simple(task) + ".init"));
        }
    }

    // AD-06/07 feed and comments

    /** Banner.isAdBanner()Z is the single gate for feed and comment ads. */
    public static void installFeedAd(Context context) {
        hookBanner(context, "AD-06");
    }

    public static void installCommentAd(Context context) {
        hookBanner(context, "AD-07");

        ClassLoader cl = context.getClassLoader();
        for (String name : new String[]{Profile9581.SPECIAL_COMMENT, Profile9581.COMMENT,
                Profile9581.COMMENT_LIST_ENTRY}) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0 || m.getReturnType() != boolean.class) {
                    continue;
                }
                // Only ad-flag getters: isAd / isSpecialAd / hasAd. A bare "ad" substring
                // also matched unrelated fields such as isInLotteryHeader.
                String n = m.getName();
                if (!n.startsWith("is") && !n.startsWith("has")) {
                    continue;
                }
                String lower = n.toLowerCase(java.util.Locale.ROOT);
                if (!lower.contains("ad")) {
                    continue;
                }
                if (lower.contains("load") || lower.contains("header") || lower.contains("lottery")) {
                    continue;
                }
                PortKit.installBool("AD-07 " + simple(name) + "#" + n, m, false);
            }
        }
    }

    private static void hookBanner(Context context, String tag) {
        Class<?> banner = PortKit.findClass(Profile9581.FEED_BANNER, context.getClassLoader());
        if (banner == null) {
            return;
        }
        Method isAd = PortKit.findMethod(banner, "isAdBanner");
        PortKit.installBool(tag + " Banner#isAdBanner", isAd, false);
    }

    /**
     * AD-08 player ads and gift stickers.
     *
     * PlayerAdJarDelegate.j(Ad, ViewGroup, ViewGroup) is the injection point; q(Ad) preloads.
     * MusicGiftModel is a pure data class with no methods of its own, so the gift row is gated
     * through the delegate rather than the model.
     */
    public static void installPlayerAd(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> delegate = PortKit.findClass(Profile9581.PLAYER_AD_DELEGATE, cl);
        if (delegate == null) {
            return;
        }
        Class<?> ad = PortKit.findClass(Profile9581.AD_META, cl);
        if (ad == null) {
            return;
        }
        Method j = PortKit.findMethod(delegate, "j", ad,
                android.view.ViewGroup.class, android.view.ViewGroup.class);
        PortKit.install("AD-08 PlayerAdJarDelegate#j", j,
                PortKit.skipVoid("PlayerAdJarDelegate.j"));

        Method q = PortKit.findMethod(delegate, "q", ad);
        PortKit.install("AD-08 PlayerAdJarDelegate#q", q,
                PortKit.skipVoid("PlayerAdJarDelegate.q"));

        Method m = PortKit.findMethod(delegate, "m",
                PortKit.findClass(Profile9581.MUSIC_GIFT, cl));
        PortKit.install("AD-08 PlayerAdJarDelegate#m(gift)", m,
                PortKit.skipVoid("PlayerAdJarDelegate.m"));
    }

    /**
     * AD-09 audio clip and podcast video.
     *
     * AdAudioClipManager exposes no View getter; e(String, String) starts the clip, a(Message)
     * drives the state machine, and isPlaying() is the gate the player reads.
     */
    public static void installAudioClip(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> clip = PortKit.findClass(Profile9581.AUDIO_CLIP, cl);
        if (clip != null) {
            Method e = PortKit.findMethod(clip, "e", String.class, String.class);
            PortKit.install("AD-09 AdAudioClipManager#e", e,
                    PortKit.skipVoid("AdAudioClipManager.e"));

            Method isPlaying = PortKit.findMethod(clip, "isPlaying");
            PortKit.installBool("AD-09 AdAudioClipManager#isPlaying", isPlaying, false);

            Method a = PortKit.findMethod(clip, "a", android.os.Message.class);
            PortKit.install("AD-09 AdAudioClipManager#a", a,
                    PortKit.skipVoid("AdAudioClipManager.a"));
        }

        // Both podcast helpers drive ads through void entry points, not boolean getters.
        // Hook showVerticalAdMode() which is the shared display gate on 9.5.81.
        for (String name : new String[]{Profile9581.PODCAST_VIDEO_AD, Profile9581.PODCAST_AD_HELPER}) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            Method show = PortKit.findMethod(cls, "showVerticalAdMode");
            PortKit.install("AD-09 " + simple(name) + "#showVerticalAdMode", show,
                    PortKit.skipVoid(simple(name) + ".showVerticalAdMode"));
        }
    }

    // helpers
    private static void skipOn(String tag, Class<?> cls, String name, Class<?>... params) {
        Method m = PortKit.findMethod(cls, name, params);
        PortKit.install(tag, m, PortKit.skipVoid(tag));
    }

    private static String simple(String className) {
        int i = className.lastIndexOf('.');
        return i >= 0 ? className.substring(i + 1) : className;
    }
}
