package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;

import android.content.Context;

import com.raincat.dolby_beta.helper.ClassHelper;

import java.lang.reflect.Method;

public class CdnHook {
    public CdnHook(Context context, int versionCode) {
        if (versionCode < 138)
            return;
        for (Method m : ClassHelper.HttpInterceptor.getMethodList(context))
            XposedCompat.hookMethod(m, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    param.setResult(param.args[2]);
                }
            });
    }
}
