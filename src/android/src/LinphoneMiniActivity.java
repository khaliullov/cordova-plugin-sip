package com.sip.linphone;
/*
LinphoneMiniActivity.java
Copyright (C) 2014  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/


import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Core;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniActivity extends Activity {
    private SurfaceView mVideoView;
    private SurfaceView mCaptureView;
    private AndroidVideoWindowImpl androidVideoWindowImpl;
    private Button answerButton;
    private Button unlockButton;
    private Animation answerAnim;
    private Animation unlockAnim;
    private Timer unlockTimer;

    public static final int NOTIFICATION_ID = 45325623;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // getIntent() should always return the most recent
        setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        Resources R = getApplication().getResources();
        String packageName = getApplication().getPackageName();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.getIdentifier("incall", "layout", packageName));

        RelativeLayout bgElement = findViewById(R.getIdentifier("topLayout", "id", packageName));
        bgElement.setBackgroundColor(Color.WHITE);

        answerButton = findViewById(R.getIdentifier("answerButton", "id", packageName));

        mVideoView = findViewById(R.getIdentifier("videoSurface", "id", packageName));

        mCaptureView = findViewById(R.getIdentifier("videoCaptureSurface", "id", packageName));
        mCaptureView.setVisibility(View.INVISIBLE);
        mCaptureView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        fixZOrder(mVideoView, mCaptureView);

        androidVideoWindowImpl = new AndroidVideoWindowImpl(mVideoView, mCaptureView, new AndroidVideoWindowImpl.VideoWindowListener() {
            public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
                Log.d("onVideoRenderingSurfaceReady");
                Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if(c != null){
                        c.setNativeVideoWindowId(vw);
                    }
                }
                mVideoView = surface;
            }

            public void onVideoRenderingSurfaceDestroyed(AndroidVideoWindowImpl vw) {
                Log.d("onVideoRenderingSurfaceDestroyed");
                Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if(c != null){
                        c.setNativeVideoWindowId(null);
                    }
                }
            }

            public void onVideoPreviewSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
                Log.d("onVideoPreviewSurfaceReady");
                mCaptureView = surface;
                LinphoneContext.instance().mLinphoneManager.getLc().setNativePreviewWindowId(mCaptureView);

            }

            public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {
                Log.d("onVideoPreviewSurfaceDestroyed");
                // Remove references kept in jni code and restart camera
                LinphoneContext.instance().mLinphoneManager.getLc().setNativePreviewWindowId(null);
            }
        });

        answerAnim = AnimationUtils.loadAnimation(this, R.getIdentifier("alpha", "anim", packageName));
        answerAnim.setFillAfter(true);

        unlockAnim = AnimationUtils.loadAnimation(this, R.getIdentifier("alpha_reverse", "anim", packageName));

        unlockButton = (Button) findViewById(R.getIdentifier("unlockButton", "id", packageName));

        float alpha = 0.1f;
        AlphaAnimation alphaUp = new AlphaAnimation(alpha, alpha);
        alphaUp.setFillAfter(true);
        unlockButton.setEnabled(false);
        unlockButton.startAnimation(alphaUp);

/*
        unlockButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
                if (lc != null) {
                    Call call = lc.getCurrentCall();
                    if (call != null) {
                        int action = event.getAction();
                        switch (action) {
                            case MotionEvent.ACTION_DOWN:
                                v.startAnimation(unlockAnim);
                                call.sendDtmfs("12#");
                                android.util.Log.d("LinphoneSip", "sending Dtmfs");
                                break;
                            case MotionEvent.ACTION_UP:
                                call.cancelDtmfs();
                                android.util.Log.d("LinphoneSip", "cancel Dtmfs");
                                break;
                        }
                    }
                }

                return true;
            }
        });
*/

        Intent i = getIntent();
        Bundle extras = i.getExtras();
        String address = extras.getString("address");
        String displayName = extras.getString("displayName");

        String videoDeviceId = LinphoneContext.instance().mLinphoneManager.getLc().getVideoDevice();
        LinphoneContext.instance().mLinphoneManager.getLc().setVideoDevice(videoDeviceId);
        //if (address != "") {
            // Linphone.mLinphoneManager.newOutgoingCall(address, displayName);
        //}

        LinphoneMiniManager.getInstance().callActivity = this;
    }

    private void hideNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void fixZOrder(SurfaceView video, SurfaceView preview) {
        video.setZOrderOnTop(false);
        preview.setZOrderOnTop(true);
        preview.setZOrderMediaOverlay(true); // Needed to be able to display control layout over
    }

    public void butAnswer(View v) {
        Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
        if (lc != null) {
            Call call = lc.getCurrentCall();
            if (call != null) {
                v.startAnimation(answerAnim);

                CallParams params = call.getParams();
                params.enableVideo(true);
                lc.acceptCallWithParams(call, params);

                answerButton.setEnabled(false);

                float alpha = 1f;
                AlphaAnimation alphaUp = new AlphaAnimation(alpha, alpha);
                alphaUp.setFillAfter(true);
                unlockButton.setEnabled(false);
                unlockButton.startAnimation(alphaUp);

                unlockButton.setEnabled(true);

                LinphoneContext.answered = true;
            }
        }
    }

    public void rejectAnswer(View v) {
        onBackPressed();
    }

    public void butUnlock(View v) {
        if (unlockTimer != null) {
            return;
        }

        Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
        if (lc != null) {
            Call call = lc.getCurrentCall();
            if (call != null) {
                v.startAnimation(unlockAnim);
                call.sendDtmfs("12#");
                android.util.Log.d("LinphoneSip", "sending Dtmfs");

                TimerTask lTask = new TimerTask() {
                    @Override
                    public void run() {
                        call.cancelDtmfs();
                        unlockTimer.cancel();
                        unlockTimer = null;

                        android.util.Log.d("LinphoneSip", "stop Dtmfs");
                    }
                };

                unlockTimer = new Timer("Dtmfs scheduler");
                unlockTimer.schedule(lTask, 1600);
            }
        }
    }

    @Override
    protected void onResume() {
	    super.onResume();

        if (mVideoView != null) {
            ((GLSurfaceView) mVideoView).onResume();
        }

        if (androidVideoWindowImpl != null) {
            synchronized (androidVideoWindowImpl) {
                Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if (c != null) {
                        c.setNativeVideoWindowId(androidVideoWindowImpl);
                    }
                }
            }
        }
    }

    @Override
    protected void onPause() {
        if (androidVideoWindowImpl != null) {
            synchronized (androidVideoWindowImpl) {
		/*
		 * this call will destroy native opengl renderer which is used by
		 * androidVideoWindowImpl
		 */
                Core lc = LinphoneContext.instance().mLinphoneManager.getLc();
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if (c != null){
                        c.setNativeVideoWindowId(null);
                    }
                }
            }
        }

        if (mVideoView != null) {
            ((GLSurfaceView) mVideoView).onPause();
        }

	    super.onPause();
    }

    @Override
    public void onBackPressed() {
        Core lc = LinphoneContext.instance().mLinphoneManager.getLc();

        if (lc != null) {

            Call c = lc.getCurrentCall();

            if (c != null){
                if (LinphoneContext.answered) {
                    c.terminate();
                } else {
                    c.decline(Reason.NotAnswered);
                }
            }
        }

        super.onBackPressed();

        LinphoneContext.instance().killCurrentApp();
    }

    @Override
    protected void onDestroy() {
        hideNotification();

        mCaptureView = null;

        if (mVideoView != null) {
            mVideoView.setOnTouchListener(null);
            mVideoView = null;
        }

        if (androidVideoWindowImpl != null) {
            // Prevent linphone from crashing if correspondent hang up while you are rotating
            androidVideoWindowImpl.release();
            androidVideoWindowImpl = null;
        }

        LinphoneMiniManager.getInstance().callActivity = null;

	    super.onDestroy();

        LinphoneContext.instance().killCurrentApp();
    }
}
