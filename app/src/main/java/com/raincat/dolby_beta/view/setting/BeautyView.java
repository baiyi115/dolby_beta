package com.raincat.dolby_beta.view.setting;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

public class BeautyView extends BaseDialogItem {
    public BeautyView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.beauty_title;
        key = SettingHelper.beauty_key;
        setData(false, false);

        setOnClickListener(view -> {
            sendBroadcast(SettingHelper.beauty_setting);
        });
    }
}
