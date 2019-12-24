package com.sip.linphone;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

public class LinphoneContext {
    private static final String TAG = "LinphoneSip";

    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static LinphoneContext sInstance = null;

    public static boolean answered = false;

    private Context mContext;

    private boolean mIsPush = false;

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
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra("address", "");
                        intent.putExtra("displayName", "");

                        mLinphoneManager.previewCall();

                        showNotification();

                        mContext.startActivity(intent);
                    }
                }
        );
    }

    public void showNotification() {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        String CHANNEL_ID = "cordova-plugin-linphone-sip";

        Intent resultIntent = new Intent(mContext.getApplicationContext(), LinphoneMiniActivity.class);
        resultIntent.putExtra("address", "");
        resultIntent.putExtra("displayName", "");
        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(mContext, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        Resources res  = mContext.getResources();
        String pkgName = mContext.getPackageName();

        Notification.Builder builder = new Notification.Builder(mContext)
                .setContentTitle("Входяший звонок домофона")
                .setSmallIcon(res.getIdentifier("icon", "drawable", pkgName))
                .setContentIntent(resultPendingIntent);

        int color = 0xFF4A47EC;

        if (Build.VERSION.SDK_INT >= 21) {
            builder.setColor(color);
        }

        if (Build.VERSION.SDK_INT >= 26){
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,"EVO Life sip", NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(CHANNEL_ID);
        }

        builder.setPriority(Notification.PRIORITY_MAX);

        Notification notification = builder.build();

        notificationManager.notify(LinphoneMiniActivity.NOTIFICATION_ID, notification);
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
}
