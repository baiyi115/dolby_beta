package com.raincat.dolby_beta;

import android.content.pm.ApplicationInfo;
import android.text.TextUtils;

import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.xposed.XposedCompat;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class HookEntry extends XposedModule {

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        XposedCompat.init(this);
        XposedCompat.logInfo("v" + com.raincat.dolby_beta.BuildConfig.VERSION_NAME + " onModuleLoaded process=" + param.getProcessName()
                + " api=" + getApiVersion() + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String packageName = param.getPackageName();
        XposedCompat.logInfo("onPackageLoaded: " + packageName);
        if (TextUtils.isEmpty(packageName))
            return;

        try {
            ApplicationInfo moduleInfo = getModuleApplicationInfo();
            if (moduleInfo != null && !TextUtils.isEmpty(moduleInfo.sourceDir))
                ScriptHelper.modulePath = moduleInfo.sourceDir;
            XposedCompat.logInfo("module apk path=" + ScriptHelper.modulePath);
        } catch (Throwable t) {
            XposedCompat.log("module path resolve failed");
            XposedCompat.log(t);
        }

        try {
            if (packageName.equals("com.netease.cloudmusic")) {
                new Hook(param.getDefaultClassLoader());
                XposedCompat.logInfo("Hook installed for " + packageName);
            } else if (packageName.equals("com.netease.cloudmusic.lite")
                    || packageName.equals("com.hihonor.cloudmusic")) {
                new HookOther(param.getDefaultClassLoader(), packageName);
                XposedCompat.logInfo("HookOther installed for " + packageName);
            }
        } catch (Throwable t) {
            XposedCompat.log("init failed");
            XposedCompat.log(t);
        }
    }
}
