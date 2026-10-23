package com.raincat.dolby_beta.view.ad;

import android.content.Context;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogItem;

/** Entry row for the ad sub-page; opens it, no checkbox. */
public class AdFilterView extends BaseDialogItem {
    public AdFilterView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.ad_filter_title;
        sub = SettingHelper.ad_filter_sub;
        setData(false, false);

        setOnClickListener(view -> sendBroadcast(SettingHelper.ad_filter_setting));
    }
}
