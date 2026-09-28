package com.raincat.dolby_beta.hook;

import android.content.Context;

import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.hook.proxy.PlayerResponseHook;
import com.raincat.dolby_beta.hook.proxy.ProxyTransport;
import com.raincat.dolby_beta.hook.proxy.TrialStateHook;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

/** Entry point of the proxy feature: resolves the version-dependent fields and delegates the hooks. */
public class ProxyHook {
    private final String fieldSSLSocketFactory;
    private final String fieldHttpUrl;
    private final String fieldProxy;
    private final Class<?> cronetInterceptorClass;
    private static Context appContext;

    public ProxyHook(Context context, boolean isPlayProcess) {
        appContext = context.getApplicationContext();
        cronetInterceptorClass = ProxyTransport.resolveCronetInterceptor(context);
        Class<?> realCallClass = findClassIfExists("okhttp3.internal.connection.RealCall", context.getClassLoader());
        if (realCallClass != null) {
            fieldSSLSocketFactory = "sslSocketFactoryOrNull";
            fieldHttpUrl = "url";
            fieldProxy = "proxy";
        } else {
            realCallClass = findClassIfExists("okhttp3.RealCall", context.getClassLoader());
            if (realCallClass != null) {
                fieldSSLSocketFactory = "sslSocketFactory";
                fieldHttpUrl = "url";
                fieldProxy = "proxy";
            } else {
                realCallClass = findClassIfExists("okhttp3.z", context.getClassLoader());
                fieldSSLSocketFactory = "o";
                fieldHttpUrl = "a";
                fieldProxy = "d";
            }
        }

        ProxyTransport transport = new ProxyTransport(context, fieldSSLSocketFactory, fieldProxy,
                cronetInterceptorClass);
        transport.install(realCallClass);
        new PlayerResponseHook(context).install();
        new TrialStateHook(context).install();

        if (!isPlayProcess) {
            ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
            if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)) {
                ScriptHelper.initScript(context, false);
                if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key)) {
                    ScriptHelper.startHttpProxyMode(context);
                } else {
                    ScriptHelper.startScript();
                }
            }
        }
    }
}
