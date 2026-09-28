package com.raincat.dolby_beta.view.setting;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

public class ProxyView extends BaseDialogItem {
    public ProxyView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.proxy_title;
        key = SettingHelper.proxy_key;
        setData(false, false);

        setOnClickListener(view -> {
            sendBroadcast(SettingHelper.proxy_setting);
        });
    }
}
