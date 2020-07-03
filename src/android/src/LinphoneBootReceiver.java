package com.sip.linphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LinphoneBootReceiver extends BroadcastReceiver {

    private static final String TAG = "LinphoneSip";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_SHUTDOWN)) {
            android.util.Log.d(TAG, "[Boot Receiver] Device is shutting down, destroying Core to unregister");
        } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
            LinphoneStorage storage = new LinphoneStorage(context);

            if (!storage.getDomain().equals("")) {
                new LinphoneContext(context, true);
                LinphoneContext.instance().start(true);

                LinphoneContext.instance().runWorker();

                if (LinphoneContext.instance().mLinphoneManager.loginFromStorage()) {
                    LinphoneContext.instance().mLinphoneManager.mPrefs.setPushNotificationEnabled(true);
                    LinphoneContext.instance().runForegraundService();
                } else {
                    LinphoneContext.instance().mLinphoneManager.mCore.refreshRegisters();
                }
            }
        } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MY_PACKAGE_REPLACED)) {

        }
    }
}