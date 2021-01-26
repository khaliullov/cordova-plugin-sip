package com.sip.linphone;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;
import org.linphone.core.Core;

import java.util.Map;

import ru.simdev.livetex.firebase.FirebaseMessageReceiver;

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

        LinphoneContext.dispatchOnUIThread(
                new Runnable() {
                    @Override
                    public void run() {
                        LinphonePreferences.instance().setPushNotificationRegistrationID(token);
                    }
                }
        );

        FirebaseMessageReceiver.saveToken(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        android.util.Log.d(TAG, "[Push Notification] Received " + remoteMessage.getFrom());
        android.util.Log.d(TAG, "[refreshUrls] Received");

        Map<String, String> data = remoteMessage.getData();

        if (data.containsKey("category") && data.get("category").equals("refresh_registration")) {
            android.util.Log.d(TAG, "[refreshUrls] refresh_registration");

            LinphoneContext.dispatchOnUIThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            LinphoneMiniManager.refreshUrls(data);
                        }
                    }
            );
        } else {
            if (remoteMessage.getNotification() == null) {
                Map<String, String> params = remoteMessage.getData();
                JSONObject object = new JSONObject(params);

                LinphoneContext.dispatchOnUIThread(mPushReceivedRunnable);
            } else {
                FirebaseMessageReceiver.sendNotification(this, remoteMessage);
            }
        }
    }
}
