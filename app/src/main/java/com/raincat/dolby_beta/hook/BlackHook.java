package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.MethodReplacement;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.content.Context;

import com.raincat.dolby_beta.helper.ExtraHelper;

import org.json.JSONObject;

public class BlackHook {
    private static final java.util.Set<Long> loggedUserId = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static boolean profileListLogged;

    public BlackHook(Context context, int versionCode) {
        if (versionCode < 138) {
            XposedCompat.hookAllMethods(findClass("com.netease.cloudmusic.meta.Profile", context.getClassLoader()), "setUserPoint", new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if ((long) XposedCompat.callMethod(param.thisObject, "getUserId") == Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID))) {
                        XposedCompat.callMethod(param.thisObject, "setVipType", 100);
                        XposedCompat.callMethod(param.thisObject, "setVipProExpireTime", System.currentTimeMillis() + 31536000000L);
                        XposedCompat.callMethod(param.thisObject, "setExpireTime", System.currentTimeMillis() + 31536000000L);
                    }
                }
            });

            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "i", MethodReplacement.returnConstant(0));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "j", MethodReplacement.returnConstant("免费"));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "o", MethodReplacement.returnConstant(false));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "s", MethodReplacement.returnConstant(false));
        } else {
            findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.getClassLoader()),
                    "fromJson", JSONObject.class, new MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            try {
                                JSONObject object = (JSONObject) param.args[0];
                                if (object == null || object.optInt("code") != 200 || object.isNull("data"))
                                    return;
                                JSONObject data = object.getJSONObject("data");
                                if (data.isNull("userId"))
                                    return;
                                long userId = data.optLong("userId");
                                if (userId != Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID)))
                                    return;

                                long expireTime = System.currentTimeMillis() + 31536000000L;
                                JSONObject associator = data.optJSONObject("associator");
                                if (associator == null) {
                                    associator = new JSONObject();
                                    data.put("associator", associator);
                                }
                                associator.put("expireTime", expireTime);
                                associator.put("vipCode", 100);
                                associator.put("vipLevel", 9);

                                JSONObject musicPackage = data.optJSONObject("musicPackage");
                                if (musicPackage == null) {
                                    musicPackage = new JSONObject();
                                    data.put("musicPackage", musicPackage);
                                }
                                musicPackage.put("expireTime", expireTime);
                                musicPackage.put("vipCode", 220);

                                data.put("redVipAnnualCount", 1);
                                data.put("redVipLevel", 9);
                                param.args[0] = object;
                                if (loggedUserId.add(userId))
                                    logInfo("BlackHook: local privilege patched, userId=" + userId);
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    });

            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "getPoints", MethodReplacement.returnConstant(0));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "getPrice", MethodReplacement.returnConstant("免费"));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "isVip", MethodReplacement.returnConstant(false));
            findAndHookMethod(findClass("com.netease.cloudmusic.theme.core.ThemeInfo", context.getClassLoader()),
                    "isDigitalAlbum", MethodReplacement.returnConstant(false));

            Class<?> userPrivilegeClass = findClass("com.netease.cloudmusic.meta.virtual.UserPrivilege", context.getClassLoader());

            findAndHookMethod(userPrivilegeClass, "fromJsonForProfileList",
                    JSONObject.class, long.class, new MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            try {
                                long userId = (long) param.args[1];
                                if (userId != Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID)))
                                    return;
                                JSONObject object = (JSONObject) param.args[0];
                                if (object == null)
                                    return;
                                long expireTime = System.currentTimeMillis() + 31536000000L;
                                patchProfilePrivilege(object, "associator", 100, expireTime);
                                patchProfilePrivilege(object, "musicPackage", 220, expireTime);
                                object.put("redVipAnnualCount", 1);
                                object.put("redVipLevel", 9);
                                param.args[0] = object;
                                if (!profileListLogged) {
                                    profileListLogged = true;
                                    logInfo("BlackHook: local profile-list privilege patched, userId=" + userId);
                                }
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    });

            XposedCompat.hookAllMethods(userPrivilegeClass, "isBlackVip", currentUserHook(true));
            XposedCompat.hookAllMethods(userPrivilegeClass, "isWhateverVip", currentUserHook(true));
            XposedCompat.hookAllMethods(userPrivilegeClass, "isWhateverMusicPackage", currentUserHook(true));
        }

        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "isVipFee", MethodReplacement.returnConstant(false));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getPlayMaxLevel", MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getDownMaxLevel", MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getFee", MethodReplacement.returnConstant(0));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getPayed", MethodReplacement.returnConstant(0));
        XposedCompat.hookAllMethods(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "isFee", MethodReplacement.returnConstant(false));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "canShare", MethodReplacement.returnConstant(true));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "getFreeLevel", MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "getPlayMaxbr", MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.SongPrivilege", context.getClassLoader()),
                "getDownloadMaxbr", MethodReplacement.returnConstant(999000));
        findAndHookMethod(findClass("com.netease.cloudmusic.meta.virtual.ResourcePrivilege", context.getClassLoader()),
                "getFlag", new MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);

                        param.setResult((int) param.getResult() & 0x8);
                    }
                });
    }

    private static void patchProfilePrivilege(JSONObject object, String name, int vipCode, long expireTime) throws Exception {
        JSONObject privilege = object.optJSONObject(name);
        if (privilege == null) {
            privilege = new JSONObject();
            object.put(name, privilege);
        }
        privilege.put("expireTime", expireTime);
        privilege.put("vipCode", vipCode);
        privilege.put("rights", true);
    }

    private static MethodHook currentUserHook(final Object value) {
        return new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Object target = param.thisObject;
                if (target == null && param.args != null && param.args.length == 1
                        && param.args[0] != null
                        && "com.netease.cloudmusic.meta.virtual.UserPrivilege".equals(param.args[0].getClass().getName()))
                    target = param.args[0];
                if (!isCurrentUser(target))
                    return;
                param.setResult(value);
            }
        };
    }

    private static boolean isCurrentUser(Object object) {
        if (object == null)
            return false;
        try {
            Object userId = XposedCompat.callMethod(object, "getUserId");
            return userId instanceof Long
                    && (long) userId == Long.parseLong(ExtraHelper.getExtraDate(ExtraHelper.USER_ID));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
