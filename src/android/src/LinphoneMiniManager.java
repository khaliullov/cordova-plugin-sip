package com.sip.linphone;
/*
LinphoneMiniManager.java
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.AuthMethod;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.EcCalibratorStatus;
import org.linphone.core.Event;
import org.linphone.core.Factory;
import org.linphone.core.Friend;
import org.linphone.core.FriendList;
import org.linphone.core.GlobalState;
import org.linphone.core.InfoMessage;
import org.linphone.core.NatPolicy;
import org.linphone.core.PresenceModel;
import org.linphone.core.ProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.SubscriptionState;
import org.linphone.core.Transports;
import org.linphone.core.VersionUpdateCheckResult;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Sylvain Berfini
 */
public class LinphoneMiniManager implements CoreListener {
    private static final String TAG = "LinphoneSip";
    private static final int RANDOM_PORT = -1;
    private static final int SIP_PORT = RANDOM_PORT;

    public static Boolean flToken = false;

    public static LinphoneMiniManager mInstance;
    public static Context mContext;
    public static Core mCore;
    public static LinphonePreferences mPrefs;
    public static Timer mTimer;
    public CallbackContext mCallbackContext;
    public CallbackContext mLoginCallbackContext;
    private AudioManager mAudioManager;
    public Activity callActivity;
    private LinphoneStorage mStorage;
    private final Vibrator mVibrator;

    public void onMessageSent(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

    }

    public void onChatRoomRead(Core core, ChatRoom chatRoom) {

    }

    public LinphoneMiniManager(Context c, boolean isPush) {
        mContext = c;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        Factory.instance().setDebugMode(true, TAG /*"Linphone Mini"*/);

        android.util.Log.d(TAG, "Start initializing Linphone");

        mPrefs = LinphonePreferences.instance();

        mStorage = new LinphoneStorage(mContext);
        mStorage.setStatus("");

        mAudioManager = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));

        try {
            String basePath = mContext.getFilesDir().getAbsolutePath();

            if (!isPush) {
                copyAssetsFromPackage(basePath);
            }

            mCore = Factory.instance().createCore(mPrefs.getLinphoneDefaultConfig(), mPrefs.getLinphoneFactoryConfig(), mContext);

            mCore.addListener(this);

            mCore.enableIpv6(false);

            if (isPush) {
                android.util.Log.w(TAG,
                        "[Manager] We are here because of a received push notification, enter background mode before starting the Core");
                mCore.enterBackground();
            }

            mCore.start();

            initCoreValues(basePath);

            setUserAgent();

            mCore.setNetworkReachable(true); // Let's assume it's true

            startIterate();

            mInstance = this;

            if (!isPush) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

                    connectivityManager.registerNetworkCallback(
                            new NetworkRequest.Builder()
                                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                    .build(),
                            new ConnectivityManager.NetworkCallback() {
                                @Override
                                public void onLost(Network network) {
                                    if (mCore != null && LinphoneContext.hasForeground()) {
                                        android.util.Log.d(TAG, "connection lost");
                                        mCore.refreshRegisters();
                                    }
                                }

                                @Override
                                public void onAvailable(Network network) {
                                    if (mCore != null && LinphoneContext.hasForeground()) {
                                        android.util.Log.d(TAG, "connection check - refreshRegisters");
                                        mCore.refreshRegisters();
                                    }
                                }
                            }
                    );
                }

                Log.i(
                        "[Push Notification] firebase push sender id ");
                try {
                    FirebaseInstanceId.getInstance()
                            .getInstanceId()
                            .addOnCompleteListener(
                                    new OnCompleteListener<InstanceIdResult>() {
                                        @Override
                                        public void onComplete(@NonNull Task<InstanceIdResult> task) {
                                            if (!task.isSuccessful()) {
                                                android.util.Log.e(TAG,
                                                        "[Push Notification] firebase getInstanceId failed: "
                                                                + task.getException());
                                                return;
                                            }
                                            String token = task.getResult().getToken();
                                            android.util.Log.i(TAG, "[Push Notification] firebase token is: " + token);
                                            LinphonePreferences.instance()
                                                    .setPushNotificationRegistrationID(token);
                                        }
                                    });
                } catch (Exception e) {
                    android.util.Log.e(TAG, "[Push Notification] firebase not available.");
                }
            }
        } catch (IOException e) {
            android.util.Log.d(TAG, "Error initializing Linphone");
            Log.e(new Object[]{"Error initializing Linphone", e.getMessage()});
        }

    }

    public Core getLc() {
        return mCore;
    }

    public static LinphoneMiniManager getInstance() {
        return mInstance;
    }

    public void destroy() {
        try {
            mTimer.cancel();
            mCore.stopRinging();
            mCore.stopConferenceRecording();
            mCore.stopDtmf();
            mCore.stopDtmfStream();
            mCore.stopEchoTester();
        } catch (RuntimeException e) {

        } finally {
            destroyCore();

            mCore = null;
            mInstance = null;
        }
    }

    private void destroyCore() {
        Log.w("[Manager] Destroying Core");

        if (LinphonePreferences.instance() != null) {
            mCore.setNetworkReachable(false);
        }

        mCore.stop();
        mCore.removeListener(this);
    }

    private void startIterate() {
        TimerTask lTask = new TimerTask() {
            @Override
            public void run() {
                mCore.iterate();
            }
        };

        mTimer = new Timer("LinphoneMini scheduler");
        mTimer.schedule(lTask, 0, 20);
    }

    private void setUserAgent() {
        try {
            String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
            }
            mCore.setUserAgent("LinphoneMiniAndroid", versionName);
        } catch (NameNotFoundException e) {
        }
    }

    private void setFrontCamAsDefault() {
        int camId = 0;
        AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing)
                camId = androidCamera.id;
        }
        mCore.setVideoDevice("" + camId);
    }

    private void copyAssetsFromPackage(String basePath) throws IOException {
        String package_name = mContext.getPackageName();
        Resources resources = mContext.getResources();

        LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("oldphone_mono", "raw", package_name), basePath + "/oldphone_mono.wav", true);
        LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("ringback", "raw", package_name), basePath + "/ringback.wav", true);
        LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("linphonerc_default", "raw", package_name), basePath + "/.linphonerc");
        LinphoneMiniUtils.copyFromPackage(mContext, resources.getIdentifier("linphonerc_factory", "raw", package_name), new File(basePath + "/linphonerc").getName());
        LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("lpconfig", "raw", package_name), basePath + "/lpconfig.xsd");
        LinphoneMiniUtils.copyIfNotExist(mContext, resources.getIdentifier("rootca", "raw", package_name), basePath + "/rootca.pem");
    }

    private void initCoreValues(String basePath) {
        mCore.setRootCa(basePath + "/rootca.pem");
        mCore.setPlayFile(basePath + "/toy_mono.wav");
        mCore.setCallLogsDatabasePath(basePath + "/linphone-history.db");
        mCore.setRing(basePath + "/oldphone_mono.wav");
        mCore.setRingDuringIncomingEarlyMedia(true);

        mCore.enableEchoCancellation(true);
        mCore.enableEchoLimiter(true);
    }

    public void newOutgoingCall(String to, String displayName) {
        Address lAddress;
        lAddress = mCore.interpretUrl(to);

        ProxyConfig lpc = mCore.getDefaultProxyConfig();

        if (lpc != null && lAddress.asStringUriOnly().equals(lpc.getDomain())) {
            return;
        }

        lAddress.setDisplayName(displayName);

        if (mCore.isNetworkReachable()) {
            CallParams params = mCore.createCallParams(mCore.getCurrentCall());
            params.enableVideo(true);
            mCore.inviteAddressWithParams(lAddress, params);
        } else {
            Log.e(new Object[]{"Error: Network unreachable"});
        }
    }

    public void setPushNotification(String appId, String regId) {
        Core core = getLc();
        if (core == null) {
            return;
        }

        android.util.Log.d(TAG, "[SET Push Notification] " + appId + " " + regId);

        if (regId != "") {
            // Add push infos to exisiting proxy configs
            if (core.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : core.getProxyConfigList()) {
                    if (lpc == null) continue;
                    if (!lpc.isPushNotificationAllowed()) {
                        lpc.edit();
                        lpc.setContactUriParameters(null);
                        lpc.setExpires(3600 * 72);
                        lpc.done();
                        if (lpc.getIdentityAddress() != null)
                            android.util.Log.d(TAG,
                                    "[SET Push Notification] infos removed from proxy config "
                                            + lpc.getIdentityAddress().asStringUriOnly());
                    } else {
                        String contactInfos =
                                "app-id="
                                        + appId
                                        + ";pn-type=firebase"
                                        + ";pn-timeout=0"
                                        + ";pn-tok="
                                        + regId
                                        + ";pn-silent=1";

                        android.util.Log.d(TAG, contactInfos);
                        String prevContactParams = lpc.getContactParameters();
                        if (prevContactParams == null
                                || prevContactParams.compareTo(contactInfos) != 0) {
                            lpc.edit();
                            lpc.setContactUriParameters(contactInfos);
                            lpc.setExpires(3600 * 72);
                            lpc.done();
                            if (lpc.getIdentityAddress() != null)
                                android.util.Log.d(TAG,
                                        "[Push Notification] infos added to proxy config "
                                                + lpc.getIdentityAddress().asStringUriOnly());
                        }
                    }
                }
                android.util.Log.d(TAG,
                        "[SET Push Notification] Refreshing registers to ensure token is up to date: "
                                + regId);
                core.refreshRegisters();
            } else {
                android.util.Log.d(TAG, "[Push Notification] enable token flag");
                flToken = true;
                loginFromStorage();
            }
        } else {
            if (core.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : core.getProxyConfigList()) {
                    lpc.edit();
                    lpc.setContactUriParameters(null);
                    lpc.done();
                    if (lpc.getIdentityAddress() != null)
                        android.util.Log.d(TAG,
                                "[SET Push Notification] infos removed from proxy config "
                                        + lpc.getIdentityAddress().asStringUriOnly());
                }
                core.refreshRegisters();
            }
        }
    }

    public void terminateCall() {
        if (mCore.inCall()) {
            Call c = mCore.getCurrentCall();
            if (c != null) c.terminate();
        }
    }

    public boolean toggleEnableCamera() {
        if (mCore.inCall()) {
            boolean enabled = !mCore.getCurrentCall().cameraEnabled();
            enableCamera(mCore.getCurrentCall(), enabled);
            return enabled;
        }
        return false;
    }

    public boolean toggleEnableSpeaker() {
//        if (mCore.inCall()) {
//			boolean enabled = !mCore.;
//			mCore.enableSpeaker(enabled);
//            return enabled;
//        }
        return false;
    }

    public boolean toggleMute() {
//        if (mCore.inCall()) {
//			boolean enabled = !mCore.();
//			mCore.muteMic(enabled);
//			return enabled;
//        }
        return false;
    }

    public void enableCamera(Call call, boolean enable) {
        if (call != null) {
            call.enableCamera(enable);
        }
    }

    public void sendDtmf(char number) {
        mCore.getCurrentCall().sendDtmf(number);
    }

    public void updateCall() {
        Core lc = mCore;
        Call lCall = lc.getCurrentCall();
        if (lCall == null) {
            Log.e(new Object[]{"Trying to updateCall while not in call: doing nothing"});
        } else {
            CallParams params = lCall.getParams();
            lc.getCurrentCall().setParams(params);
        }
    }

    public void listenCall(CallbackContext callbackContext) {
        mCallbackContext = callbackContext;
    }

    public void listenLogin(CallbackContext callbackContext) {
        mLoginCallbackContext = callbackContext;
    }

    public void ensureRegistered() {
        Core lc = mCore;
        lc.ensureRegistered();
    }

    public void setStunServer(String stunServer) {
        boolean turn = stunServer.substring(0, 5).equals("turn:") ? true : false;

        ProxyConfig mProxyConfig = mCore.getDefaultProxyConfig();
        if (mProxyConfig == null) {
            return;
        }
        mProxyConfig.edit();
        NatPolicy natPolicy = mProxyConfig.getNatPolicy();
        if (natPolicy == null) {
            natPolicy = mCore.createNatPolicy();
            mProxyConfig.setNatPolicy(natPolicy);
        }
        if (natPolicy != null) {
            if (turn) {
                natPolicy.setStunServer(stunServer.substring(5));
                natPolicy.enableTurn(true);
            } else {
                natPolicy.setStunServer(stunServer);
                natPolicy.enableStun(true);
            }
        }
        mProxyConfig.done();
    }

    public void disableStunServer() {
        ProxyConfig mProxyConfig = mCore.getDefaultProxyConfig();
        mProxyConfig.edit();
        NatPolicy natPolicy = mProxyConfig.getNatPolicy();
        if (natPolicy == null) {
            natPolicy = mCore.createNatPolicy();
            mProxyConfig.setNatPolicy(natPolicy);
        }
        if (natPolicy != null) {
            natPolicy.enableStun(false);
        }
        mProxyConfig.done();
    }

    public void acceptCall() {
        Call call = mCore.getCurrentCall();
        if (call != null) {
            CallParams params = call.getParams();
            params.enableVideo(true);
            params.enableAudio(true);
            mCore.acceptCallWithParams(call, params);
        }
    }

    public void previewCall() {
        Call call = mCore.getCurrentCall();
        if (call != null) {
            String agent = call.getRemoteUserAgent();

            android.util.Log.e(TAG, "agent: " + agent);

            if (agent.matches("(.*)Rubetek(.*)")) {
                android.util.Log.e(TAG, "agent: RUBETEK CALL");
                CallParams params = call.getParams();
                params.enableVideo(true);
                params.enableAudio(false);
                call.acceptEarlyMediaWithParams(params);
            } else {
                CallParams params = call.getParams();
                params.enableVideo(true);
                call.acceptEarlyMediaWithParams(params);
                params.enableAudio(false);
                call.setParams(params);
            }
        }
    }

    public void call(String address, String displayName) {
        newOutgoingCall(address, displayName);
    }

    public void hangup() {
        terminateCall();
    }

    public boolean loginFromStorage() {
        String username = mStorage.getUsername();

        android.util.Log.i(TAG, "[Mini manager] login from storage " + username);

        if (username != "") {
            android.util.Log.i(TAG, "[Mini manager] logining from storage");

            login(username, mStorage.getPassword(), mStorage.getDomain());

            String stun = mStorage.getStun();

            if (stun != "") {
                setStunServer(stun);
            }

            return true;
        }

        return false;
    }

    public void saveAuth(String username, String password, String domain) {
        mStorage.setUsername(username);
        mStorage.setPassword(password);
        mStorage.setDomain(domain);
    }

    public void saveStunServer(String stun) {
        mStorage.setStun(stun);
    }

    public void clearRegistration() {
        mCore.clearAllAuthInfo();
        mCore.clearProxyConfig();
    }

    public void login(String username, String password, String domain) {
        clearRegistration();

        Factory lcFactory = Factory.instance();

        Transports transports = mCore.getTransports();
        transports.setUdpPort(SIP_PORT);
        transports.setTcpPort(SIP_PORT);
        transports.setTlsPort(RANDOM_PORT);
        mCore.setTransports(transports);

        android.util.Log.d(TAG, "auth full: " + username + " " + password + " " + domain);

        Address address = lcFactory.createAddress("sip:" + username);

        Address proxyAddress = lcFactory.createAddress("sip:" + domain);

        Integer port = proxyAddress.getPort();

        android.util.Log.d(TAG, "proxyAddress: " + proxyAddress.asStringUriOnly());

        if (password != null) {
            mCore.addAuthInfo(lcFactory.createAuthInfo(address.getUsername(), address.getUsername(), password, (String) null, (String) null, address.getDomain()));
        }

        ProxyConfig proxyCfg = mCore.createProxyConfig();
        proxyCfg.edit();
        proxyCfg.setIdentityAddress(address);
        proxyCfg.setServerAddr(proxyAddress.asStringUriOnly());

        if (port != 0) {
            proxyCfg.setRoute(proxyAddress.getDomain() + ':' + port);
        }

        android.util.Log.d(TAG, "auth: " + address.getUsername() + " " + password + " " + address.getDomain() + " - " + proxyAddress.asStringUriOnly());

        proxyCfg.enableRegister(true);
        proxyCfg.setExpires(86400 * 30);
        proxyCfg.done();

        mCore.addProxyConfig(proxyCfg);
        mCore.setDefaultProxyConfig(proxyCfg);

        flToken = true;

        android.util.Log.d(TAG, "logined");
    }

    private NatPolicy getOrCreateNatPolicy() {
        NatPolicy nat = mCore.getNatPolicy();

        if (nat == null) {
            nat = getLc().createNatPolicy();
        }

        return nat;
    }

    public void logout() {
        ProxyConfig[] prxCfgs = mCore.getProxyConfigList();
        if (prxCfgs.length > 0) {
            final ProxyConfig proxyCfg = prxCfgs[0];
            mCore.removeProxyConfig(proxyCfg);
            android.util.Log.d(TAG, "logouted");
        }
    }

    @Override
    public void onGlobalStateChanged(Core core, GlobalState globalState, String s) {
        android.util.Log.d(TAG, "Global state changed");
        android.util.Log.d(TAG, globalState.name());
        android.util.Log.d(TAG, s);
    }

    @Override
    public void onRegistrationStateChanged(Core core, ProxyConfig proxyConfig, RegistrationState registrationState, String s) {
        if (registrationState == RegistrationState.Ok) {
            android.util.Log.d(TAG, "RegistrationSuccess");

            if (flToken) {
                mPrefs.setPushNotificationEnabled(true);
                flToken = false;
            }
        } else if (registrationState == RegistrationState.Failed) {
            android.util.Log.d(TAG, "RegistrationFailed:: " + s);
        } else if (registrationState == RegistrationState.Cleared) {
            android.util.Log.d(TAG, "RegistrationCleared:: " + s);
        } else if (registrationState == RegistrationState.None) {
            android.util.Log.d(TAG, "RegistrationNone:: " + s);
        }

        if (mLoginCallbackContext != null) {
            if (registrationState == RegistrationState.Ok) {
                mLoginCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "RegistrationSuccess"));
            } else if (registrationState == RegistrationState.Failed) {
                mLoginCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "RegistrationFailed:: " + s));
            } else if (registrationState == RegistrationState.Cleared) {
                android.util.Log.d(TAG, "RegistrationCleared:: " + s);
            } else if (registrationState == RegistrationState.None) {
                android.util.Log.d(TAG, "RegistrationNone:: " + s);
            }
        }
    }

    private HashMap<String, String> prepareExtras(Call call) {
        HashMap<String, String> extras = new HashMap<String, String>();
        extras.put("id", "");
        extras.put("address", "");
        extras.put("displayName", "");
        extras.put("unlockUrl", "");
        if (call == null || call.getRemoteAddress() == null) {
            return extras;
        }
        String callerId = call.getRemoteAddress().getUsername();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String PREFS_NAME = preferences.getString("NativeStorageSharedPreferencesName", "NativeStorage");
        SharedPreferences settings = mContext.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String jsonContacts = settings.getString("contacts", "[]");
        try {
            JSONArray contacts = new JSONArray(jsonContacts);
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject contact = (JSONObject) contacts.getJSONObject(i);
                if (contact.has("sip_name") && contact.has("door_open_url")) {
                    if (contact.getString("sip_name").equals(callerId)) {
                        extras.put("unlockUrl", contact.getString("door_open_url"));
                        if (contact.has("address")) {
                            extras.put("address", contact.getString("address"));
                        } else {
                            extras.put("address", call.getRemoteAddress().asStringUriOnly());
                        }
                        if (contact.has("id")) {
                            extras.put("id", contact.getString("id"));
                        }
                        if (contact.has("entrance")) {
                            extras.put("displayName", "Подъезд №" + contact.getString("entrance"));
                        } else {
                            extras.put("displayName", callerId);
                        }
                    }
                }
            }
        } catch (JSONException e) {
        }
        return extras;
    }

    @Override
    public void onCallStateChanged(Core core, Call call, State state, String s) {
        android.util.Log.d(TAG, "-------------- onCallStateChanged -------------");

        if (state == State.Connected) {
            android.util.Log.d(TAG, "StateChanged Connected");
            mVibrator.cancel();
        } else if (state == State.IncomingReceived) {
            HashMap<String, String> extras = this.prepareExtras(call);

            android.util.Log.w(TAG, "extra id " + extras.get("id"));

            String notDisturb = mStorage.getNotdisturb(extras.get("id"));

            android.util.Log.w(TAG, "notDisturb " + notDisturb + " " + (notDisturb != "1"));

            if (notDisturb.equals("1")) {
                call.decline(Reason.NotAnswered);
                android.util.Log.w(TAG, "notDisturb ON");
            } else {
                android.util.Log.w(TAG, "notDisturb OFF");

                LinphoneContext.instance().openIncall(extras);
                android.util.Log.d(TAG, "StateChanged Incoming");

                if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    long[] patern = {0, 1000, 1000};
                    mVibrator.vibrate(patern, 1);
                }

                LinphoneContext.isCall = true;
                LinphoneContext.instance().showNotification();
            }
        } else if (state == State.End) {
            mVibrator.cancel();

            if (callActivity != null) {
                callActivity.finish();
            }

            android.util.Log.d(TAG, "StateChanged End");

            LinphoneContext.isCall = false;
            LinphoneContext.instance().showNotification();
        } else if (state == State.Error) {
            mVibrator.cancel();

            if (callActivity != null) {
                callActivity.finish();
            }


            LinphoneContext.isCall = false;
            LinphoneContext.instance().showNotification();

            android.util.Log.d(TAG, "StateChanged Error");
        }

        if (mCallbackContext != null) {
            if (state == State.Connected) {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "Connected"));
            } else if (state == State.IncomingReceived) {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "Incoming"));
            } else if (state == State.End) {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "End"));
            } else if (state == State.Error) {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "Error"));

            }
        }

        android.util.Log.d(TAG, "Call state: " + state + "(" + s + ")");

    }

    @Override
    public void onNotifyPresenceReceived(Core core, Friend friend) {

    }

    @Override
    public void onNotifyPresenceReceivedForUriOrTel(Core core, Friend friend, String s, PresenceModel presenceModel) {

    }

    @Override
    public void onNewSubscriptionRequested(Core core, Friend friend, String s) {

    }

    @Override
    public void onAuthenticationRequested(Core core, AuthInfo authInfo, AuthMethod authMethod) {
        android.util.Log.d(TAG, "Authentication requested");
    }

    @Override
    public void onCallLogUpdated(Core core, CallLog callLog) {
        android.util.Log.d(TAG, "Call log updated"+callLog.toStr());
    }

    @Override
    public void onMessageReceived(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

    }

    @Override
    public void onMessageReceivedUnableDecrypt(Core core, ChatRoom chatRoom, ChatMessage chatMessage) {

    }

    @Override
    public void onIsComposingReceived(Core core, ChatRoom chatRoom) {

    }

    @Override
    public void onDtmfReceived(Core core, Call call, int i) {
        android.util.Log.d(TAG, "DTMF RECEIVED");
    }

    @Override
    public void onReferReceived(Core core, String s) {

    }

    @Override
    public void onCallEncryptionChanged(Core core, Call call, boolean b, String s) {

    }

    @Override
    public void onTransferStateChanged(Core core, Call call, State state) {

    }

    @Override
    public void onBuddyInfoUpdated(Core core, Friend friend) {

    }

    @Override
    public void onCallStatsUpdated(Core core, Call call, CallStats callStats) {
        android.util.Log.d(TAG, "Call stats updated:: Download bandwidth :: "+callStats.getDownloadBandwidth());
    }

    @Override
    public void onInfoReceived(Core core, Call call, InfoMessage infoMessage) {
        android.util.Log.d(TAG, "Info message received :: "+infoMessage.getContent().getStringBuffer());
    }

    @Override
    public void onSubscriptionStateChanged(Core core, Event event, SubscriptionState subscriptionState) {
        android.util.Log.d(TAG, "Subscription state changed :: "+subscriptionState.name());
    }

    @Override
    public void onNotifyReceived(Core core, Event event, String s, Content content) {

    }

    @Override
    public void onSubscribeReceived(Core core, Event event, String s, Content content) {

    }

    @Override
    public void onPublishStateChanged(Core core, Event event, PublishState publishState) {

    }

    @Override
    public void onConfiguringStatus(Core core, ConfiguringState configuringState, String s) {
        if (configuringState == ConfiguringState.Successful) {
            android.util.Log.i(TAG,"[Context] Configuring state is Successful");
        } else if (configuringState == ConfiguringState.Failed) {
            android.util.Log.i(TAG,"[Context] Configuring state is Failed");
        } else if (configuringState == ConfiguringState.Skipped) {
            android.util.Log.i(TAG,"[Context] Configuring state is Skipped");
        }
    }

    @Override
    public void onNetworkReachable(Core core, boolean b) {
        android.util.Log.d(TAG, "Is network reachable?? " + b);
    }

    @Override
    public void onLogCollectionUploadStateChanged(Core core, Core.LogCollectionUploadState logCollectionUploadState, String s) {

    }

    @Override
    public void onLogCollectionUploadProgressIndication(Core core, int i, int i1) {

    }

    @Override
    public void onFriendListCreated(Core core, FriendList friendList) {

    }

    @Override
    public void onFriendListRemoved(Core core, FriendList friendList) {

    }

    @Override
    public void onCallCreated(Core core, Call call) {

    }

    @Override
    public void onVersionUpdateCheckResultReceived(Core core, VersionUpdateCheckResult versionUpdateCheckResult, String s, String s1) {

    }

    @Override
    public void onChatRoomStateChanged(Core core, ChatRoom chatRoom, ChatRoom.State state) {

    }

    @Override
    public void onQrcodeFound(Core core, String s) {

    }

    @Override
    public void onEcCalibrationResult(Core core, EcCalibratorStatus ecCalibratorStatus, int i) {

    }

    @Override
    public void onEcCalibrationAudioInit(Core core) {

    }

    @Override
    public void onEcCalibrationAudioUninit(Core core) {

    }
}


