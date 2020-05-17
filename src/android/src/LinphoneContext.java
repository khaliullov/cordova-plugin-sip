package com.sip.linphone;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ru.simdev.evo.life.MainActivity;
import ru.simdev.evo.life.R;

public class LinphoneContext {
    private static final String TAG = "LinphoneSip";
    public static int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 2323;
    public static final int iconColor = 0xFF4A47EC;

    public static final String CHANNEL_ID = "EvolifeSipService";
    public static final String SERVICE_CHANNEL_ID = "EvolifeSipService";
    public static boolean channelInited = false;
    public static final int NOTIFICATION_ID = 45325623;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static LinphoneContext sInstance = null;

    public static boolean answered = false;

    public static boolean isConnected = false;
    public static boolean isCall = false;

    private Context mContext;

    private boolean mIsPush = false;
    private static boolean hasForeground = false;

    public LinphoneMiniManager mLinphoneManager;

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LinphoneContext instance() {
        return sInstance;
    }

    public static void dispatchOnUIThread(Runnable r) {
        sHandler.post(r);
    }

    public static void dispatchOnUIThreadAfter(Runnable r, long after) {
        sHandler.postDelayed(r, after);
    }

    public static void removeFromUIThreadDispatcher(Runnable r) {
        sHandler.removeCallbacks(r);
    }

    public LinphoneContext(Context context, boolean isPush) {
        mContext = context;

        sInstance = this;
        Log.i(TAG,"[Context] Ready");

        LinphonePreferences.instance().setContext(context);

        mLinphoneManager = new LinphoneMiniManager(context, isPush);
        mIsPush = isPush;

        if (!isPush) {
            runWorker();
        }
    }

    public void updateContext(Context context) {
        mContext = context;
        mIsPush = false;
    }

    public void start(boolean isPush) {
        Log.i(TAG,"[Context] Starting, push status is " + (isPush ? "true" : "false"));
    }

    public void destroy() {
        Log.i(TAG, "[Context] Destroying");

        if (mLinphoneManager != null) {
            mLinphoneManager.destroy();
        }

        sInstance = null;
    }

    public void openIncall() {
        answered = false;

        dispatchOnUIThread(
                new Runnable() {
                    @Override
                    public void run() {
                        mLinphoneManager.ensureRegistered();

                        Intent intent = new Intent(mContext, LinphoneMiniActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                        intent.putExtra("address", "");
                        intent.putExtra("displayName", "");

                        mLinphoneManager.previewCall();

                        mContext.startActivity(intent);
                    }
                }
        );
    }

    public static boolean hasForeground() {
        return hasForeground;
    }

    public void runForegraundService() {
        if (!hasForeground) {
            Intent serviceIntent = new Intent(mContext, LinphoneForegroundService.class);
            serviceIntent.setAction(LinphoneForegroundService.ACTION_START_FOREGROUND_SERVICE);
            ContextCompat.startForegroundService(mContext, serviceIntent);

            hasForeground = true;
        }
    }

    public void stopForegraundService() {
        if (hasForeground) {
            Intent serviceIntent = new Intent(mContext, LinphoneForegroundService.class);
            serviceIntent.setAction(LinphoneForegroundService.ACTION_STOP_FOREGROUND_SERVICE);
            ContextCompat.startForegroundService(mContext, serviceIntent);

            hasForeground = false;
        }
    }

    public void showNotification() {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(NOTIFICATION_ID, isCall ? getCallNotification(mContext) : getServiceNotification(mContext, isConnected));
        android.util.Log.d(TAG, "StateChanged " + (isCall ? "getCallNotification" : "getServiceNotification"));

    }

    public static Notification getServiceNotification(Context context, boolean connected) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,0, notificationIntent, 0);

        Notification.Builder builder =  new Notification.Builder(context)
                .setContentTitle("Домофон")
                .setContentText(connected ? "подключен" : "не подключен")
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(iconColor);
        }

        if (Build.VERSION.SDK_INT >= 26){
            createNotificationChannel(context);
            builder.setChannelId(SERVICE_CHANNEL_ID);
        }

        builder.setPriority(Notification.PRIORITY_MIN);

        return builder.build();
    }

    public static Notification getCallNotification(Context context) {
        android.util.Log.d(TAG, "StateChanged getCallNotification");
        Intent resultIntent = new Intent(context.getApplicationContext(), LinphoneMiniActivity.class);
        resultIntent.putExtra("address", "");
        resultIntent.putExtra("displayName", "");
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        Notification.Builder builder = new Notification.Builder(context)
                .setContentTitle("Домофон")
                .setContentText("Входяший звонок домофона")
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(resultPendingIntent);

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(iconColor);
        }

        if (Build.VERSION.SDK_INT >= 26){
            createNotificationChannel(context);
            builder.setChannelId(CHANNEL_ID);
        }

        builder.setPriority(Notification.PRIORITY_MAX);

        return builder.build();
    }

    private static void createNotificationChannel(Context context) {
        if (!channelInited && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Evo Life Service Channel",
                    NotificationManager.IMPORTANCE_MIN
            );
            manager.createNotificationChannel(serviceChannel);

            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Evo Life Call Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(callChannel);
        }
    }

    public void killCurrentApp() {
        if (mIsPush) {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                List<ActivityManager.AppTask> appTasks = am.getAppTasks();
                if (appTasks.size() > 0) {
                    ActivityManager.AppTask appTask = appTasks.get(0);
                    appTask.finishAndRemoveTask();
                }
            }
        }
    }

    public void runWorker() {
        try {
            android.util.Log.i(TAG, "[Context Worker]: run");
            PeriodicWorkRequest myWorkRequest = new PeriodicWorkRequest.Builder(LinphoneWorker.class, 15, TimeUnit.MINUTES).addTag(TAG).build();
            WorkManager.getInstance(mContext).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, myWorkRequest);
        } catch (Exception e) {
            android.util.Log.e(TAG, "[Context Worker]: " + e.getMessage());
        }
    }

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!Settings.canDrawOverlays(mContext)) {
                dispatchOnUIThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Intent localIntent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION");
                                    localIntent.setData(Uri.parse("package:" + mContext.getPackageName()));
                                    localIntent.setFlags(268435456);
                                    mContext.startActivity(localIntent);
                                } catch (Exception e){
                                    android.util.Log.d(TAG, e.getMessage());
                                }
                            }
                        }
                );
            }
        }
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile (String filePath) throws Exception {
        File fl = new File(filePath);
        FileInputStream fin = new FileInputStream(fl);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }
}
