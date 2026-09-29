package com.raincat.dolby_beta.hook;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.raincat.dolby_beta.xposed.XposedCompat.findAndHookMethod;
import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;
import static com.raincat.dolby_beta.xposed.XposedCompat.hookAllMethods;

public class AdAndUpdateHook {

    private static final String[] AD_CARD_VIEW_HOLDERS = new String[]{
            "com.netease.cloudmusic.module.ad.banner.scale.RcmdAdScaleBigCardViewHolder",
            "com.netease.cloudmusic.module.ad.banner.scale.RcmdAdScaleBigCardViewHolderV2",
            "com.netease.cloudmusic.module.ad.banner.flipping.RcmdAdFlippingViewHolder",
            "com.netease.cloudmusic.module.ad.banner.gallery.RcmdAdGalleryViewHolder",
            "com.netease.cloudmusic.ui.ad.dslview.AdDSLViewHolder"
    };

    private static String okHttpClientClassString = "okhttp3.OkHttpClient";
    private static String newCallMethodString = "newCall";
    private static String httpUrlFieldString = "url";
    private static String urlFieldString = "url";

    private static final Set<String> HIDDEN_LOGGED =
            Collections.synchronizedSet(new HashSet<>());

    public AdAndUpdateHook(Context context, final int versionCode) {
        if (versionCode < 138) {
            okHttpClientClassString = "okhttp3.x";
            newCallMethodString = "a";
            httpUrlFieldString = "a";
            urlFieldString = "j";
        }

        hookAdUrls(context);
        hookAdCards(context);
        hookLoadingAd(context);
    }

    /**
     * Resolved Field per (class, name). This hook runs once per HTTP request and the original
     * code called getDeclaredField() + setAccessible() on every single one of them.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Field> URL_FIELDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Field urlField(Class<?> clazz, String name) {
        String key = clazz.getName() + '#' + name;
        Field cached = URL_FIELDS.get(key);
        if (cached != null)
            return cached;
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Field previous = URL_FIELDS.putIfAbsent(key, field);
            return previous != null ? previous : field;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ad or upgrade endpoint. A NetEase CDN host is never treated as an ad host. */
    private static boolean shouldBlock(String url) {
        if (url.contains("android/version") || url.contains("android/upgrade"))
            return true;
        if (url.contains("music.126.net"))
            return false;
        if (url.contains("resource-exposure/config") || url.contains("api/ad")
                || url.contains("ad/get") || url.contains("ad/loading")
                || url.contains("appcustomconfig/get"))
            return true;
        return isAdMediaPath(url);
    }

    /**
     * Media URLs are only blocked when the path itself is an ad/splash path. A bare
     * {@code endsWith(".jpg")} / {@code endsWith(".mp4")} used to rewrite any image or video
     * request — including cover art and MV playback — to 127.0.0.1-invalid.
     */
    private static boolean isAdMediaPath(String url) {
        if (!url.endsWith(".jpg") && !url.endsWith(".mp4"))
            return false;
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("/ad/") || lower.contains("/ads/") || lower.contains("/advert")
                || lower.contains("splash") || lower.contains("loadingad")
                || lower.contains("/ad_") || lower.contains("_ad.");
    }

    private void hookAdUrls(Context context) {
        Class<?> okHttpClientClass = findClassIfExists(okHttpClientClassString, context.getClassLoader());
        if (okHttpClientClass == null) {
            XposedCompat.logError("AdHook OkHttpClient not found: " + okHttpClientClassString
                    + " (ad URL blocking disabled)");
            return;
        }
        hookAllMethods(okHttpClientClass, newCallMethodString, new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Cheapest filter first: this fires once per HTTP request, and ad removal is
                // commonly disabled, in which case nothing below should run at all.
                if (!SettingHelper.getInstance().isEnable(SettingHelper.ad_remove_key))
                    return;
                if (param.args == null || param.args.length != 1
                        || !param.args[0].getClass().getName().contains("okhttp"))
                    return;
                Object request = param.args[0];
                Field httpUrl = urlField(request.getClass(), httpUrlFieldString);
                if (httpUrl == null)
                    return;
                Object urlObj = httpUrl.get(request);
                if (urlObj == null || !shouldBlock(urlObj.toString()))
                    return;
                Field target = urlField(urlObj.getClass(), urlFieldString);
                if (target != null) {
                    // Trace what actually got blocked: the rule set is heuristic and this is the
                    // only way to tell an ad hit from a false positive on a device build.
                    XposedCompat.logDebug("AdHook blocked url: " + urlObj);
                    target.set(urlObj, "https://999.0.0.1/");
                }
            }
        });
    }

    private void hookAdCards(Context context) {
        List<String> targets = new ArrayList<>();
        for (String name : AD_CARD_VIEW_HOLDERS) {
            if (findClassIfExists(name, context.getClassLoader()) != null)
                targets.add(name);
            else
                XposedCompat.logError("AdHook ad card not found: " + name);
        }
        if (targets.size() != AD_CARD_VIEW_HOLDERS.length) {
            for (String name : DexKitHelper.findAdCardViewHolderCandidates(context))
                if (!targets.contains(name))
                    targets.add(name);
        }

        for (final String className : targets) {
            Class<?> clazz = findClassIfExists(className, context.getClassLoader());
            if (clazz == null)
                continue;
            int hooked = 0;
            final Field itemViewField = findItemViewField(clazz);
            if (itemViewField == null)
                XposedCompat.logError("AdHook ad card has no itemView field: " + className);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"onBindViewHolder".equals(method.getName())
                        || method.getParameterTypes().length != 3)
                    continue;

                // Must be afterHookedMethod: an intercept-before hook skips the binding call
                // itself and leaves the recycled holder showing its previous content.
                XposedCompat.hookMethod(method, new MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        hideAdItem(param.thisObject, itemViewField, className);
                    }
                });
                hooked++;
            }
            if (hooked == 0)
                XposedCompat.logError("AdHook ad card has no onBindViewHolder: " + className);
            else
                XposedCompat.logInfo("AdHook ad card hooked: " + className + " (" + hooked + ")");
        }
    }

    private static void hideAdItem(Object holder, Field itemViewField, String className) {
        if (!SettingHelper.getInstance().isEnable(SettingHelper.ad_remove_key))
            return;
        View itemView = itemView(holder, itemViewField);
        if (itemView == null)
            return;

        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        boolean alreadyHidden = itemView.getVisibility() == View.GONE
                && itemView.getMinimumHeight() == 0
                && params != null && params.height == 0;
        if (alreadyHidden)
            return;

        // Hiding needs GONE *and* height 0: StaggeredGridLayoutManager keeps reserving the
        // row for a GONE child, so the item would still take up space.
        itemView.setVisibility(View.GONE);
        itemView.setMinimumHeight(0);
        if (params != null) {
            params.height = 0;
            itemView.setLayoutParams(params);
        }
        if (HIDDEN_LOGGED.add(className))
            XposedCompat.logInfo("AdHook hid ad card: " + className);
    }

    /**
     * Resolved once per ViewHolder class at install time. RecyclerView.ViewHolder exposes the row
     * as the public final field {@code itemView}; this RecyclerView build has no getItemView().
     */
    private static Field findItemViewField(Class<?> holderClass) {
        for (Class<?> type = holderClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!"itemView".equals(field.getName())
                        || !View.class.isAssignableFrom(field.getType()))
                    continue;
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static View itemView(Object holder, Field itemViewField) {
        if (holder == null || itemViewField == null)
            return null;
        try {
            Object value = itemViewField.get(holder);
            return value instanceof View ? (View) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void hookLoadingAd(Context context) {
        try {
            if (!SettingHelper.getInstance().isEnable(SettingHelper.ad_remove_key)
                    || findClassIfExists("com.netease.cloudmusic.activity.LoadingAdActivity",
                    context.getClassLoader()) == null)
                return;
            findAndHookMethod("com.netease.cloudmusic.activity.LoadingAdActivity", context.getClassLoader(),
                    "onCreate", Bundle.class, new MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // Re-read the switch per call: the install-time check alone meant that
                            // turning "去广告" off kept closing the ad activity until a restart.
                            if (!SettingHelper.getInstance().isEnable(SettingHelper.ad_remove_key))
                                return;
                            Activity activity = (Activity) param.thisObject;
                            if (!activity.isFinishing())
                                activity.finish();
                        }
                    });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("AdHook#LoadingAd", t);
        }
    }
}
