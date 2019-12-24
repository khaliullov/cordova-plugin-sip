package com.sip.linphone;

import android.content.Context;

import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Config;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;

import java.io.File;

public class LinphonePreferences {
    private static final String TAG = "LinphoneSip";

    private static final int LINPHONE_CORE_RANDOM_PORT = -1;
    private static final String LINPHONE_DEFAULT_RC = "/.linphonerc";
    private static final String LINPHONE_FACTORY_RC = "/linphonerc";
    private static final String LINPHONE_LPCONFIG_XSD = "/lpconfig.xsd";
    private static final String DEFAULT_ASSISTANT_RC = "/default_assistant_create.rc";
    private static final String LINPHONE_ASSISTANT_RC = "/linphone_assistant_create.rc";

    private static LinphonePreferences sInstance;

    private Context mContext;
    private String mBasePath;

    private LinphonePreferences() {}

    public static synchronized LinphonePreferences instance() {
        if (sInstance == null) {
            sInstance = new LinphonePreferences();
        }
        return sInstance;
    }

    public void destroy() {
        mContext = null;
        sInstance = null;
    }

    public void setContext(Context c) {
        mContext = c;
        mBasePath = mContext.getFilesDir().getAbsolutePath();
    }

    private Core getLc() {
        if (!LinphoneContext.isReady()) return null;

        return LinphoneMiniManager.mCore;
    }

    public String getLinphoneDefaultConfig() {
        return mBasePath + LINPHONE_DEFAULT_RC;
    }

    public String getLinphoneFactoryConfig() {
        return mBasePath + LINPHONE_FACTORY_RC;
    }

    public String getDefaultDynamicConfigFile() {
        return mBasePath + DEFAULT_ASSISTANT_RC;
    }

    public String getLinphoneDynamicConfigFile() {
        return mBasePath + LINPHONE_ASSISTANT_RC;
    }

    public Config getConfig() {
        android.util.Log.i(TAG, "[Preferences] getConfig");
        Core core = getLc();
        if (core != null) {
            return core.getConfig();
        }

        if (!LinphoneContext.isReady()) {
            android.util.Log.i(TAG, "[Preferences] not is ready");

            File linphonerc = new File(mBasePath + "/.linphonerc");

            if (linphonerc.exists()) {
                return Factory.instance().createConfig(linphonerc.getAbsolutePath());
            }
        } else {
            android.util.Log.i(TAG, "[Preferences] else");

            return Factory.instance().createConfig(getLinphoneDefaultConfig());
        }

        return null;
    }

    private String getPushNotificationRegistrationID() {
        if (getConfig() == null) return null;
        return getConfig().getString("app", "push_notification_regid", null);
    }

    public void setPushNotificationRegistrationID(String regId) {
        if (getConfig() == null) return;
        android.util.Log.i(TAG, "[Preferences] New token received: " + regId);
        getConfig().setString("app", "push_notification_regid", (regId != null) ? regId : "");

        setPushNotificationEnabled(true);
    }

    public void setPushNotificationEnabled(boolean enable) {
        android.util.Log.i(TAG, "[Preferences] setPushNotificationEnabled");
        if (getConfig() == null) return;
        getConfig().setBool("app", "push_notification", enable);

        Core core = getLc();
        if (core == null) {
            return;
        }

        if (enable) {
            // Add push infos to exisiting proxy configs
            String regId = getPushNotificationRegistrationID();
            String appId = Linphone.APPID;

            android.util.Log.i(TAG, "[Preferences] " + regId + " - " + appId);

            LinphoneContext.instance().mLinphoneManager.setPushNotification(appId, regId);
        } else {
            LinphoneContext.instance().mLinphoneManager.setPushNotification("", "");
        }
    }

    private ProxyConfig getProxyConfig(int n) {
        if (getLc() == null) return null;
        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (n < 0 || n >= prxCfgs.length) return null;
        return prxCfgs[n];
    }

    private AuthInfo getAuthInfo(int n) {
        ProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) return null;
        Address addr = prxCfg.getIdentityAddress();
        return getLc().findAuthInfo(null, addr.getUsername(), addr.getDomain());
    }

    public String getAccountUsername(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getUsername();
    }

    public String getAccountHa1(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getHa1();
    }

    public String getAccountDomain(int n) {
        ProxyConfig proxyConf = getProxyConfig(n);
        return (proxyConf != null) ? proxyConf.getDomain() : "";
    }
}
