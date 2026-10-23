package com.raincat.dolby_beta.port.ads;

import android.content.Context;

import com.raincat.dolby_beta.helper.SettingHelper;

/**
 * 广告净化的总入口。所有子项都受 master + ad_filter + 子项开关三重约束。
 */
public final class AdHooks {

    private AdHooks() {
    }

    public static void install(Context context) {
        SettingHelper s = SettingHelper.getInstance();
        if (s == null) {
            return;
        }

        if (s.isAdEnabled(SettingHelper.ad_splash_key)) {
            SplashAdHook.install(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_launch_dialog_key)) {
            AdMiscHooks.installLaunchDialog(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_report_key)) {
            AdReportHook.install(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_search_key)) {
            AdMiscHooks.installSearchAd(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_preload_key)) {
            AdMiscHooks.installPreload(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_feed_key)) {
            AdMiscHooks.installFeedAd(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_comment_key)) {
            AdMiscHooks.installCommentAd(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_player_key)) {
            AdMiscHooks.installPlayerAd(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_audio_clip_key)) {
            AdMiscHooks.installAudioClip(context);
        }
        if (s.isAdEnabled(SettingHelper.ad_motivation_key)) {
            AdMotivationHook.install(context);
        }
    }

    /** :play installs only the audio clip hook (audioclip). */
    public static void installPlayProcess(Context context) {
        SettingHelper s = SettingHelper.getInstance();
        if (s == null || !s.isAdEnabled(SettingHelper.ad_audio_clip_key)) {
            return;
        }
        AdMiscHooks.installAudioClip(context);
    }
}
