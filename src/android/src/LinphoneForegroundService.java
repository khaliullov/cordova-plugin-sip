package com.sip.linphone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;

import java.util.Timer;
import java.util.TimerTask;

import io.ionic.starter.MainActivity;
import io.ionic.starter.R;

public class LinphoneForegroundService extends Service {
    private static final String TAG = "LinphoneSip";
    public static final String CHANNEL_ID = "EvolifeSipService";
    private static final int ID = 214923423;
    private CoreListenerStub mListener;
    public static Timer mTimer;
    private RegistrationState prevState;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.util.Log.d(TAG, "run service");

        createNotificationChannel();

        Notification notification = getNotification(false);

        startForeground(ID, notification);

        mListener = new CoreListenerStub() {
            @Override
            public void onRegistrationStateChanged(final Core core, final ProxyConfig proxy, final RegistrationState state, String smessage) {
                if (state != RegistrationState.Progress && (prevState == null || prevState != state)) {
                    android.util.Log.d(TAG, "update notification " + (prevState != null ? prevState.toString() : "null") + " - " + state.toString());
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.notify(ID, getNotification(state == RegistrationState.Ok));

                    prevState = state;
                }
            }
        };

        if (LinphoneMiniManager.mCore != null) {
            LinphoneMiniManager.mCore.addListener(mListener);
            ProxyConfig lpc = LinphoneMiniManager.mCore.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(LinphoneMiniManager.mCore, lpc, lpc.getState(), null);
            }
        }

        TimerTask lTask = new TimerTask() {
            @Override
            public void run() {
                android.util.Log.d(TAG, "TIMER");
                if (LinphoneMiniManager.mCore != null) {
                    android.util.Log.d(TAG, "TIMER refreshRegisters");
                    LinphoneMiniManager.mCore.refreshRegisters();
                }
            }
        };

        mTimer = new Timer("LinphoneMini scheduler 2");
        mTimer.schedule(lTask, 0, 60000);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mTimer.cancel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        android.util.Log.d(TAG, "bind service");
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Evo Life Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification getNotification(Boolean connected) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        int color = 0xFF4A47EC;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Домофон")
                .setContentText(connected ? "подключен" : "не подключен")
                .setSmallIcon(R.drawable.icon)
                .setColor(color)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }
}