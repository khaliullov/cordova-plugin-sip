package com.sip.linphone;

import android.content.Context;
import android.content.SharedPreferences;

import com.sip.linphone.models.Sipauth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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

    public void setUnlockConfig(int widgetId, String id, String title) {
        String key = String.valueOf(widgetId);
        android.util.Log.d("LinphoneWidget", "key: " + key);

        String jsonSettings = pref.getString("unlockConfig", "{}");
        android.util.Log.d("LinphoneWidget", "get settings: " + jsonSettings);

        try {
            JSONObject settings = new JSONObject(jsonSettings);

            JSONObject entry = new JSONObject();
            entry.put("id", id);
            entry.put("title", title);
            settings.put(key, entry);

            android.util.Log.d("LinphoneWidget", "settings: " + settings.toString());

            editor.putString("unlockConfig", settings.toString());
            editor.commit();
        } catch (JSONException e) {
            android.util.Log.d("LinphoneWidget", "ERROR " + e.getMessage());
        }
    }

    public String getUnlockTitle(int widgetId) {
        String key = String.valueOf(widgetId);

        String jsonSettings = pref.getString("unlockConfig", "{}");

        try {
            JSONObject settings = new JSONObject(jsonSettings);

            if (settings.has(key)) {
                JSONObject entry = settings.getJSONObject(key);

                return entry.has("title") ? entry.getString("title") : "";
            }
        } catch (JSONException e) {

        }

        return "";
    }

    public String getUnlockId(int widgetId) {
        String key = String.valueOf(widgetId);

        String jsonSettings = pref.getString("unlockConfig", "{}");

        try {
            JSONObject settings = new JSONObject(jsonSettings);

            if (settings.has(key)) {
                JSONObject entry = settings.getJSONObject(key);

                return entry.has("id") ? entry.getString("id") : "";
            }
        } catch (JSONException e) {

        }

        return "";
    }

    public void setAuth(Set<Sipauth> authList) {
        JSONArray data = new JSONArray();

        Iterator<Sipauth> itr = authList.iterator();

        while (itr.hasNext()) {
            Sipauth auth = itr.next();

            try {
                JSONObject entry = new JSONObject();
                entry.put("username", auth.username);
                entry.put("password", auth.password);
                entry.put("domain", auth.domain);

                data.put(entry);
            } catch (JSONException e) {

            }
        }

        editor.putString("intercomAuth", data.toString());
        editor.commit();
    }

    public Set<Sipauth> getAuth() {
        String dataString = pref.getString("intercomAuth", "[]");

        HashSet<Sipauth> authList = new HashSet();

        try {
            JSONArray data = new JSONArray(dataString);

            for (int i = 0; i < data.length(); i++) {
                JSONObject entry = (JSONObject) data.get(i);

                Sipauth auth = new Sipauth();
                auth.username = entry.has("username") ? entry.getString("username") : "";
                auth.password = entry.has("password") ? entry.getString("password") : "";
                auth.domain = entry.has("domain") ? entry.getString("domain") : "";

                authList.add(auth);
            }
        } catch (JSONException e) {

        }

        if (authList.size() == 0) {
            Sipauth auth = new Sipauth();
            auth.username = getUsername();
            auth.password = getPassword();
            auth.domain = getDomain();

            if (!auth.username.equals("")) {
                authList.add(auth);
            }
        }

        return authList;
    }
}