package com.sip.linphone;

import android.content.Context;
import android.content.SharedPreferences;

public class LinphoneStorage {
    private SharedPreferences.Editor editor;
    private Context mContext;

    public SharedPreferences pref;

    private int PRIVATE_MODE = Context.MODE_PRIVATE;
    private static final String PREF_NAME = "_linphone_store";

    public LinphoneStorage(Context context) {
        mContext = context;
        pref = mContext.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    public void setUsername(String username) {
        editor.putString("username", username);
        editor.commit();
    }

    public String getUsername() {
        return pref.getString("username", "");
    }

    public void setPassword(String password) {
        editor.putString("password", password);
        editor.commit();
    }

    public String getPassword() {
        return pref.getString("password", "");
    }

    public void setDomain(String domain) {
        editor.putString("domain", domain);
        editor.commit();
    }

    public String getDomain() {
        return pref.getString("domain", "");
    }
}