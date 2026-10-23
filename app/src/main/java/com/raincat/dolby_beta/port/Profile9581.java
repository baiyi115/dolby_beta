package com.raincat.dolby_beta.port;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Profile9581 {

    public static final long VERSION_CODE = 9005081L;

    // obfuscated classes
    /** No-ad AB switch; q60.w0 on 9.6.08. */
    public static final String NO_AD_AB = "o60.w0";
    /** Top-tab adapter; same name on 9.6.08. */
    public static final String TOP_TAB_ADAPTER = "dr.h";
    /** Bottom-tab repository; th0.h on 9.6.08. */
    /** Bottom-tab view layer; rh0.c on 9.6.08. */
    /** Bluetooth autoplay config; tt0.d on 9.6.08. */
    public static final String AUTOPLAY_BLUETOOTH = "mt0.d";
    /** Social autoplay config; vt0.r on 9.6.08. */
    public static final String AUTOPLAY_SOCIAL = "ot0.r";
    /** Autoplay master switch; lv0.j on 9.6.08. */
    public static final String AUTOPLAY_MASTER = "ev0.j";
    /** In-app update entry; fw0.e on 9.6.08. */

    // named classes
    public static final String AD_MANAGER = "com.netease.cloudmusic.module.ad.LoadingAdManager";
    public static final String AD_INFO = "com.netease.cloudmusic.module.ad.meta.AdInfo";
    public static final String AD_ACTIVITY = "com.netease.cloudmusic.activity.LoadingAdActivity";
    public static final String AD_FRAGMENT = "com.netease.cloudmusic.fragment.LoadingAdFragment";
    public static final String AD_SHAKE = "com.netease.cloudmusic.module.ad.boot.LoadingAdShakeHelper";

    public static final String AD_REPORT_TASK = "com.netease.cloudmusic.module.ad.AdCloudReportTask";
    public static final String START_APP_B = "com.netease.cloudmusic.ad.StartAppHelper$b";
    public static final String DEEPLINK_PRE =
            "com.netease.cloudmusic.process.task.app.attach.AdExposureDeeplinkPreRequestTask";
    public static final String DEEPLINK_TASK =
            "com.netease.cloudmusic.process.task.app.create.AdExposureDeeplinkTask";

    public static final String LAUNCH_DIALOG = "com.netease.cloudmusic.ui.LaunchDialog";
    public static final String LAUNCH_DIALOG_DATA = "com.netease.cloudmusic.ui.LaunchDialog$Data";
    public static final String AD_META = "com.netease.cloudmusic.meta.Ad";

    public static final String MOTIVATION_FREE_LISTEN_GUIDE =
            "com.netease.cloudmusic.module.ad.motivation.commonui.AdFreeListenEntranceGuideDialog";
    public static final String MOTIVATION_EARLY_GAIN =
            "com.netease.cloudmusic.module.ad.motivation.commonui.AdMotivationEarlyGainDialog";
    public static final String MOTIVATION_ENDCARD =
            "com.netease.cloudmusic.module.ad.motivation.commonui.AdMotivationEndcardSuccessDialog";
    public static final String MOTIVATION_CLICK_STAY_FAIL =
            "com.netease.cloudmusic.module.ad.motivation.commonui.AdMotivationClickStayFailDialog";

    public static final String FEED_BANNER = "com.netease.cloudmusic.meta.Banner";
    public static final String SPECIAL_COMMENT = "com.netease.cloudmusic.meta.virtual.SpecialComment";
    public static final String COMMENT = "com.netease.cloudmusic.meta.Comment";
    public static final String COMMENT_LIST_ENTRY =
            "com.netease.cloudmusic.meta.virtual.CommentListEntry";

    public static final String PLAYER_AD_DELEGATE =
            "com.netease.cloudmusic.module.ad.playerjar.PlayerAdJarDelegateImpl";
    public static final String MUSIC_GIFT =
            "com.netease.cloudmusic.module.player.vinylfakelayer.musicgift.meta.MusicGiftModel";

    public static final String AUDIO_CLIP = "com.netease.cloudmusic.module.ad.audioclip.AdAudioClipManager";
    public static final String PODCAST_AD_HELPER =
            "com.netease.cloudmusic.module.ad.podcast.AdPodcastPlayerPageHelper";
    public static final String PODCAST_VIDEO_AD =
            "com.netease.cloudmusic.module.ad.podcast.PodcastVideoAdHelperImpl";

    public static final String SEARCH_AD_MANAGER =
            "com.netease.cloudmusic.music.biz.search.start.rnview.RNSearchAdViewManger";
    public static final String SEARCH_AD_LARGE =
            "com.netease.cloudmusic.music.biz.search.complex.viewholder.ad.SearchAdItemLargeView";
    public static final String SEARCH_AD_MEDIUM =
            "com.netease.cloudmusic.music.biz.search.complex.viewholder.ad.SearchAdItemMediumView";

    public static final String AD_COLD_BOOT =
            "com.netease.cloudmusic.process.task.main.create.AdColdBootEntranceTask";
    public static final String PODCAST_REWARD_AD =
            "com.netease.cloudmusic.process.task.main.createproxy.PodcastRewardAdTask";
    public static final String AD_PRELOAD_RECOMMEND =
            "com.netease.cloudmusic.process.task.main.create.APreloadRecommendViewTask";

    // UI
    public static final String MIX_CONTAINER =
            "com.netease.cloudmusic.dsl.container.data.MixContainerData";
    public static final String DISCOVERY_BLOCK =
            "com.netease.cloudmusic.discovery.data.meta.DiscoveryBlockData";
    public static final String TOP_TAB_INFO =
            "com.netease.cloudmusic.discovery.view.module.hometoptab.model.HomeTopTabInfo";
    public static final String MAIN_ACTIVITY =
            "com.netease.cloudmusic.activity.MainActivity";
    public static final String UPDATE_CONFIG = "com.netease.cloudmusic.appupdate.CMUpdateConfig";
    public static final String UPDATE_STATE = "com.netease.cloudmusic.appupdate.d";
    public static final String SEARCH_GUIDE = "com.netease.cloudmusic.meta.SearchGuide";

    public static final String HINT_PUSH_GUIDE =
            "com.netease.cloudmusic.main.hint.push.PushGuideHintView";
    public static final String HINT_BOTTOM_TAB_BUBBLE =
            "com.netease.cloudmusic.main.hint.BottomTabGuideBubbleNativeView";
    public static final String HINT_SCORE_DIALOG =
            "com.netease.cloudmusic.module.hint.view.ScoreHintDialogView";
    public static final String HINT_LIVE_PUSH =
            "com.netease.cloudmusic.module.hint.view.LiveLocalPushHintView";
    public static final String HINT_VIP_RENEWAL =
            "com.netease.cloudmusic.discovery.view.module.music.v3.vipcard.HintVipNormalRenewalDialogueView";

    public static final String LYRICS_BOTTOM_HINT =
            "com.netease.cloudmusic.module.hint.view.LyricsBottomHintView";
    public static final String LYRICS_VIDEO_TIPS =
            "com.netease.cloudmusic.fragment.LyricVideoTipsDialog";

    public static final String PLAYER_COMMENT_POP =
            "com.netease.cloudmusic.ui.hint.PlayerCommentPopView";
    public static final String PLAYER_RECOMMEND_HINT =
            "com.netease.cloudmusic.module.hint.view.PlayerRecommendSongHintView";
    public static final String PLAYER_INCENTIVE_AD =
            "com.netease.cloudmusic.module.hint.view.PlayerIncentiveAdView";
    public static final String PLAYER_AD_MOTIVATION =
            "com.netease.cloudmusic.ui.hint.PlayerAdMotivationView";
    public static final String AD_GREETING_VIEW =
            "com.netease.cloudmusic.ui.ad.dslview.greeting.AdGreetingDSLView";

    public static final String LIVE_PROMOTION = "com.netease.play.livepage.promotion.LivePromotion";
    public static final String COMMENT_LIVE_HOLDER =
            "com.netease.cloudmusic.module.comment.CommentLiveViewHolder";
    public static final String NOTES_PUBLISH_GUIDE =
            "com.netease.cloudmusic.music.biz.mlog.consume.social.homepage.ui.NotesPublishGuideHint";
    public static final String NOTES_SEARCH_GUIDE =
            "com.netease.cloudmusic.music.biz.mlog.consume.social.homepage.ui.NotesSearchGuideHint";

    public static final String WIDGET_ADD_GUIDE =
            "com.netease.cloudmusic.music.biz.appwidgets.view.WidgetAddGuideDialogHelper$a";
    public static final String PUSH_GUIDE_BOTTOM_SHEET =
            "com.netease.cloudmusic.main.hint.push.PushGuideBottomSheetFragment";
    public static final String PUSH_GUIDE_CENTER_DIALOG =
            "com.netease.cloudmusic.main.hint.push.PushGuideCenterDialogFragment";

    public static final String PODCAST_BOTTOM_GUIDE =
            "com.netease.cloudmusic.music.biz.voice.home.PodcastBottomGuideManager";
    public static final String FM_AIDJ_SWITCH = "com.netease.cloudmusic.hint.aidj.FMAIDJSwitchView";
    public static final String PLAYER_AIDJ_GUIDE =
            "com.netease.cloudmusic.ui.hint.aidj.PlayerAIDJSwitchGuideView";

    // privacy
    public static final String OAID_MANAGER = "com.netease.cloudmusic.oaid.MusicOaidUploadManager";
    public static final String OAID_TASK =
            "com.netease.cloudmusic.process.task.app.create.AdOaidUploadTask";
    public static final String LOG_TO_FILE_TASK =
            "com.netease.cloudmusic.process.task.app.create.NeedLogToFileTask";
    public static final String NB_LOG_TASK =
            "com.netease.cloudmusic.process.task.app.create.NBLogTask";
    public static final String STATISTIC_IMPL =
            "com.netease.cloudmusic.core.statistic.StatisticImpl";
    public static final String LOG_MONITOR =
            "com.netease.cloudmusic.statistic.monitor.LogMonitorConfigManager";

    // sets
    /** Hint and guide views hidden as a group. */
    public static final Set<String> HINT_VIEWS = new HashSet<>(Arrays.asList(
            HINT_PUSH_GUIDE,
            HINT_BOTTOM_TAB_BUBBLE,
            HINT_SCORE_DIALOG,
            HINT_LIVE_PUSH,
            HINT_VIP_RENEWAL,
            LYRICS_BOTTOM_HINT,
            LYRICS_VIDEO_TIPS,
            PLAYER_COMMENT_POP,
            PLAYER_RECOMMEND_HINT,
            PLAYER_INCENTIVE_AD,
            LIVE_PROMOTION,
            COMMENT_LIVE_HOLDER,
            NOTES_PUBLISH_GUIDE,
            NOTES_SEARCH_GUIDE,
            WIDGET_ADD_GUIDE,
            PUSH_GUIDE_BOTTOM_SHEET,
            PUSH_GUIDE_CENTER_DIALOG,
            PODCAST_BOTTOM_GUIDE,
            FM_AIDJ_SWITCH,
            PLAYER_AIDJ_GUIDE
    ));

    private Profile9581() {
    }
}
