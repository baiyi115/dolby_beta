package com.raincat.dolby_beta.view.privacy;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

/** Entry row for the privacy sub-page; opens it, no checkbox. */
public class PrivacyView extends BaseDialogItem {
    public PrivacyView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.privacy_title;
        sub = SettingHelper.privacy_sub;
        setData(false, false);

        setOnClickListener(view -> sendBroadcast(SettingHelper.privacy_setting));
    }
}
