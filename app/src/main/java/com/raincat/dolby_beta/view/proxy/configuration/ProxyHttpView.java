package com.raincat.dolby_beta.view.proxy.configuration;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogInputItem;

public class ProxyHttpView extends BaseDialogInputItem {
    public ProxyHttpView(Context context) {
        super(context);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
        title = SettingHelper.http_proxy_title;
        editView.setKeyListener(DigitsKeyListener.getInstance("0123456789.qwertyuiopasdfghjklzxcvbnm"));
        setData(SettingHelper.getInstance().getHttpProxy() + "", SettingHelper.http_proxy_default);

        defaultView.setOnClickListener(view -> {
            editView.setText(SettingHelper.http_proxy_default);
            editView.setSelection(editView.getText().length());
        });

        editView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                SettingHelper.getInstance().setHttpProxy(editView.getText().toString());
            }
        });
    }
}
