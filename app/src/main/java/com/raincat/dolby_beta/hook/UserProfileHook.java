package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.content.Context;
import android.os.Bundle;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.UserHelper;

public class UserProfileHook {
    public UserProfileHook(Context context) {

        Class<?> userProfileClass = findClassIfExists("com.netease.cloudmusic.meta.Profile", context.getClassLoader());
        if (userProfileClass != null) {
            findAndHookMethod(userProfileClass, "setNickname", String.class, new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    String nickName = (String) param.args[0];
                    if (nickName.equals("未登录") || nickName.length() == 0)
                        return;
                    if ((boolean) XposedCompat.callMethod(param.thisObject, "isMe") && ExtraHelper.getExtraDate(ExtraHelper.USER_ID).equals("-1"))
                        ExtraHelper.setExtraDate(ExtraHelper.USER_ID, XposedCompat.callMethod(param.thisObject, "getUserId"));
                }
            });
        }

        Class<?> mainActivityClass = findClassIfExists("com.netease.cloudmusic.activity.MainActivity", context.getClassLoader());
        if (mainActivityClass != null) {
            findAndHookMethod(mainActivityClass, "onResume", new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    new Thread(() -> {
                        if (ExtraHelper.getExtraDate(ExtraHelper.COOKIE).equals("-1"))
                            ExtraHelper.setExtraDate(ExtraHelper.COOKIE, ClassHelper.Cookie.getCookie(context));
                        if (ExtraHelper.getExtraDate(ExtraHelper.USER_ID).equals("-1"))
                            UserHelper.getUserInfo();
                    }).start();
                }
            });
        }

        Class<?> loginActivityClass = findClassIfExists("com.netease.cloudmusic.activity.LoginActivity", context.getClassLoader());
        if (loginActivityClass == null)
            loginActivityClass = findClassIfExists("com.netease.cloudmusic.module.login.LoginActivity", context.getClassLoader());
        if (loginActivityClass != null) {
            findAndHookMethod(loginActivityClass, "onCreate", Bundle.class, new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    ExtraHelper.cleanUserData();
                }
            });
        }

        Class<?> playListClass = findClassIfExists("com.netease.cloudmusic.meta.PlayList", context.getClassLoader());
        if (playListClass != null) {
            findAndHookMethod(playListClass, "setSpecialType", int.class, new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if ((int) param.args[0] == 5 && ExtraHelper.getExtraDate(ExtraHelper.LOVE_PLAY_LIST).equals("-1"))
                        ExtraHelper.setExtraDate(ExtraHelper.LOVE_PLAY_LIST, XposedCompat.callMethod(param.thisObject, "getId"));
                }
            });
        }
    }
}
