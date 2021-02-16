package com.sip.linphone.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.sip.linphone.LinphoneUnlockActivity;

import ru.simdev.evo.components.view.TextViewHeader;
import ru.simdev.evo.video.R;
import ru.simdev.evo.components.fragments.BaseFragment;

public class TitleFragment extends BaseFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (getArguments() != null) {
            String oldTitle = getArguments().getString("title", "Домофон");
            EditText text = view.findViewById(R.id.config_unlock_title);
            text.setText(oldTitle);
        }

        Button button = view.findViewById(R.id.config_unlock_add);

        button.setOnClickListener((View v) -> {
            EditText editText = view.findViewById(R.id.config_unlock_title);
            String title = editText.getText().toString();

            android.util.Log.d(TAG, "[UNLOCK] title: " + title);

            LinphoneUnlockActivity activity =  (LinphoneUnlockActivity) getParentActivity();
            activity.createWidget(title);
        });

        return view;
    }

    protected int getLayoutId() {
        return R.layout.fragment_linphone_title;
    }

    @Override
    public View getCustomActionBarView(LayoutInflater inflater, int actionBarHeight) {
        View header = inflater.inflate(R.layout.evo_default_header, null);

        TextViewHeader headerText = header.findViewById(R.id.ec_headerTitle);
        headerText.setText("Название для кнопки");

        ImageView goBack = header.findViewById(R.id.ec_goBack);
        goBack.setOnClickListener((View v) -> {
            LinphoneUnlockActivity activity =  (LinphoneUnlockActivity) getParentActivity();

            if (activity.edit) {
                activity.cancelCreate();
            } else {
                activity.onBackPressed();
            }

        });

        return header;
    }
}
