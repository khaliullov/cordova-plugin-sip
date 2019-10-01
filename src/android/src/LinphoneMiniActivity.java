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
import android.content.Intent;
import android.content.res.Resources;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.graphics.Color;
import android.view.View;
import android.content.pm.ActivityInfo;

import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CallParams;

import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.AndroidVideoWindowImpl;

/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniActivity extends Activity {
    private SurfaceView mVideoView;
    private SurfaceView mCaptureView;
    private AndroidVideoWindowImpl androidVideoWindowImpl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

        Resources R = getApplication().getResources();
        String packageName = getApplication().getPackageName();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.getIdentifier("incall", "layout", packageName));

        RelativeLayout bgElement = findViewById(R.getIdentifier("topLayout", "id", packageName));
        bgElement.setBackgroundColor(Color.WHITE);

        mVideoView = findViewById(R.getIdentifier("videoSurface", "id", packageName));

        mCaptureView = findViewById(R.getIdentifier("videoCaptureSurface", "id", packageName));
        mCaptureView.setVisibility(View.INVISIBLE);
        mCaptureView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        fixZOrder(mVideoView, mCaptureView);

        androidVideoWindowImpl = new AndroidVideoWindowImpl(mVideoView, mCaptureView, new AndroidVideoWindowImpl.VideoWindowListener() {
            public void onVideoRenderingSurfaceReady(AndroidVideoWindowImpl vw, SurfaceView surface) {
                Log.d("onVideoRenderingSurfaceReady");
                Core lc = Linphone.mLinphoneCore;
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
                Core lc = Linphone.mLinphoneCore;
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
                Linphone.mLinphoneCore.setNativePreviewWindowId(mCaptureView);

            }

            public void onVideoPreviewSurfaceDestroyed(AndroidVideoWindowImpl vw) {
                Log.d("onVideoPreviewSurfaceDestroyed");
                // Remove references kept in jni code and restart camera
                Linphone.mLinphoneCore.setNativePreviewWindowId(null);
            }
        });

        Intent i = getIntent();
        Bundle extras = i.getExtras();
        String address = extras.getString("address");
        String displayName = extras.getString("displayName");

        String videoDeviceId = Linphone.mLinphoneCore.getVideoDevice();
        Linphone.mLinphoneCore.setVideoDevice(videoDeviceId);
        //if (address != "") {
            // Linphone.mLinphoneManager.newOutgoingCall(address, displayName);
        //}

        LinphoneMiniManager.getInstance().callActivity = this;

    }

    private void fixZOrder(SurfaceView video, SurfaceView preview) {
        video.setZOrderOnTop(false);
        preview.setZOrderOnTop(true);
        preview.setZOrderMediaOverlay(true); // Needed to be able to display control layout over
    }

//    public void switchCamera() {
//        try {
//            String videoDeviceId = Linphone.mLinphoneCore.getVideoDevice();
//            videoDeviceId = (videoDeviceId + 1) % AndroidCameraConfiguration.retrieveCameras().length;
//            Linphone.mLinphoneCore.setVideoDevice(videoDeviceId);
//            Linphone.mLinphoneManager.updateCall();
//
//            // previous call will cause graph reconstruction -> regive preview
//            // window
//            if (mCaptureView != null) {
//                Linphone.mLinphoneCore.setPreviewWindow(mCaptureView);
//            }
//        } catch (ArithmeticException ae) {
//            Log.e("Cannot switch camera : no camera");
//        }
//    }

    public void butAnswer(View v) {
        Core lc = Linphone.mLinphoneCore;
        if (lc != null) {
            Call call = lc.getCurrentCall();
            if (call != null) {
                CallParams params = call.getParams();
                params.enableVideo(true);
                lc.acceptCallWithParams(call, params);
            }
        }
    }

    public void rejectAnswer(View v) {
        onBackPressed();
    }

    @Override
    protected void onResume() {
	    super.onResume();

        if (mVideoView != null) {
            ((GLSurfaceView) mVideoView).onResume();
        }

        if (androidVideoWindowImpl != null) {
            synchronized (androidVideoWindowImpl) {
                Core lc = Linphone.mLinphoneCore;
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if(c != null){
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
                Core lc = Linphone.mLinphoneCore;
                if (lc != null) {
                    Call c = lc.getCurrentCall();
                    if(c != null){
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
        Core lc = Linphone.mLinphoneCore;

        if (lc != null) {

            Call c = lc.getCurrentCall();

            if (c != null){
                c.terminate();
            }
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
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
    }
}
