package com.raincat.dolby_beta.view.proxy;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.utils.Tools;
import com.raincat.dolby_beta.view.BaseDialogItem;
import com.raincat.dolby_beta.xposed.XposedCompat;

public class ProxyCoverView extends BaseDialogItem {
    public ProxyCoverView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_cover_title;
        sub = SettingHelper.proxy_cover_sub;
        key = SettingHelper.proxy_cover_key;
        setData(false, false);

        setOnClickListener(view -> {
            // Extraction (asset copy + unzip) and the root shell call must not run on the UI
            // thread: they used to freeze the settings dialog on tap.
            final boolean localScript = SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key)
                    && !SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key);
            Tools.showToastOnLooper(context, localScript ? "操作成功，脚本即将重新启动" : "操作成功");
            new Thread(() -> {
                try {
                    ScriptHelper.initScript(context, true);
                    ScriptHelper.startScript();
                } catch (Throwable t) {
                    XposedCompat.log("ProxyCoverView script restart failed");
                    XposedCompat.log(t);
                }
            }, "dolby-script-restart").start();
        });
    }
}
