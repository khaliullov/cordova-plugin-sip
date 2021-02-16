package com.sip.linphone;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import ru.simdev.evo.video.R;

/**
 * Implementation of App Widget functionality.
 */
public class UnlockWidget extends AppWidgetProvider {

    String unlockUrl = "";

    static protected PendingIntent getPendingSelfIntent(Context context, int widgetID, String action) {
        Intent intent = new Intent(context, UnlockWidget.class);
        intent.setAction(action);
        intent.putExtra("widgetID", widgetID);
        return PendingIntent.getBroadcast(context, widgetID, intent, 0);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        LinphoneStorage storage = new LinphoneStorage(context);
        String title = storage.getUnlockTitle(appWidgetId);
        title = title.equals("") ? "Мой домофон" : title;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.unlock_widget);

        views.setOnClickPendingIntent(R.id.widget_unlock_button, getPendingSelfIntent(context, appWidgetId,"open"));
        views.setTextViewText(R.id.widget_unlock_label, title);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == "open") {
            int widgetId = intent.getIntExtra("widgetID", 0);
            LinphoneStorage storage = new LinphoneStorage(context);
            String doorphone = storage.getUnlockId(widgetId);

            String[] ids = doorphone.split(":");

            unlockUrl = getDoorphoneUrl(context, ids[0]);

            if (ids.length > 1) {
                unlockUrl += "/" + ids[1];
            }

            android.util.Log.d("LinphoneWidget", unlockUrl);

            if (!unlockUrl.equals("")) {
                new AsyncDoorOpenRequest(context).execute(unlockUrl);
            }
        }

        super.onReceive(context, intent);
    }

    class AsyncDoorOpenRequest extends AsyncTask<String, Void, String> {
        Context context;

        AsyncDoorOpenRequest(Context c) {
            context = c;
        }

        @Override
        protected String doInBackground(String... args) {
            String url = args[0];
            String result = "error!";
            HttpURLConnection httpConn = null;
            try {
                URL myurl = new URL(url);
                httpConn = (HttpURLConnection) myurl.openConnection();
                httpConn.setUseCaches(false);
                httpConn.setDoOutput(false);
                httpConn.setDoInput(true);
                httpConn.setReadTimeout(3 * 1000);
                httpConn.setConnectTimeout(3 * 1000);
                httpConn.setRequestMethod("POST");

                StringBuilder content;
                int statusCode = httpConn.getResponseCode();

                android.util.Log.d("LinphoneSip", "statusCode " + statusCode);

                if (statusCode == 200) {
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(httpConn.getInputStream()))) {

                        String line;
                        content = new StringBuilder();

                        while ((line = in.readLine()) != null) {
                            content.append(line);
                        }
                        JSONObject json = new JSONObject(content.toString());
                        if (json.has("status") && json.getBoolean("status")) {
                            result = "opened";
                        }
                    } catch (Exception e) {

                    }
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (httpConn != null) {
                    httpConn.disconnect();
                }
            }

            android.util.Log.d("LinphoneSip", "door status");
            android.util.Log.d("LinphoneSip", result);

            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("opened")) {
                Toast.makeText(context, "Дверь открыта", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getDoorphoneUrl(Context context, String id) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String PREFS_NAME = preferences.getString("NativeStorageSharedPreferencesName", "NativeStorage");
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String jsonContacts = settings.getString("contacts", "[]");

        try {
            JSONArray contacts = new JSONArray(jsonContacts);
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject contact = (JSONObject) contacts.getJSONObject(i);
                if (contact.has("id") && contact.getString("id").equals(id) && contact.has("door_open_url")) {
                    return contact.getString("door_open_url");
                }
            }
        } catch (JSONException e) {

        }

        return "";
    }
}