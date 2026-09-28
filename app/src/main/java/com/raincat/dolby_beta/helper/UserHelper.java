package com.raincat.dolby_beta.helper;

import com.google.gson.Gson;
import com.raincat.dolby_beta.model.UserInfoBean;
import com.raincat.dolby_beta.net.Http;

import java.util.HashMap;

public class UserHelper {

    public static void getUserInfo() {
        HashMap<String, Object> headers = new HashMap<>();
        headers.put("cookie", ExtraHelper.getExtraDate(ExtraHelper.COOKIE));
        String userInfo = new Http("GET", "https://music.163.com/api/nuser/account/get", headers, (String) null).getResult();
        Gson gson = new Gson();
        UserInfoBean userInfoBean = gson.fromJson(userInfo, UserInfoBean.class);
        ExtraHelper.setExtraDate(ExtraHelper.USER_ID, userInfoBean.getProfile().getUserId());
    }
}
