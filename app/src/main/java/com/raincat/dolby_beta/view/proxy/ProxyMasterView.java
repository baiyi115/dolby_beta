package com.raincat.dolby_beta.view.proxy;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;
import com.raincat.dolby_beta.xposed.XposedCompat;

public class ProxyMasterView extends BaseDialogItem {
    public ProxyMasterView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_master_title;
        key = SettingHelper.proxy_master_key;
        setData(true, SettingHelper.getInstance().getSetting(key));

        setOnClickListener(view -> {
            SettingHelper.getInstance().setSetting(key, !checkBox.isChecked());
            sendBroadcast(SettingHelper.refresh_setting);
            // Script extraction and the root shell call are slow; keep them off the UI thread so
            // toggling the proxy switch cannot freeze the dialog.
            new Thread(() -> {
                try {
                    ScriptHelper.initScript(context, false);
                    ScriptHelper.startScript();
                } catch (Throwable t) {
                    XposedCompat.log("ProxyMasterView script start failed");
                    XposedCompat.log(t);
                }
            }, "dolby-script-start").start();
        });
    }
}
