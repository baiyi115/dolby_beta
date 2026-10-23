package com.raincat.dolby_beta.port.ui;

import android.content.Context;
import android.view.View;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;

/**
 * UI 精简的总入口。每个子项独立安装，失败互不影响。
 */
public final class UiHooks {

    private UiHooks() {
    }

    public static void install(Context context) {
        SettingHelper s = SettingHelper.getInstance();
        if (s == null) {
            return;
        }
        ClassLoader cl = context.getClassLoader();

        if (s.isUiEnabled(SettingHelper.ui_home_blocks_key)) {
            HomeBlocksHook.install(context,
                    HomeBlocksHook.parseHideSet(s.getHomeBlocksFilter()));
        }
        if (s.isUiEnabled(SettingHelper.ui_top_channels_key)) {
            TopTabHook.install(context,
                    TopTabHook.parseKeepSet(s.getTopChannelsKeep()));
        }
        if (s.isUiEnabled(SettingHelper.ui_update_dialog_key)) {
            installUpdate(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_startup_delay_key)) {
            installStartupDelay(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_hints_key)) {
            installHints(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_autoplay_key)) {
            installAutoplay(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_player_hints_key)) {
            installPlayerHints(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_search_guide_key)) {
            installSearchGuide(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_lyrics_hint_key)) {
            installLyricsHint(cl);
        }
        if (s.isUiEnabled(SettingHelper.ui_community_live_key)) {
            installCommunityLive(cl);
        }
    }

    // UI-01 update dialog

    
    private static void installUpdate(ClassLoader cl) {
        Class<?> config = PortKit.findClass(Profile9581.UPDATE_CONFIG, cl);
        if (config != null) {
            Method hasNew = PortKit.findMethod(config, "hasNewVersion");
            PortKit.installBool("UI-01 CMUpdateConfig#hasNewVersion", hasNew, false);
        }

        Class<?> state = PortKit.findClass(Profile9581.UPDATE_STATE, cl);
        if (state != null) {
            PortKit.installBool("UI-01 appupdate.d#D(forceUpdate)",
                    PortKit.findMethod(state, "D"), false);
            PortKit.installBool("UI-01 appupdate.d#I(needPopUp)",
                    PortKit.findMethod(state, "I"), false);
            PortKit.installBool("UI-01 appupdate.d#C",
                    PortKit.findMethod(state, "C"), false);
        }
    }

    /**
     * UI-02 startup delay.
     *
     * StartTimeHandler delay getters are only reached through same-package invoke-static,
     * which Xposed cannot intercept. MainActivity.isNeedDelay()Z is the instance gate.
     */
    private static void installStartupDelay(ClassLoader cl) {
        Class<?> activity = PortKit.findClass(Profile9581.MAIN_ACTIVITY, cl);
        if (activity == null) {
            return;
        }
        Method need = PortKit.findMethod(activity, "isNeedDelay");
        PortKit.installBool("UI-02 MainActivity#isNeedDelay", need, false);
    }

    // UI-07 hints and bubbles
    private static void installHints(ClassLoader cl) {
        int hooked = 0;
        for (String className : Profile9581.HINT_VIEWS) {
            Class<?> cls = PortKit.findClass(className, cl);
            if (cls == null) {
                continue;
            }
            // gate the maybeDisplayHint / show entry
            for (String name : new String[]{"maybeDisplayHint", "show", "display"}) {
                Method m = PortKit.findMethod(cls, name);
                if (m == null) {
                    continue;
                }
                PortKit.install("UI-07 " + simple(className) + "#" + name, m,
                        PortKit.skipVoid(simple(className) + "." + name));
                hooked++;
                break;
            }
        }
        XposedCompat.logInfo("UI-07 hint hooks installed=" + hooked);
    }

    // UI-08 autoplay
    /**
     * UI-08 autoplay.
     *
     * mt0.d a/b/c/d/e are bluetooth and car autoplay switches (all read
     * setting#autoPlayConfig). ev0.j g/h are the launch-autoplay master switch.
     * ot0.r holds social prefs where only j() (mlogPlayIn4G) gates autoplay;
     * the other boolean getters there are unrelated preferences.
     */
    private static void installAutoplay(ClassLoader cl) {
        Class<?> bluetooth = PortKit.findClass(Profile9581.AUTOPLAY_BLUETOOTH, cl);
        if (bluetooth != null) {
            for (String name : new String[]{"a", "b", "c", "d", "e"}) {
                Method m = PortKit.findMethod(bluetooth, name);
                PortKit.installBool("UI-08 mt0.d#" + name, m, false);
            }
        }

        Class<?> master = PortKit.findClass(Profile9581.AUTOPLAY_MASTER, cl);
        if (master != null) {
            for (String name : new String[]{"g", "h"}) {
                Method m = PortKit.findMethod(master, name);
                PortKit.installBool("UI-08 ev0.j#" + name, m, false);
            }
        }

        Class<?> social = PortKit.findClass(Profile9581.AUTOPLAY_SOCIAL, cl);
        if (social != null) {
            Method j = PortKit.findMethod(social, "j");
            PortKit.installBool("UI-08 ot0.r#j(mlogPlayIn4G)", j, false);
        }
    }

    // UI-10 player hints
    private static void installPlayerHints(ClassLoader cl) {
        String[] targets = {
                Profile9581.PLAYER_COMMENT_POP,
                Profile9581.PLAYER_RECOMMEND_HINT,
                Profile9581.PLAYER_INCENTIVE_AD,
        };
        for (String name : targets) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            for (String method : new String[]{"show", "maybeDisplayHint", "display"}) {
                Method m = PortKit.findMethod(cls, method);
                if (m != null) {
                    PortKit.install("UI-10 " + simple(name) + "#" + method, m,
                            PortKit.skipVoid(simple(name) + "." + method));
                    break;
                }
            }
        }
    }

    // UI-11 search hot words
    private static void installSearchGuide(ClassLoader cl) {
        Class<?> cls = PortKit.findClass(Profile9581.SEARCH_GUIDE, cl);
        if (cls == null) {
            return;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 0
                    && java.util.List.class.isAssignableFrom(m.getReturnType())) {
                PortKit.install("UI-11 SearchGuide#" + m.getName(), m, new MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        PortKit.hit("UI-11 SearchGuide empty");
                        param.setResult(new java.util.ArrayList<>());
                    }
                });
            }
        }
    }

    // UI-14 lyrics promotion
    private static void installLyricsHint(ClassLoader cl) {
        String[] targets = {
                Profile9581.LYRICS_BOTTOM_HINT,
                Profile9581.LYRICS_VIDEO_TIPS,
        };
        for (String name : targets) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            for (String method : new String[]{"show", "maybeDisplayHint"}) {
                Method m = PortKit.findMethod(cls, method);
                if (m != null) {
                    PortKit.install("UI-14 " + simple(name) + "#" + method, m,
                            PortKit.skipVoid(simple(name) + "." + method));
                    break;
                }
            }
        }
    }

    // UI-15 community and live
    private static void installCommunityLive(ClassLoader cl) {
        String[] targets = {
                Profile9581.LIVE_PROMOTION,
                Profile9581.COMMENT_LIVE_HOLDER,
                Profile9581.NOTES_PUBLISH_GUIDE,
                Profile9581.NOTES_SEARCH_GUIDE,
        };
        for (String name : targets) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            for (String method : new String[]{"show", "maybeDisplayHint", "display"}) {
                Method m = PortKit.findMethod(cls, method);
                if (m != null) {
                    PortKit.install("UI-15 " + simple(name) + "#" + method, m,
                            PortKit.skipVoid(simple(name) + "." + method));
                    break;
                }
            }
        }
    }

    private static String simple(String className) {
        int i = className.lastIndexOf('.');
        return i >= 0 ? className.substring(i + 1) : className;
    }
}
