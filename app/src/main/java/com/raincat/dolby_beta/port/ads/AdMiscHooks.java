package com.raincat.dolby_beta.port.ads;

import android.app.Activity;
import android.content.Context;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;

import java.lang.reflect.Method;

/**
 * 除开屏外的其余广告入口：运营弹窗、搜索、预加载、信息流、评论、播放页、音频贴片。
 *
 * 全部坐标来自 MT 对 9.5.81 的逐条实测（PORT_VERIFY_9581.md）。
 * 曾经的错误实现「扫所有返回 View 的无参方法」已删除：这些类里根本不存在这种签名。
 */
public final class AdMiscHooks {

    private AdMiscHooks() {
    }

    // launch dialog

    
    public static void installLaunchDialog(Context context) {
        Class<?> cls = PortKit.findClass(Profile9581.LAUNCH_DIALOG, context.getClassLoader());
        if (cls == null) {
            return;
        }
        Class<?> data = PortKit.findClass(Profile9581.LAUNCH_DIALOG_DATA,
                context.getClassLoader());

        skipOn("launchdialog LaunchDialog#showCompactDialog", cls, "showCompactDialog", Activity.class, data);
        skipOn("launchdialog LaunchDialog#showSinglePicDialog", cls, "showSinglePicDialog", Activity.class, data);
        skipOn("launchdialog LaunchDialog#showMultiModulePopUpDialog", cls, "showMultiModulePopUpDialog",
                Activity.class, data);

        // three-arg overload carries an OnDismissListener
        for (Method m : cls.getDeclaredMethods()) {
            if (!"showPictureDialog".equals(m.getName())) {
                continue;
            }
            if (m.getParameterTypes().length != 3) {
                continue;
            }
            PortKit.install("launchdialog LaunchDialog#showPictureDialog(3)", m,
                    PortKit.skipVoid("LaunchDialog.showPictureDialog"));
        }
    }

    // search ads

    /**
     * RNSearchAdViewManger 没有 View getter。9.5.81 上广告内容由 private 的
     * showAdLargeView/showAdMediumView 挂到现成的 RNSearchAdView 上，跳过这两个方法即可，
     * RN 侧仍会正常创建视图，只是不再填充广告。
     *
     * 不要拦截 createViewInstance：它除构造视图外还设置 mEmitter、构造并订阅 ViewModel 的
     * LiveData、写入 mViewRef；用构造出来的空视图顶替会让这些字段保持空值，后续
     * setExtraParams/hideAllViews 可能 NPE。它声明返回 RNSearchAdView，早先
     * setResult(new View(ctx)) 还会在调用方按声明类型 checkcast 时抛 ClassCastException。
     */
    public static void installSearchAd(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> cls = PortKit.findClass(Profile9581.SEARCH_AD_MANAGER, cl);
        if (cls == null) {
            return;
        }

        PortKit.installSkipDefault("searchad RNSearchAdViewManger#showAdLargeView",
                PortKit.findMethod(cls, "showAdLargeView"));
        PortKit.installSkipDefault("searchad RNSearchAdViewManger#showAdMediumView",
                PortKit.findMethod(cls, "showAdMediumView"));
    }

    // preload

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
            PortKit.install("adpreload " + simple(task) + "#init", init,
                    PortKit.skipVoid(simple(task) + ".init"));
        }
    }

    // feed and comments

    /**
     * Banner.isAdBanner()Z gates both feed and comment ads. It must be installed exactly once
     * from {@link AdHooks}: installing it per feature wrapped the same method twice.
     */
    public static void installBanner(Context context, String tag) {
        hookBanner(context, tag);
    }

    public static void installCommentAd(Context context) {
        // Banner is shared with feed and installed once by AdHooks.installBanner().
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
                PortKit.installBool("commentad " + simple(name) + "#" + n, m, false);
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
     * playerad player ads and gift stickers.
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
        PortKit.install("playerad PlayerAdJarDelegate#j", j,
                PortKit.skipVoid("PlayerAdJarDelegate.j"));

        Method q = PortKit.findMethod(delegate, "q", ad);
        PortKit.install("playerad PlayerAdJarDelegate#q", q,
                PortKit.skipVoid("PlayerAdJarDelegate.q"));

        Method m = PortKit.findMethod(delegate, "m",
                PortKit.findClass(Profile9581.MUSIC_GIFT, cl));
        PortKit.install("playerad PlayerAdJarDelegate#m(gift)", m,
                PortKit.skipVoid("PlayerAdJarDelegate.m"));
    }

    /**
     * audioclip audio clip and podcast video.
     *
     * AdAudioClipManager exposes no View getter; e(String, String) starts the clip, a(Message)
     * drives the state machine, and isPlaying() is the gate the player reads.
     */
    public static void installAudioClip(Context context) {
        ClassLoader cl = context.getClassLoader();
        Class<?> clip = PortKit.findClass(Profile9581.AUDIO_CLIP, cl);
        if (clip != null) {
            Method e = PortKit.findMethod(clip, "e", String.class, String.class);
            PortKit.install("audioclip AdAudioClipManager#e", e,
                    PortKit.skipVoid("AdAudioClipManager.e"));

            Method isPlaying = PortKit.findMethod(clip, "isPlaying");
            PortKit.installBool("audioclip AdAudioClipManager#isPlaying", isPlaying, false);

            Method a = PortKit.findMethod(clip, "a", android.os.Message.class);
            PortKit.install("audioclip AdAudioClipManager#a", a,
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
            PortKit.install("audioclip " + simple(name) + "#showVerticalAdMode", show,
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
