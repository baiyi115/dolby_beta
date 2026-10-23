package com.raincat.dolby_beta.port.ads;

import android.content.Context;

import com.raincat.dolby_beta.port.PortKit;
import com.raincat.dolby_beta.port.Profile9581;
import com.raincat.dolby_beta.xposed.MethodHook;

import java.lang.reflect.Method;

/**
 * AD-10: reward-ad and free-listening prompts.
 *
 * The bottom "free listening time used up" bar belongs to the ad-motivation system
 * (PlayerAdMotivationView), not to the ordinary ad pipeline, so AD-01/06 never reach it.
 * AdGreetingDSLView is the same system rendered into the home greeting slot.
 */
public final class AdMotivationHook {

    private AdMotivationHook() {
    }

    public static void install(Context context) {
        ClassLoader cl = context.getClassLoader();

        // Bottom toast bar on the player and home page.
        Class<?> motivation = PortKit.findClass(Profile9581.PLAYER_AD_MOTIVATION, cl);
        if (motivation != null) {
            Method maybeDisplay = PortKit.findMethod(motivation, "maybeDisplayHint");
            PortKit.install("AD-10 PlayerAdMotivationView#maybeDisplayHint", maybeDisplay,
                    PortKit.skipVoid("PlayerAdMotivationView.maybeDisplayHint"));

            Method hide = PortKit.findMethod(motivation, "hide");
            PortKit.install("AD-10 PlayerAdMotivationView#hide", hide, new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    PortKit.hit("PlayerAdMotivationView.hide");
                }
            });
        }

        // Home greeting-slot ad card.
        Class<?> greeting = PortKit.findClass(Profile9581.AD_GREETING_VIEW, cl);
        if (greeting != null) {
            hideAdGreeting(greeting);
        }

        // Reward-ad dialogs: entrance guide, early gain, endcard success.
        String[] dialogs = {
                Profile9581.MOTIVATION_FREE_LISTEN_GUIDE,
                Profile9581.MOTIVATION_EARLY_GAIN,
                Profile9581.MOTIVATION_ENDCARD,
                Profile9581.MOTIVATION_CLICK_STAY_FAIL,
        };
        for (String name : dialogs) {
            Class<?> cls = PortKit.findClass(name, cl);
            if (cls == null) {
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0 || m.getReturnType() != void.class) {
                    continue;
                }
                String n = m.getName();
                if (!n.equals("show") && !n.startsWith("show")) {
                    continue;
                }
                PortKit.install("AD-10 " + simple(name) + "#" + n, m,
                        PortKit.skipVoid(simple(name) + "." + n));
            }
        }
    }

    /**
     * AdGreetingDSLView renders ad cards into the home greeting slot through
     * showGreetingResource(JSONObject) and showGreetingResourceList(). Skipping both
     * leaves the slot empty without breaking the DSL host.
     */
    private static void hideAdGreeting(Class<?> cls) {
        Method single = PortKit.findMethodByParamTypeName(cls, "showGreetingResource",
                "com.alibaba.fastjson.JSONObject");
        PortKit.install("AD-10 AdGreetingDSLView#showGreetingResource", single,
                PortKit.skipVoid("AdGreetingDSLView.showGreetingResource"));

        Method list = PortKit.findMethod(cls, "showGreetingResourceList");
        PortKit.install("AD-10 AdGreetingDSLView#showGreetingResourceList", list,
                PortKit.skipVoid("AdGreetingDSLView.showGreetingResourceList"));
    }

    private static String simple(String className) {
        int i = className.lastIndexOf('.');
        return i >= 0 ? className.substring(i + 1) : className;
    }
}
