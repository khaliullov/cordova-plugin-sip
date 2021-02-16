package com.sip.linphone;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.sip.linphone.fragments.DoorphoneFragment;
import com.sip.linphone.fragments.TitleFragment;
import com.sip.linphone.models.Doorphone;

import ru.simdev.evo.video.R;
import ru.simdev.livetex.fragments.OnlineChatFragment1;

public class LinphoneUnlockActivity extends FragmentActivity {
    private static final String TAG = "LinphoneSip";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    public boolean edit = false;

    private String doorphone = "";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

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
            edit = true;

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

        getSupportFragmentManager().beginTransaction().replace(R.id.config_unlock_container, new TitleFragment()).addToBackStack(getClass().getName()).commit();
    }

    public void showDoorphoneFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.config_unlock_container, new DoorphoneFragment()).addToBackStack(getClass().getName()).commit();
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