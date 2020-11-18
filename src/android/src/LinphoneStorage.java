package com.sip.linphone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class LinphoneStorage {
    private SharedPreferences.Editor editor;
    private SharedPreferences.Editor editorIonic;
    private Context mContext;

    public SharedPreferences pref;
    public SharedPreferences prefIonic;
    public SharedPreferences prefStatus;

    private int PRIVATE_MODE = Context.MODE_PRIVATE;
    private static final String PREF_NAME = "_linphone_store";
    private static final String IONIC_PREF_NAME = "NativeStorage";

    public LinphoneStorage(Context context) {
        mContext = context;
        pref = mContext.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();

        prefIonic = mContext.getSharedPreferences(IONIC_PREF_NAME, PRIVATE_MODE);
        editorIonic = prefIonic.edit();
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

    public void setStun(String stun) {
        editor.putString("stun", stun);
        editor.commit();
    }

    public String getStun() {
        return pref.getString("stun", "");
    }

    public String getNotdisturb(String doorphoneId) {
        String jsonSettings = prefIonic.getString("intercom_settings", "[]");

        try {
            JSONObject settings = new JSONObject(jsonSettings);

            JSONObject doorphone = settings.getJSONObject(doorphoneId);

            if (doorphone != null) {
                return doorphone.getString("notdisturb");
            }
        } catch (JSONException e) {
        }

        return "0";
    }

    public void setForeground(Boolean foreground) {
        editor.putBoolean("foreground", foreground);
        editor.commit();
    }

    public Boolean getForeground() {
        return pref.getBoolean("foreground", true);
    }

    public void setStatus(String status) {
        editorIonic.putString("intercom_status", "{\"status\":\"" + status + "\"}");
        editorIonic.commit();
    }
}