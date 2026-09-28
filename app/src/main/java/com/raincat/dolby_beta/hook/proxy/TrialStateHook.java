package com.raincat.dolby_beta.hook.proxy;

import android.content.Context;

import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

/** Playback trial-state suppression: trial getters, real duration, RN audition window and fake completion. */
public final class TrialStateHook {
    private static final Set<String> TRIAL_HITS =
            Collections.synchronizedSet(new HashSet<>());

    private static final Set<Long> REPLACED_IDS =
            Collections.synchronizedSet(new LinkedHashSet<>());
    private static volatile long lastReplacedAt;
    private static boolean trialHooksLogged;

    private final Context context;

    public TrialStateHook(Context context) {
        this.context = context;
    }

    public void install() {
        final Context context = this.context;
        if (!SettingHelper.getInstance().isEnable(SettingHelper.black_key)
                && !SettingHelper.getInstance().isEnable(SettingHelper.proxy_master_key))
            return;

        ClassLoader loader = context.getClassLoader();
        Class<?> songUrlInfo = trialTarget(loader, "com.netease.cloudmusic.meta.virtual.SongUrlInfo");
        hookTrialNoArg(songUrlInfo, "isAuditionSong", false);
        hookTrialNoArg(songUrlInfo, "getFreeTrialPrivilege", null);
        hookTrialNoArg(songUrlInfo, "getFreeTrialInfo", null);
        hookTrialNoArg(songUrlInfo, "getFreeTimeTrialPrivilege", null);
        hookTrialNoArg(songUrlInfo, "getUrlSource", 0L);
        hookTrialNoArg(songUrlInfo, "getAuditionEndPosition", 0);
        hookTrialNoArg(songUrlInfo, "getAuditionStartPosition", 0);

        Class<?> songPrivilege = trialTarget(loader, "com.netease.cloudmusic.meta.virtual.SongPrivilege");
        hookTrialNoArg(songPrivilege, "isAuditionSong", false);
        hookTrialNoArg(songPrivilege, "needAuditionSong", false);
        hookTrialNoArg(songPrivilege, "getFreeTrialPrivilege", null);

        Class<?> freeTrialInfo = trialTarget(loader,
                "com.netease.cloudmusic.meta.virtual.freetrial.FreeTrialInfo");
        hookTrialNoArg(freeTrialInfo, "getEndMillisecond", 0);
        hookTrialNoArg(freeTrialInfo, "getStartMillisecond", 0);

        Class<?> freeTrialPrivilege = trialTarget(loader,
                "com.netease.cloudmusic.meta.virtual.freetrial.FreeTrialPrivilege");
        if (freeTrialPrivilege != null && songUrlInfo != null) {
            try {
                XposedCompat.findAndHookMethod(freeTrialPrivilege, "isPlayingFullFreeTrail",
                        songUrlInfo, new MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                if (param.args == null || param.args.length != 1
                                        || !shouldSuppressTrial(param.args[0]))
                                    return;
                                param.setResult(true);
                                logTrialHitOnce("FreeTrialPrivilege.isPlayingFullFreeTrail=true");
                            }
                        });
            } catch (Throwable t) {
                XposedCompat.noteHookFailed("ProxyHook#isPlayingFullFreeTrail", t);
            }
        }

        Class<?> musicInfo = trialTarget(loader, "com.netease.cloudmusic.meta.MusicInfo");
        hookTrialNoArg(musicInfo, "isAuditionSong", false);
        hookTrialNoArg(musicInfo, "needAuditionSong", false);
        hookTrialNoArg(musicInfo, "isCurrentMusicPlayingAudition", false);
        hookTrialNoArg(musicInfo, "isAuditionSongBitrate", false);
        hookTrialNoArg(musicInfo, "getAuditionEndPosition", 0);
        hookTrialNoArg(musicInfo, "getAuditionStartPosition", 0);
        hookTrialNoArg(musicInfo, "getFreeTrialType", 0);
        hookAudioRealDuration(musicInfo);

        Class<?> simpleMusicInfo = trialTarget(loader,
                "com.netease.cloudmusic.meta.virtual.SimpleMusicInfo");
        hookTrialNoArg(simpleMusicInfo, "needAuditionSong", false);
        hookTrialNoArg(simpleMusicInfo, "isFullTrialSong", false);
        hookTrialNoArg(simpleMusicInfo, "isFullTrialUserConsumable", false);
        hookTrialNoArg(simpleMusicInfo, "isFullTrialResConsumable", false);
        hookTrialNoArg(simpleMusicInfo, "getFreeTrialType", 0);
        hookTrialNoArg(simpleMusicInfo, "getFullTrialLimitFreeTag", null);

        hookRnAuditionDuration(loader);
        hookFakeCompletion(context, loader);

        if (!trialHooksLogged) {
            trialHooksLogged = true;
            XposedCompat.logInfo("ProxyHook playback trial-state hooks installed v4 (gated by REPLACED_IDS)");
        }
    }

    /** Resolves a trial-state target and reports version drift instead of failing silently. */
    private static Class<?> trialTarget(ClassLoader loader, String name) {
        Class<?> clazz = findClassIfExists(name, loader);
        if (clazz == null)
            XposedCompat.log("ProxyHook trial target missing: " + name);
        return clazz;
    }

    private void hookTrialNoArg(Class<?> clazz, final String name, final Object result) {
        if (clazz == null)
            return;
        // Built once: these hooks fire on hot playback/UI paths and must not format per call.
        final String label = clazz.getSimpleName() + "." + name + "=" + result;
        try {
            XposedCompat.findAndHookMethod(clazz, name, new MethodHook() {
                @Override
                protected boolean usesArgs() {
                    return false;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!shouldSuppressTrial(param.thisObject))
                        return;
                    param.setResult(result);
                    logTrialHitOnce(label);
                }
            });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#" + clazz.getName() + "#" + name, t);
        }
    }

    /** Shared once-only registry: the player JSON replacement reports through the same set. */
    static boolean markOnce(String message) {
        return TRIAL_HITS.add(message);
    }

    private static void logTrialHitOnce(String message) {
        if (markOnce(message))
            XposedCompat.logInfo("ProxyHook trial-state hit: " + message);
    }

    private static void hookAudioRealDuration(Class<?> musicInfo) {
        if (musicInfo == null)
            return;
        try {
            XposedCompat.findAndHookMethod(musicInfo, "getAudioRealDuration", new MethodHook() {
                @Override
                protected boolean usesArgs() {
                    return false;
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!shouldSuppressTrial(param.thisObject))
                        return;
                    Object reported = param.getResult();
                    if (!(reported instanceof Number))
                        return;
                    Object fullDuration = XposedCompat.callMethod(param.thisObject, "getDuration");
                    if (!(fullDuration instanceof Number))
                        return;
                    int real = ((Number) reported).intValue();
                    int full = ((Number) fullDuration).intValue();
                    // Lift a stale audition-truncated value up to the metadata duration only, and
                    // never shrink: MainProcessPlayService reads this method as the fallback for
                    // its premature-completion check, so shortening a longer real stream would
                    // make replaced songs look "premature" and strand playback at track end.
                    if (full > 0 && real > 0 && real < full)
                        param.setResult(full);
                }
            });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#getAudioRealDuration", t);
        }
    }

    private static void hookFakeCompletion(Context context, ClassLoader loader) {
        Class<?> playService = findClassIfExists(
                "com.netease.cloudmusic.service.MainProcessPlayService", loader);
        String source = "known-class";
        if (playService == null) {
            for (String name : DexKitHelper.findPlayServiceCandidates(context)) {
                Class<?> candidate = findClassIfExists(name, loader);
                if (candidate != null && hasNoArgVoidMethod(candidate, "onFakeCompletion")) {
                    playService = candidate;
                    source = "dexkit";
                    break;
                }
            }
        }
        if (playService == null) {
            XposedCompat.logError("ProxyHook play service onFakeCompletion class not found");
            return;
        }
        final Class<?> serviceClass = playService;

        try {
            XposedCompat.findAndHookMethod(playService, "onFakeCompletion", new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);

                    if (!isCurrentMusicReplaced(param.thisObject)
                            || !isPrematureCompletion(param.thisObject, serviceClass))
                        return;
                    param.setResult(null);
                    logTrialHitOnce("MainProcessPlayService.onFakeCompletion blocked");
                }
            });
            XposedCompat.logInfo("ProxyHook onFakeCompletion hook installed: "
                    + playService.getName() + " (" + source + ")");

            XposedCompat.findAndHookMethod(playService, "onCompletion", String.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    logCompletionState(param.thisObject, serviceClass);
                    if (!isCurrentMusicReplaced(param.thisObject)
                            || !isPrematureCompletion(param.thisObject, serviceClass))
                        return;
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#onFakeCompletion", t);
        }
    }

    private static boolean isCurrentMusicReplaced(Object service) {
        try {
            Object music = XposedCompat.callMethod(service, "getCurrentMusic");
            return shouldSuppressTrial(music);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPrematureCompletion(Object service, Class<?> serviceClass) {
        try {
            Object music = XposedCompat.callMethod(service, "getCurrentMusic");
            if (music == null)
                return false;

            int duration = intValue(XposedCompat.callMethod(music, "getDuration"));
            if (duration <= 0)
                duration = intValue(XposedCompat.callMethod(music, "getAudioRealDuration"));
            int position = intValue(XposedCompat.callStaticMethod(serviceClass, "getCurrentTime"));
            if (position < 0)
                position = intValue(XposedCompat.callMethod(service, "getCurrentStreamPosition"));
            if (duration <= 0 || position < 0)
                return false;

            boolean premature = duration - position > 10_000;
            if (premature)
                logTrialHitOnce("MainProcessPlayService.onCompletion blocked: position="
                        + position + ", duration=" + duration);
            return premature;
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#isPrematureCompletion", t);
            return false;
        }
    }

    private static void logCompletionState(Object service, Class<?> serviceClass) {
        try {
            Object music = XposedCompat.callMethod(service, "getCurrentMusic");
            int duration = music == null ? -1 : intValue(XposedCompat.callMethod(music, "getDuration"));
            if (duration <= 0 && music != null)
                duration = intValue(XposedCompat.callMethod(music, "getAudioRealDuration"));
            int position = intValue(XposedCompat.callStaticMethod(serviceClass, "getCurrentTime"));
            if (position < 0)
                position = intValue(XposedCompat.callMethod(service, "getCurrentStreamPosition"));
            logTrialHitOnce("MainProcessPlayService.onCompletion observed: position="
                    + position + ", duration=" + duration + ", music=" + music);
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#logCompletionState", t);
        }
    }

    static int intValue(Object value) {
        if (value instanceof Number)
            return ((Number) value).intValue();
        return -1;
    }

    private static boolean hasNoArgVoidMethod(Class<?> clazz, String name) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName())
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() == void.class)
                return true;
        }
        return false;
    }

    private static final long RN_AUDITION_WINDOW_MS = 15_000;

    private static void hookRnAuditionDuration(ClassLoader loader) {
        Class<?> bridgeClass = findClassIfExists("uj0.p0", loader);
        if (bridgeClass == null)
            return;
        try {
            XposedCompat.findAndHookMethod(bridgeClass, "G1",
                    int.class, boolean.class, new MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args == null || param.args.length != 2)
                                return;
                            if (Boolean.TRUE.equals(param.args[1])
                                    && System.currentTimeMillis() - lastReplacedAt < RN_AUDITION_WINDOW_MS) {
                                param.args[1] = Boolean.FALSE;
                                logTrialHitOnce("RN SET_AUDITION_DURATION isAuditionSong=false");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("ProxyHook#SET_AUDITION_DURATION", t);
        }
    }

    static void rememberReplacedId(long id) {
        if (id <= 0)
            return;
        synchronized (REPLACED_IDS) {
            if (REPLACED_IDS.size() > 1024)
                REPLACED_IDS.clear();
            REPLACED_IDS.add(id);
        }
        lastReplacedAt = System.currentTimeMillis();
    }

    private static boolean isReplacedId(long id) {
        return id > 0 && REPLACED_IDS.contains(id);
    }

    private static long idOf(Object object) {
        if (object == null)
            return -1;
        try {
            Object value = XposedCompat.callMethod(object, "getId");
            if (value instanceof Number) {
                long id = ((Number) value).longValue();
                return id > 0 ? id : -1;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static boolean shouldSuppressTrial(Object object) {
        if (!ProxyTransport.isProxyActive())
            return false;
        return isReplacedId(idOf(object));
    }
}
