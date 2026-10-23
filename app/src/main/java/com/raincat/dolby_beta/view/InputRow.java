package com.raincat.dolby_beta.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;

import com.raincat.dolby_beta.helper.SettingHelper;

/**
 * 通用文本输入行。
 *
 * 现有代码里每个输入项都要单独建一个 View 类（ProxyHttpView、ProxyOriginalView…），
 * 首页模块与顶部频道的清单输入直接复用本类，不再新增文件。
 */
public class InputRow extends BaseDialogInputItem {

    private String key;
    private String initial;

    public InputRow(Context context, String title, String key, String initial) {
        super(context);
        apply(title, key, initial);
    }

    @Override
    public void init(Context context, AttributeSet attrs) {
        super.init(context, attrs);
    }

    private void apply(String rowTitle, String rowKey, String rowInitial) {
        this.key = rowKey;
        this.initial = rowInitial;

        this.title = rowTitle;
        setData(SettingHelper.getInstance().getString(rowKey, rowInitial), rowInitial);

        defaultView.setOnClickListener(view -> {
            editView.setText(initial);
            editView.setSelection(editView.getText().length());
        });

        editView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                SettingHelper.getInstance().putString(key, editView.getText().toString().trim());
            }
        });
    }
}
