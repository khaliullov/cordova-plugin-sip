package com.sip.linphone.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.sip.linphone.models.Doorphone;

import java.util.List;

import ru.simdev.evo.video.R;

public class DoorphoneArrayAdapter extends ArrayAdapter<Doorphone> {
    public DoorphoneArrayAdapter(Context context) {
        super(context, R.layout.item_linphone_doorphone);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.item_linphone_doorphone, parent, false);

        Doorphone data = getItem(position);

        TextView address = view.findViewById(R.id.doorphone_address);
        address.setText(data.address);

        TextView entrance = view.findViewById(R.id.doorphone_entrance);

        if (!data.door.equals("")) {
            entrance.setText("Подъезд " + data.entrance + ", " + data.door);
        } else {
            entrance.setText("Подъезд " + data.entrance);
        }

        return view;
    }

    public void setData(Doorphone data) {
        add(data);
        notifyDataSetChanged();
    }

    public void setData(List<Doorphone> data) {
        addAll(data);
        notifyDataSetChanged();
    }
}
