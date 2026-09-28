package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;

import android.content.Context;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.SettingHelper;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.List;

public class AdExtraHook {
    public AdExtraHook(Context context) {
        if (SettingHelper.getInstance().isEnable(SettingHelper.ad_remove_key)) {
            List<Method> methods = ClassHelper.Ad.getAdMethod(context);
            if (methods != null) {
                for (Method method : methods) {
                    XposedCompat.hookMethod(method, new MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof JSONObject) {
                                    param.args[i] = new JSONObject();
                                    return;
                                }
                            }
                        }
                    });
                }
            }
        }
    }
}
