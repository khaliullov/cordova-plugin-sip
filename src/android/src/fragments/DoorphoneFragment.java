package com.sip.linphone.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.widget.ListView;
import android.widget.TextView;

import android.widget.AdapterView;

import com.sip.linphone.LinphoneUnlockActivity;
import com.sip.linphone.adapters.DoorphoneArrayAdapter;
import com.sip.linphone.models.Doorphone;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ru.simdev.evo.video.R;

public class DoorphoneFragment extends Fragment {
    private static final String TAG = "LinphoneSip";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_linphone_doorphones, container, false);

        ArrayList<Doorphone> doorphones = getList();

        ListView listview = view.findViewById(R.id.doorphones_list);

        if (doorphones.size() > 0) {
            TextView text = view.findViewById(R.id.doorphones_list_empty);
            text.setVisibility(View.GONE);

            final DoorphoneArrayAdapter adapter = new DoorphoneArrayAdapter(getContext());
            adapter.setData(doorphones);
            listview.setAdapter(adapter);

            listview.setOnItemClickListener((AdapterView<?> parent, final View v, int position, long id) -> {
                android.util.Log.d(TAG, "[UNLOCK] click " + position);
                getParentActivity().showTitleFragment((Doorphone) parent.getItemAtPosition(position));
            });
        } else {
            listview.setVisibility(View.GONE);
        }

        return view;
    }

    public LinphoneUnlockActivity getParentActivity() {
        return (LinphoneUnlockActivity) getActivity();
    }

    ArrayList<Doorphone> getList() {
        ArrayList<Doorphone> data = new ArrayList<>();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String PREFS_NAME = preferences.getString("NativeStorageSharedPreferencesName", "NativeStorage");
        SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String jsonContacts = settings.getString("contacts", "[]");

        try {
            JSONArray contacts = new JSONArray(jsonContacts);
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject contact = contacts.getJSONObject(i);
                android.util.Log.d(TAG, "[UNLOCK] " + contact.getString("address"));

                if (contact.has("id") && contact.has("sip_name") && contact.has("door_open_url")) {
                    JSONArray doors = contact.getJSONArray("doors");

                    for (int l = 0; l < doors.length(); l++) {
                        JSONObject door = doors.getJSONObject(l);

                        Doorphone df = new Doorphone();
                        df.id = contact.getString("id");
                        df.address = contact.getString("address");
                        df.entrance = contact.getString("entrance");
                        df.doorId = door.has("doornum") ? door.getString("doornum") : "";
                        df.door = door.has("name") ? door.getString("name") : "";

                        data.add(df);
                    }
                }
            }
        } catch (JSONException e) {

        }

        return data;
    }
}
