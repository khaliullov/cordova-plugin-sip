package com.sip.linphone.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.Fragment;

import com.sip.linphone.LinphoneUnlockActivity;

import ru.simdev.evo.video.R;

public class TitleFragment extends Fragment {
    private static final String TAG = "LinphoneSip";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_linphone_title, container, false);

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

            getParentActivity().createWidget(title);
        });

        Button cButton = view.findViewById(R.id.config_unlock_cancel);

        cButton.setOnClickListener((View v) -> {
            getParentActivity().cancelCreate();
        });

        return view;
    }

    public LinphoneUnlockActivity getParentActivity() {
        return (LinphoneUnlockActivity) getActivity();
    }
}
