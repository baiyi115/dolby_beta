package com.raincat.dolby_beta.view.proxy;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.ScriptHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

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
            ScriptHelper.initScript(context, false);
            ScriptHelper.startScript();
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }
}
