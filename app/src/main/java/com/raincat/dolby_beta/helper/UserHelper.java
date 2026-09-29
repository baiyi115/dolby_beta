package com.raincat.dolby_beta.helper;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.raincat.dolby_beta.model.UserInfoBean;
import com.raincat.dolby_beta.net.Http;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.util.HashMap;

public class UserHelper {

    /**
     * Runs on a bare thread from UserProfileHook. Every failure mode of that call used to escape
     * into the default uncaught-exception handler and kill the host process: Http returns the
     * exception message (or "") on a failure, gson throws JsonSyntaxException on that text, and
     * fromJson("") returns null whose getProfile() then threw.
     */
    public static void getUserInfo() {
        try {
            HashMap<String, Object> headers = new HashMap<>();
            headers.put("cookie", ExtraHelper.getExtraDate(ExtraHelper.COOKIE));
            String userInfo = new Http("GET", "https://music.163.com/api/nuser/account/get", headers, (String) null).getResult();
            if (TextUtils.isEmpty(userInfo) || userInfo.charAt(0) != '{') {
                XposedCompat.logInfo("UserHelper account/get returned no JSON, will retry later");
                return;
            }
            UserInfoBean userInfoBean = new Gson().fromJson(userInfo, UserInfoBean.class);
            if (userInfoBean == null)
                return;
            long userId = userInfoBean.getProfile().getUserId();
            if (userId <= 0) {
                XposedCompat.logInfo("UserHelper account/get has no userId, will retry later");
                return;
            }
            ExtraHelper.setExtraDate(ExtraHelper.USER_ID, userId);
        } catch (Throwable t) {
            XposedCompat.log("UserHelper.getUserInfo failed");
            XposedCompat.log(t);
        }
    }
}
