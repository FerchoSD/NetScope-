package com.netscope.lan.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.netscope.lan.R;
import com.netscope.lan.core.net.WifiScanner;

import java.util.ArrayList;
import java.util.List;

public final class WifiAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private List<WifiScanner.Network> items = new ArrayList<>();

    public WifiAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<WifiScanner.Network> networks) {
        items = networks == null ? new ArrayList<WifiScanner.Network>() : new ArrayList<>(networks);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }

    @Override public WifiScanner.Network getItem(int position) { return items.get(position); }

    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView != null ? convertView
                : inflater.inflate(R.layout.item_wifi, parent, false);
        WifiScanner.Network network = items.get(position);

        TextView glyph = row.findViewById(R.id.tvWifiGlyph);
        TextView ssid = row.findViewById(R.id.tvWifiSsid);
        TextView meta = row.findViewById(R.id.tvWifiMeta);
        TextView signal = row.findViewById(R.id.tvWifiSignal);
        TextView security = row.findViewById(R.id.tvWifiSecurity);

        glyph.setText(WifiScanner.signalGlyph(network.signalPercent));
        ssid.setText(network.ssid);
        StringBuilder info = new StringBuilder();
        info.append("canal ").append(network.channel > 0 ? String.valueOf(network.channel) : "—");
        if (!network.band.isEmpty()) info.append(" · ").append(network.band);
        if (!network.bssid.isEmpty()) info.append(" · ").append(network.bssid);
        if (network.connected) info.append(" · conectada");
        meta.setText(info.toString());
        signal.setText(network.signalPercent + "%");
        signal.setTextColor(signalColor(network.signalPercent));
        security.setText(network.security);
        return row;
    }

    private int signalColor(int percent) {
        if (percent >= 60) return 0xFF34D399;
        if (percent >= 30) return 0xFFFBBF24;
        return 0xFFF87171;
    }
}
