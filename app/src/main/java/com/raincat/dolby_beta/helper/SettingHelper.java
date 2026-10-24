package com.raincat.dolby_beta.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.HashMap;

public class SettingHelper {
    public static final String refresh_setting = "β_refresh_setting";
    public static final String proxy_setting = "β_proxy_setting";
    public static final String beauty_setting = "β_beauty_setting";
    public static final String proxy_configuration_setting = "β_proxy_configuration_setting";
    public static final String ad_filter_setting = "β_ad_filter_setting";
    public static final String privacy_setting = "β_privacy_setting";

    public static final String master_key = "β_master_key";
    public static final String master_title = "总开关";

    public static final String black_key = "β_black_key";
    public static final String black_title = "本地黑胶";
    public static final String black_sub = "黑胶标志、个性换肤、鲸云音效等本地权益显示（需访问网易服务器的设置不可用）";

    public static final String ad_remove_key = "β_ad_remove_key";
    public static final String ad_remove_title = "去广告";
    public static final String ad_remove_sub = "屏蔽启动页广告、首页广告卡片与升级提示";

    public static final String proxy_key = "β_proxy_key";
    public static final String proxy_title = "音源代理设置";

    public static final String proxy_configuration_key = "β_proxy_configuration_key";
    public static final String proxy_configuration_title = "代理参数配置";
    public static final String proxy_configuration_sub = "在此填入对于代理服务器与相关脚本参数";

    public static final String proxy_master_key = "β_proxy_master_key";
    public static final String proxy_master_title = "代理开关";

    public static final String proxy_server_key = "β_proxy_server_key";
    public static final String proxy_server_title = "服务器代理模式";
    public static final String proxy_server_sub = "如果您不想使用高占用的node，有自己的服务器代理可使用此方式并填写自己的服务器地址与端口，且使用服务器对应音质";

    public static final String proxy_priority_key = "β_proxy_priority_key";
    public static final String proxy_priority_title = "音质优先";
    public static final String proxy_priority_sub = "音质优先：使用外部音源提高音质，不可避免的会增大匹配错误概率\n匹配度优先：尽可能采用网易云音源，但非会员很多曲目只有128K/96K";

    public static final String proxy_flac_key = "β_proxy_flac_key";
    public static final String proxy_flac_title = "无损音质优先";
    public static final String proxy_flac_sub = "使用外部音源时优先获取无损音质，但并不是100%能获取到无损音质";

    public static final String http_proxy_key = "β_http_proxy_key";
    public static final String http_proxy_title = "代理服务器";
    public static final String http_proxy_default = "127.0.0.1";

    public static final String qq_cookie_key = "β_qq_cookie_key";
    public static final String qq_cookie_title = "QQCookie";
    public static final String qq_cookie_default = "uin=<your_uin>; qm_keyst=<your_qm_keyst>";

    public static final String migu_cookie_key = "β_migu_cookie_key";
    public static final String migu_cookie_title = "咪咕Cookie";
    public static final String migu_cookie_default = "<your_aversionid>";

    public static final String proxy_port_key = "β_proxy_port_key";
    public static final String proxy_port_title = "代理端口（1~65535）";
    public static final int proxy_port_default = 23338;

    public static final String proxy_original_key = "β_proxy_original_key";
    public static final String proxy_original_title = "代理源（空格隔开）";
    public static final String proxy_original_default = "pyncmd kuwo bodian";

    public static final String proxy_cover_key = "β_proxy_cover_key";
    public static final String proxy_cover_title = "重新释放脚本";
    public static final String proxy_cover_sub = "当更新后或者发现UnblockNeteaseMusic运行不正常时可尝试重新释放脚本";

    public static final String beauty_key = "β_beauty_key";
    public static final String beauty_title = "美化设置";

    public static final String beauty_tab_hide_key = "β_beauty_tab_hide_key";
    public static final String beauty_tab_hide_title = "精简Tab";
    public static final String beauty_tab_hide_sub = "首页仅保留“我的”与“发现”，并默认显示“我的";

    // AD filtering: its own sub-page, decoupled from black_key

    public static final String ad_filter_title = "广告净化";
    public static final String ad_filter_sub = "开屏、弹窗、搜索、信息流、评论、播放页、音频贴片";

    public static final String ad_splash_key = "β_ad_splash_key";
    public static final String ad_splash_title = "开屏广告";
    public static final String ad_splash_sub = "判定/数据源/落地页三层拦截，含摇一摇与免广实验";

    public static final String ad_launch_dialog_key = "β_ad_launch_dialog_key";
    public static final String ad_launch_dialog_title = "运营弹窗";
    public static final String ad_launch_dialog_sub = "启动时的运营/营销弹窗，保留一键登录";

    public static final String ad_report_key = "β_ad_report_key";
    public static final String ad_report_title = "上报与外跳";
    public static final String ad_report_sub = "广告统计上报、静默拉起外部 App、曝光预请求";

    public static final String ad_search_key = "β_ad_search_key";
    public static final String ad_search_title = "搜索广告";
    public static final String ad_search_sub = "搜索起始页广告位、结果大图/中图广告";

    public static final String ad_preload_key = "β_ad_preload_key";
    public static final String ad_preload_title = "广告预加载";
    public static final String ad_preload_sub = "冷启动广告入口、播客激励广告预加载";

    public static final String ad_feed_key = "β_ad_feed_key";
    public static final String ad_feed_title = "首页信息流";
    public static final String ad_feed_sub = "推荐流推广卡片";

    public static final String ad_comment_key = "β_ad_comment_key";
    public static final String ad_comment_title = "评论区广告";
    public static final String ad_comment_sub = "评论条目广告字段、推广条、带货标签";

    public static final String ad_player_key = "β_ad_player_key";
    public static final String ad_player_title = "播放页广告";
    public static final String ad_player_sub = "黑胶广告层、送礼贴纸、全屏广告";

    public static final String ad_motivation_key = "β_ad_motivation_key";
    public static final String ad_motivation_title = "激励广告与免费听";
    public static final String ad_motivation_sub = "底部免听提示条、首页问候语广告、激励弹窗";

    public static final String ad_audio_clip_key = "β_ad_audio_clip_key";
    public static final String ad_audio_clip_title = "音频贴片";
    public static final String ad_audio_clip_sub = "音频贴片、播客视频广告（含 :play 进程）";

    /** 所有广告净化子项，供旧广告通路的兜底判断使用。 */
    private static final String[] AD_KEYS = {
            ad_splash_key, ad_launch_dialog_key, ad_report_key, ad_search_key,
            ad_preload_key, ad_feed_key, ad_comment_key, ad_player_key,
            ad_audio_clip_key, ad_motivation_key
    };

    // UI simplification: rows on the existing beauty sub-page

    public static final String ui_home_blocks_key = "β_ui_home_blocks_key";
    public static final String ui_home_blocks_title = "首页模块净化";
    public static final String ui_home_blocks_sub = "按 blockCode 隐藏发现页卡片，需在下方填写隐藏清单";

    public static final String ui_top_channels_key = "β_ui_top_channels_key";
    public static final String ui_top_channels_title = "顶部频道管理";
    public static final String ui_top_channels_sub = "按保留清单精简顶部 tab，同时解锁删除与拖拽";

    public static final String ui_update_dialog_key = "β_ui_update_dialog_key";
    public static final String ui_update_dialog_title = "更新弹窗";
    public static final String ui_update_dialog_sub = "版本检查、强更弹窗、应用内下载入口";

    public static final String ui_startup_delay_key = "β_ui_startup_delay_key";
    public static final String ui_startup_delay_title = "跳过启动延迟";
    public static final String ui_startup_delay_sub = "去掉红屏人为等待";

    public static final String ui_hints_key = "β_ui_hints_key";
    public static final String ui_hints_title = "引导与气泡";
    public static final String ui_hints_sub = "推送/评分/VIP/直播/底栏气泡等提示";

    public static final String ui_autoplay_key = "β_ui_autoplay_key";
    public static final String ui_autoplay_title = "禁止自动播放";
    public static final String ui_autoplay_sub = "冷启动、蓝牙、4G、云村自动播放";

    public static final String ui_player_hints_key = "β_ui_player_hints_key";
    public static final String ui_player_hints_title = "播放页提示";
    public static final String ui_player_hints_sub = "评论气泡、推荐歌曲提示、卡顿弹窗";

    public static final String ui_search_guide_key = "β_ui_search_guide_key";
    public static final String ui_search_guide_title = "搜索热词";
    public static final String ui_search_guide_sub = "关闭搜索框运营热词轮播";

    public static final String ui_lyrics_hint_key = "β_ui_lyrics_hint_key";
    public static final String ui_lyrics_hint_title = "歌词页推广";
    public static final String ui_lyrics_hint_sub = "歌词底部 Hint、歌词视频弹窗";

    public static final String ui_community_live_key = "β_ui_community_live_key";
    public static final String ui_community_live_title = "云村与直播";
    public static final String ui_community_live_sub = "直播推广位、云村发布/搜索气泡";

    public static final String ui_home_blocks_filter_key = "β_ui_home_blocks_filter_key";
    public static final String ui_home_blocks_filter_title = "隐藏的 blockCode（逗号分隔）";
    public static final String ui_home_blocks_filter_default = "";

    public static final String ui_top_channels_keep_key = "β_ui_top_channels_keep_key";
    public static final String ui_top_channels_keep_title = "保留的频道名（逗号分隔）";
    public static final String ui_top_channels_keep_default = "";

    // Privacy: its own sub-page, all off by default

    public static final String privacy_title = "隐私保护";
    public static final String privacy_sub = "OAID、日志写盘、启动任务、APM、统计、剪贴板";

    public static final String pv_oaid_key = "β_pv_oaid_key";
    public static final String pv_oaid_title = "OAID 拦截";
    public static final String pv_oaid_sub = "OAID 上传与启动任务";

    public static final String pv_log_tasks_key = "β_pv_log_tasks_key";
    public static final String pv_log_tasks_title = "日志写盘";
    public static final String pv_log_tasks_sub = "NeedLogToFileTask / NBLogTask";

    public static final String pv_startup_blacklist_key = "β_pv_startup_blacklist_key";
    public static final String pv_startup_blacklist_title = "启动任务黑名单";
    public static final String pv_startup_blacklist_sub = "按 taskName 跳过非必要启动任务（暂未实装）";

    public static final String pv_apm_key = "β_pv_apm_key";
    public static final String pv_apm_title = "APM 上报";
    public static final String pv_apm_sub = "抑制 logApm / forceApmUpload，不拦初始化";

    public static final String pv_stats_key = "β_pv_stats_key";
    public static final String pv_stats_title = "统计上报";
    public static final String pv_stats_sub = "关闭监控采样开关，拦截监控上报";

    public static final String pv_clipboard_key = "β_pv_clipboard_key";
    public static final String pv_clipboard_title = "剪贴板读取";
    public static final String pv_clipboard_sub = "拦截目标进程内的剪贴板读取";

    private static SettingHelper instance;

    private SharedPreferences sharedPreferences;
    private HashMap<String, Boolean> settingMap;

    public static SettingHelper getInstance() {
        return instance;
    }

    private SettingHelper(Context context) {
        refreshSetting(context);
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new SettingHelper(context);
        }
    }

    public void refreshSetting(Context context) {
        sharedPreferences = context.getSharedPreferences("com.netease.cloudmusic.preferences", Context.MODE_MULTI_PROCESS);
        settingMap = new HashMap<>();

        settingMap.put(master_key, sharedPreferences.getBoolean(master_key, true));
        settingMap.put(black_key, sharedPreferences.getBoolean(black_key, true));
        settingMap.put(ad_remove_key, sharedPreferences.getBoolean(ad_remove_key, true));
        settingMap.put(proxy_master_key, sharedPreferences.getBoolean(proxy_master_key, true));
        settingMap.put(proxy_server_key, sharedPreferences.getBoolean(proxy_server_key, false));
        settingMap.put(proxy_priority_key, sharedPreferences.getBoolean(proxy_priority_key, false));
        settingMap.put(proxy_flac_key, sharedPreferences.getBoolean(proxy_flac_key, false));

        settingMap.put(beauty_tab_hide_key, sharedPreferences.getBoolean(beauty_tab_hide_key, false));

        // 广告净化：默认全开（与原 ad_remove_key 行为对齐）
        settingMap.put(ad_splash_key, sharedPreferences.getBoolean(ad_splash_key, true));
        settingMap.put(ad_launch_dialog_key, sharedPreferences.getBoolean(ad_launch_dialog_key, true));
        settingMap.put(ad_report_key, sharedPreferences.getBoolean(ad_report_key, true));
        settingMap.put(ad_search_key, sharedPreferences.getBoolean(ad_search_key, true));
        settingMap.put(ad_preload_key, sharedPreferences.getBoolean(ad_preload_key, true));
        settingMap.put(ad_feed_key, sharedPreferences.getBoolean(ad_feed_key, true));
        settingMap.put(ad_comment_key, sharedPreferences.getBoolean(ad_comment_key, true));
        settingMap.put(ad_player_key, sharedPreferences.getBoolean(ad_player_key, true));
        settingMap.put(ad_audio_clip_key, sharedPreferences.getBoolean(ad_audio_clip_key, true));
        settingMap.put(ad_motivation_key, sharedPreferences.getBoolean(ad_motivation_key, false));

        // UI 精简：默认关（保守，避免影响原有体验）
        settingMap.put(ui_home_blocks_key, sharedPreferences.getBoolean(ui_home_blocks_key, false));
        settingMap.put(ui_top_channels_key, sharedPreferences.getBoolean(ui_top_channels_key, false));
        settingMap.put(ui_update_dialog_key, sharedPreferences.getBoolean(ui_update_dialog_key, false));
        settingMap.put(ui_startup_delay_key, sharedPreferences.getBoolean(ui_startup_delay_key, false));
        settingMap.put(ui_hints_key, sharedPreferences.getBoolean(ui_hints_key, false));
        settingMap.put(ui_autoplay_key, sharedPreferences.getBoolean(ui_autoplay_key, false));
        settingMap.put(ui_player_hints_key, sharedPreferences.getBoolean(ui_player_hints_key, false));
        settingMap.put(ui_search_guide_key, sharedPreferences.getBoolean(ui_search_guide_key, false));
        settingMap.put(ui_lyrics_hint_key, sharedPreferences.getBoolean(ui_lyrics_hint_key, false));
        settingMap.put(ui_community_live_key, sharedPreferences.getBoolean(ui_community_live_key, false));

        // 隐私保护：默认全关
        settingMap.put(pv_oaid_key, sharedPreferences.getBoolean(pv_oaid_key, false));
        settingMap.put(pv_log_tasks_key, sharedPreferences.getBoolean(pv_log_tasks_key, false));
        settingMap.put(pv_startup_blacklist_key, sharedPreferences.getBoolean(pv_startup_blacklist_key, false));
        settingMap.put(pv_apm_key, sharedPreferences.getBoolean(pv_apm_key, false));
        settingMap.put(pv_stats_key, sharedPreferences.getBoolean(pv_stats_key, false));
        settingMap.put(pv_clipboard_key, sharedPreferences.getBoolean(pv_clipboard_key, false));
    }

    /** 广告净化子项是否启用（受子页总开关与模块总开关共同约束）。 */
    public boolean isAdEnabled(String key) {
        return getSetting(master_key) && getSetting(key);
    }

    /** UI 精简子项是否启用。 */
    public boolean isUiEnabled(String key) {
        return getSetting(master_key) && getSetting(key);
    }

    /** 隐私保护子项是否启用。 */
    public boolean isPrivacyEnabled(String key) {
        return getSetting(master_key) && getSetting(key);
    }

    /**
     * 旧广告通路（OkHttp URL 拦截、广告对象参数清空）的兜底条件。
     * 任意子项开启即生效，全部关闭时彻底停用；细粒度拦截仍由各自的 hook 单独判断。
     * 之前旧通路只认 ad_remove_key，而该开关已从设置界面移除，导致子项关闭后仍然被拦截。
     */
    public boolean isAnyAdEnabled() {
        if (!getSetting(master_key))
            return false;
        for (String key : AD_KEYS)
            if (getSetting(key))
                return true;
        return false;
    }

    public String getHomeBlocksFilter() {
        return sharedPreferences.getString(ui_home_blocks_filter_key, "");
    }

    /** 读取任意字符串配置（给 ToggleRow / InputRow 这类通用控件用）。 */
    public String getString(String key, String def) {
        try {
            String v = sharedPreferences.getString(key, def);
            return v == null ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }

    public void putString(String key, String value) {
        try {
            sharedPreferences.edit().putString(key, value == null ? "" : value).apply();
        } catch (Throwable ignored) {
        }
    }

    public void setHomeBlocksFilter(String value) {
        sharedPreferences.edit().putString(ui_home_blocks_filter_key,
                value == null ? "" : value).apply();
    }

    public String getTopChannelsKeep() {
        return sharedPreferences.getString(ui_top_channels_keep_key, "");
    }

    public void setTopChannelsKeep(String value) {
        sharedPreferences.edit().putString(ui_top_channels_keep_key,
                value == null ? "" : value).apply();
    }

    public void setSetting(String key, boolean value) {
        settingMap.put(key, value);
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public boolean getSetting(String key) {
        // Unboxing here threw for any key that has no refreshSetting() row (the pure UI keys such as
        // proxy_cover_key are constants only), which is a crash waiting for the first caller to read
        // one while its checkbox is visible.
        Boolean value = settingMap.get(key);
        return value != null && value;
    }

    public boolean isEnable(String key) {
        return getSetting(master_key) && getSetting(key);
    }

    private void deleteSetting(String key) {
        if (sharedPreferences.contains(key)) {
            sharedPreferences.edit().remove(key).apply();
        }
    }

    public void resetSetting() {
        deleteSetting(master_key);
        deleteSetting(black_key);
        deleteSetting(ad_remove_key);
        deleteSetting(proxy_master_key);
        deleteSetting(proxy_server_key);
        deleteSetting(proxy_priority_key);
        deleteSetting(proxy_flac_key);
        deleteSetting(beauty_tab_hide_key);

        String[] legacy = {"β_dex_key", "β_warn_key", "β_listen_key", "β_fix_comment_key",
                "β_update_key", "β_beauty_bubble_hide_key", "β_beauty_banner_hide_key",
                "β_beauty_ksong_key", "β_beauty_black_key", "β_beauty_rotation_key",
                "β_beauty_comment_hot_key", "β_beauty_sidebar_hide_key",
                "β_kuwo_cookie_key", "β_proxy_gray_key"};
        for (String key : legacy)
            deleteSetting(key);
    }

    public int getProxyPort() {
        return sharedPreferences.getInt(SettingHelper.proxy_port_key, SettingHelper.proxy_port_default);
    }

    public void setProxyPort(String port) {
        if (!TextUtils.isEmpty(port))
            sharedPreferences.edit().putInt(SettingHelper.proxy_port_key, Integer.parseInt(port)).apply();
    }

    public String getProxyOriginal() {
        String original = sharedPreferences.getString(
                SettingHelper.proxy_original_key,
                SettingHelper.proxy_original_default);
        if ("pyncmd kuwo".equals(original))
            original = SettingHelper.proxy_original_default;
        return original;
    }

    public void setProxyOriginal(String original) {
        if (!TextUtils.isEmpty(original))
            sharedPreferences.edit().putString(SettingHelper.proxy_original_key, original).apply();
    }

    public void setHttpProxy(String http) {
        if (!TextUtils.isEmpty(http))
            sharedPreferences.edit().putString(SettingHelper.http_proxy_key, http).apply();
    }

    public String getHttpProxy() {
        return sharedPreferences.getString(SettingHelper.http_proxy_key, SettingHelper.http_proxy_default);
    }

    public String getQqCookie() {
        return sharedPreferences.getString(SettingHelper.qq_cookie_key, SettingHelper.qq_cookie_default);
    }
    public void setQqCookie(String cookie) {
        if (!TextUtils.isEmpty(cookie))
            sharedPreferences.edit().putString(SettingHelper.qq_cookie_key, cookie).apply();
    }
    public String getMiguCookie() {
        return sharedPreferences.getString(SettingHelper.migu_cookie_key, SettingHelper.migu_cookie_default);
    }
    public void setMiguCookie(String cookie) {
        if (!TextUtils.isEmpty(cookie))
            sharedPreferences.edit().putString(SettingHelper.migu_cookie_key, cookie).apply();
    }
}
