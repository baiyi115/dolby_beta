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
