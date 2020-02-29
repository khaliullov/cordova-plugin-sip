package com.sip.linphone;

import android.Manifest;
import android.content.Context;
import android.net.sip.SipAudioCall;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.linphone.core.Core;
import org.linphone.mediastream.Log;

import java.util.Timer;

public class Linphone extends CordovaPlugin  {
    public static Linphone mInstance;
    public static LinphoneMiniManager mLinphoneManager;
    public static Core mLinphoneCore;
    public static Context mContext;
    private static final int RC_MIC_PERM = 2;
    public static Timer mTimer;
    public CallbackContext mListenCallback;
    CordovaInterface cordova;

    public SipManager manager = null;
    public SipProfile me = null;
    public SipAudioCall call = null;

    public static Boolean answered = false;
    public static Boolean closeActivity = false;

    private static final String TAG = "LinphoneSip";
    public static final String APPID = "924566543835";

    public static LinphoneContext lContext;

    private static Boolean powerManager = true;


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        android.util.Log.i(TAG, "initialize cordova sip");

        this.cordova = cordova;
        mContext = cordova.getActivity().getApplicationContext();

        if (LinphoneContext.isReady()) {
            android.util.Log.i(TAG, "update context");
            lContext = LinphoneContext.instance();
            lContext.updateContext(mContext);
        } else {
            android.util.Log.i(TAG, "create new context");
            lContext = new LinphoneContext(mContext, false);
        }

        mLinphoneManager = lContext.mLinphoneManager;
        mLinphoneCore = mLinphoneManager.getLc();
        mInstance = this;

        mLinphoneCore.clearAllAuthInfo();
        mLinphoneCore.clearProxyConfig();
    }

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        switch (action) {
            case "login":
                android.util.Log.d("CORE","LOGIN IN");
                login(args.getString(0), args.getString(1), args.getString(2), callbackContext);
                return true;
            case "logout":
                logout(callbackContext);
                return true;
            case "sendLogcat":
                sendLogcat();
                return true;
            case "call":
                call(args.getString(0), args.getString(1), callbackContext);
                return true;
            case "listenCall":
                listenCall(callbackContext);
                return true;
            case "ensureRegistered":
                ensureRegistered(callbackContext);
                return true;
            case "setPushNotification":
                setPushNotification(args.getString(0), args.getString(1), callbackContext);
                return true;
            case "acceptCall":
                acceptCall(args.getString(0), callbackContext);
                return true;
            case "disableStunServer":
                disableStunServer(callbackContext);
                return true;
            case "setStunServer":
                setStunServer(args.getString(0), callbackContext);
                return true;
            case "videocall":
                videocall(args.getString(0), args.getString(1), callbackContext);
                return true;
            case "hangup":
                hangup(callbackContext);
                return true;
            case "toggleVideo":
                toggleVideo(callbackContext);
                return true;
            case "toggleSpeaker":
                toggleSpeaker(callbackContext);
                return true;
            case "toggleMute":
                toggleMute(callbackContext);
                return true;
            case "sendDtmf":
                sendDtmf(args.getString(0), callbackContext);
                return true;
        }
        return false;
    }

    public void login(final String username, final String password, final String domain, final CallbackContext callbackContext) {
        if (!cordova.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            cordova.requestPermission(this, RC_MIC_PERM, Manifest.permission.RECORD_AUDIO);
        }

        if (powerManager) {
            android.util.Log.d(TAG, "SHOW DIAOL");
            LinphoneDeviceUtils.displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(cordova.getActivity());
            powerManager = false;
        }

        LinphoneContext.instance().checkPermission();

        cordova.getThreadPool().execute(() -> {
            mLinphoneManager.listenLogin(callbackContext);
            mLinphoneManager.clearRegistration();
            mLinphoneManager.login(username, password, domain);
            mLinphoneManager.saveAuth(username, password, domain);

            LinphoneContext.instance().runForegraundService();
        });
    }

    public void setPushNotification(final String appId, final String regId, final CallbackContext callbackContext) {
        mLinphoneManager.setPushNotification(appId, regId);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {

    }

    public void sendLogcat() {
        try {
            LinphoneContext.instance().sendLogcatMail(cordova.getActivity());
        } catch (Exception e){
            Log.d("call error", e.getMessage());
        }
    }

    public static synchronized void logout(final CallbackContext callbackContext) {
        try{
            Log.d("logout");
            mLinphoneManager.logout();
            mLinphoneManager.saveAuth("", "", "");
            mLinphoneManager.saveStunServer("");
            LinphoneContext.instance().stopForegraundService();
            callbackContext.success();
        }catch (Exception e){
            Log.d("Logout error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public static synchronized void call(final String address, final String displayName, final CallbackContext callbackContext) {
        try {
            mLinphoneManager.listenCall(callbackContext);
            mLinphoneManager.call(address, displayName);
        } catch (Exception e){
            Log.d("call error", e.getMessage());
        }
    }

    public static synchronized void hangup(final CallbackContext callbackContext) {
        try{
            mLinphoneManager.listenCall(callbackContext);
            mLinphoneManager.hangup();
        }catch (Exception e){
            Log.d("hangup error", e.getMessage());
        }
    }

    public static synchronized void listenCall(final CallbackContext callbackContext) {
        mLinphoneManager.listenCall(callbackContext);
    }

    public static synchronized void ensureRegistered(final CallbackContext callbackContext) {
        mLinphoneManager.listenCall(callbackContext);
        mLinphoneManager.ensureRegistered();
    }

    public void setStunServer(final String stunServer, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            mLinphoneManager.listenCall(callbackContext);
            mLinphoneManager.setStunServer(stunServer);
            mLinphoneManager.saveStunServer(stunServer);
        });
    }

    public static synchronized void disableStunServer(final CallbackContext callbackContext) {

        mLinphoneManager.listenCall(callbackContext);
        mLinphoneManager.disableStunServer();
        mLinphoneManager.saveStunServer("");
    }

    public static synchronized void acceptCall( final String isAcceptCall, final CallbackContext callbackContext) {
        mLinphoneManager.listenCall(callbackContext);

        if("true".equals(isAcceptCall)) {
            mLinphoneManager.previewCall();

            callbackContext.success();
        } else
            mLinphoneManager.terminateCall();
    }

    public static synchronized void videocall(final String address, final String displayName, final CallbackContext callbackContext) {
        try{
            Log.d("incall", address, displayName);
            callbackContext.success();
        }catch (Exception e){
            Log.d("incall error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public static synchronized void toggleVideo(final CallbackContext callbackContext) {
        try{
            boolean isenabled = mLinphoneManager.toggleEnableCamera();
            callbackContext.success(isenabled ? 1 : 0);
        }catch (Exception e){
            Log.d("toggleVideo error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public static synchronized void toggleSpeaker(final CallbackContext callbackContext) {
        try{
            Log.d("toggleSpeaker");
            boolean isenabled = mLinphoneManager.toggleEnableSpeaker();
            Log.d("toggleSpeaker sukses",isenabled);
            callbackContext.success(isenabled ? 1 : 0);
        }catch (Exception e){
            Log.d("toggleSpeaker error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public static synchronized void toggleMute(final CallbackContext callbackContext) {
        try{
            Log.d("toggleMute");
            boolean isenabled = mLinphoneManager.toggleMute();
            Log.d("toggleMute sukses",isenabled);
            callbackContext.success(isenabled ? 1 : 0);
        }catch (Exception e){
            Log.d("toggleMute error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    public static synchronized void sendDtmf(final String number, final CallbackContext callbackContext) {
        try{
            Log.d("sendDtmf");
            mLinphoneManager.sendDtmf(number.charAt(0));
            Log.d("sendDtmf sukses",number);
            callbackContext.success();
        } catch (Exception e){
            Log.d("sendDtmf error", e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }
}