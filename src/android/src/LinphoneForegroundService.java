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
    private LinphoneStorage mStorage;
    private boolean relogin = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mStorage = new LinphoneStorage(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = ACTION_START_FOREGROUND_SERVICE;

        if (intent != null) {
            android.util.Log.d(TAG, "[Foreground Service] NULL ACTION");
            action = intent.getAction();
        }

        switch (action) {
            case ACTION_STOP_FOREGROUND_SERVICE:
                stopForegroundService();
                break;

            case ACTION_START_FOREGROUND_SERVICE:
                android.util.Log.d(TAG, "run service " + (LinphoneContext.isReady() ? "isReady" : "no"));

                if (!LinphoneContext.isReady()) {
                    android.util.Log.e(TAG, "[Foreground Service] Starting context");
                    new LinphoneContext(getApplicationContext(), true);
                    LinphoneContext.instance().start(true);
                }

                startForeground(LinphoneContext.NOTIFICATION_ID, LinphoneContext.getServiceNotification(this, false));

                mListener = new CoreListenerStub() {
                    @Override
                    public void onRegistrationStateChanged(final Core core, final ProxyConfig proxy, final RegistrationState state, String smessage) {
                        android.util.Log.d(TAG, "STATE " + state.toString());
                        if (state != RegistrationState.Progress && (prevState == null || prevState != state)) {
                            android.util.Log.d(TAG, "update notification " + (prevState != null ? prevState.toString() : "null") + " - " + state.toString());

                            LinphoneContext.isConnected = state == RegistrationState.Ok;
                            LinphoneContext.instance().showNotification();

                            mStorage.setStatus(LinphoneContext.isConnected ? "connected" : "disconnected");

                            prevState = state;

                            if (state == RegistrationState.Ok) {
                                relogin = false;
                            } else if (!relogin) {
                                if (LinphoneMiniManager.getInstance() != null) {
                                    relogin = true;
                                    LinphoneMiniManager.getInstance().loginFromStorage();
                                }
                            }
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
                    if (LinphoneMiniManager.mCore != null) {
                        LinphoneMiniManager.mCore.refreshRegisters();
                    }
                    }
                };

                mTimer = new Timer("LinphoneMini scheduler 2");
                mTimer.schedule(lTask, 0, 60000);

                //return START_NOT_STICKY;
                break;
        }

        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        //stopForegroundService();

        if (!LinphoneContext.isReady()) {
            android.util.Log.e(TAG, "[Foreground Service] Starting context");
            new LinphoneContext(getApplicationContext(), true);
            LinphoneContext.instance().start(true);
            LinphoneContext.instance().runForegraundService();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (LinphoneMiniManager.mCore != null) {
            LinphoneMiniManager.mCore.removeListener(mListener);
        }

        if (mTimer != null) {
            mTimer.cancel();
        }
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