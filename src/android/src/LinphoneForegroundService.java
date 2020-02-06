package com.sip.linphone;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;

import java.util.Timer;
import java.util.TimerTask;

public class LinphoneForegroundService extends Service {
    private static final String TAG = "LinphoneSip";
    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";
    private CoreListenerStub mListener;
    public static Timer mTimer;
    private RegistrationState prevState;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        switch (action) {
            case ACTION_STOP_FOREGROUND_SERVICE:
                stopForegroundService();
                break;

            case ACTION_START_FOREGROUND_SERVICE:
                android.util.Log.d(TAG, "run service");

                startForeground(LinphoneContext.NOTIFICATION_ID, LinphoneContext.getServiceNotification(this, false));

                mListener = new CoreListenerStub() {
                    @Override
                    public void onRegistrationStateChanged(final Core core, final ProxyConfig proxy, final RegistrationState state, String smessage) {
                        if (state != RegistrationState.Progress && (prevState == null || prevState != state)) {
                            android.util.Log.d(TAG, "update notification " + (prevState != null ? prevState.toString() : "null") + " - " + state.toString());

                            LinphoneContext.isConnected = state == RegistrationState.Ok;
                            LinphoneContext.instance().showNotification();

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

                //return START_NOT_STICKY;
                break;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        LinphoneMiniManager.mCore.removeListener(mListener);
        mTimer.cancel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        android.util.Log.d(TAG, "bind service");
        return null;
    }

    private void stopForegroundService()
    {
        android.util.Log.d(TAG, "Stop foreground service.");

        stopForeground(true);
        stopSelf();
    }

}