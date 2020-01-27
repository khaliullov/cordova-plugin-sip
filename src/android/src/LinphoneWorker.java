package com.sip.linphone;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class LinphoneWorker extends Worker {

    private static final String TAG = "LinphoneSip";

    public LinphoneWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Worker.Result doWork() {
        // Do the work here--in this case, upload the images.
        android.util.Log.i(TAG, "[Linphone Worker] tick");

        if (!LinphoneContext.isReady()) {
            android.util.Log.e(TAG, "[Linphone Worker] Starting context");
            new LinphoneContext(getApplicationContext(), true);
            LinphoneContext.instance().start(true);

            if (LinphoneContext.instance().mLinphoneManager.loginFromStorage()) {
                LinphoneContext.instance().mLinphoneManager.mPrefs.setPushNotificationEnabled(true);
                LinphoneContext.instance().runForegraundService();
            }
        } else {

        }

        // Indicate whether the task finished successfully with the Result
        return Worker.Result.success();
    }
}