package com.sip.linphone;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.linphone.core.Core;


public class LinphoneFirebaseMessaging extends FirebaseMessagingService {
    private static final String TAG = "LinphoneSip";

    private Runnable mPushReceivedRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!LinphoneContext.isReady()) {
                        android.util.Log.i(TAG, "[Push Notification] Starting context");
                        new LinphoneContext(getApplicationContext(), true);
                        LinphoneContext.instance().start(true);
                    } else {
                        android.util.Log.i(TAG, "[Push Notification] Notifying Core");
                        if (LinphoneMiniManager.getInstance() != null) {
                            Core core = LinphoneMiniManager.mCore;
                            if (core != null) {
                                android.util.Log.i(TAG, "[Push Notification] ensureRegisterede");
                                core.ensureRegistered();
                            }
                        }
                    }
                }
            };


    @Override
    public void onNewToken(final String token) {
        super.onNewToken(token);

        android.util.Log.d(TAG, "[Push Notification] Refreshed token: " + token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        android.util.Log.d(TAG, "[Push Notification] Received");

        LinphoneContext.dispatchOnUIThread(mPushReceivedRunnable);
    }
}
