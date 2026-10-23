package com.raincat.dolby_beta.view;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;

/** Reusable switch row; avoids one class per toggle. */
public class ToggleRow extends BaseDialogItem {

    public ToggleRow(Context context, String title, String sub, String key) {
        super(context);
        apply(title, sub, key);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
    }

    private void apply(String rowTitle, String rowSub, String rowKey) {
        title = rowTitle;
        sub = rowSub;
        key = rowKey;
        setData(true, SettingHelper.getInstance().getSetting(rowKey));

        setOnClickListener(view -> {
            boolean next = !checkBox.isChecked();
            SettingHelper.getInstance().setSetting(rowKey, next);
            checkBox.setChecked(next);
            sendBroadcast(SettingHelper.refresh_setting);
        });
    }
}
