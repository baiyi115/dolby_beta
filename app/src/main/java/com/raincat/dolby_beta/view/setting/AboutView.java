package com.raincat.dolby_beta.view.setting;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;

import com.raincat.dolby_beta.view.BaseDialogItem;

public class AboutView extends BaseDialogItem {
    public AboutView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = "关于";
        setData(false, false);

        setOnClickListener(view -> {
            Uri uri = Uri.parse("https://github.com/nining377/dolby_beta");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            context.startActivity(intent);
        });
    }
}
