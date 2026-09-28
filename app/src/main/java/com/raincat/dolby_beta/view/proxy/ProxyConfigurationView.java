package com.raincat.dolby_beta.view.proxy;

import android.content.Context;
import android.util.AttributeSet;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

public class ProxyConfigurationView extends BaseDialogItem {
    public ProxyConfigurationView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_configuration_title;
        key = SettingHelper.proxy_configuration_key;
        sub = SettingHelper.proxy_configuration_sub;
        setData(false, false);

        setOnClickListener(view -> {
            sendBroadcast(SettingHelper.proxy_configuration_setting);
        });
    }
}
