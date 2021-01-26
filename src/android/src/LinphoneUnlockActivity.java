package com.sip.linphone;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.sip.linphone.fragments.DoorphoneFragment;
import com.sip.linphone.fragments.TitleFragment;
import com.sip.linphone.models.Doorphone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ru.simdev.evo.video.R;
import ru.simdev.livetex.LivetexContext;
import ru.simdev.livetex.fragments.InitFragment;
import ru.simdev.livetex.fragments.OnlineChatFragment1;
import ru.simdev.livetex.utils.BusProvider;

public class LinphoneUnlockActivity extends FragmentActivity {
    private static final String TAG = "LinphoneSip";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private String doorphone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_linphone_unlock);

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();

        if (extras != null) {
            mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        android.util.Log.d(TAG, "[UNLOCK] mAppWidgetId: " + mAppWidgetId);

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }

        LinphoneStorage storage = new LinphoneStorage(this);
        String title = storage.getUnlockTitle(mAppWidgetId);
        doorphone = storage.getUnlockId(mAppWidgetId);

        if (title.equals("")) {
            getSupportFragmentManager().beginTransaction().add(R.id.config_unlock_container, new DoorphoneFragment()).commit();
        } else {
            TitleFragment fragment = new TitleFragment();

            Bundle args = new Bundle();
            args.putString("title", title);
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction().add(R.id.config_unlock_container, fragment).commit();
        }
    }

    public void showTitleFragment(Doorphone doorphone) {
        this.doorphone = doorphone.id + (doorphone.doorId.equals("") ? "" : ":" + doorphone.doorId);

        android.util.Log.d(TAG, "[UNLOCK] click " + doorphone.id + (doorphone.doorId.equals("") ? "" : ":" + doorphone.doorId));

        getSupportFragmentManager().beginTransaction().replace(R.id.config_unlock_container, new TitleFragment()).commit();
    }

    public void createWidget(String title) {
        android.util.Log.d(TAG, "[UNLOCK] title: " + title);

        LinphoneStorage storage = new LinphoneStorage(this);
        storage.setUnlockConfig(mAppWidgetId, doorphone, title);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        UnlockWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);

        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);

        finish();
    }

    public void cancelCreate() {
        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_CANCELED, resultValue);

        finish();
    }


    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            String currentFragment = getSupportFragmentManager().getBackStackEntryAt(getSupportFragmentManager().getBackStackEntryCount() - 1).getName();
            if (currentFragment != null && currentFragment.equals(OnlineChatFragment1.class.getName())) {
                finish();
                return;
            }
        }

        super.onBackPressed();
    }

}