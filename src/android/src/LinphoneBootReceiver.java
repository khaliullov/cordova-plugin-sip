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
            new LinphoneContext(context, true);
            LinphoneContext.instance().start(true);
        } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MY_PACKAGE_REPLACED)) {

        }
    }
}