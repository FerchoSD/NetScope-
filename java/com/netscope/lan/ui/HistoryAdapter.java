package com.netscope.lan.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.netscope.lan.R;
import com.netscope.lan.core.db.ScanRepository;
import com.netscope.lan.util.Formats;

import java.util.ArrayList;
import java.util.List;

public final class HistoryAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<ScanRepository.ScanSummary> items = new ArrayList<>();

    public HistoryAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<ScanRepository.ScanSummary> scans) {
        items.clear();
        items.addAll(scans);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }

    @Override public ScanRepository.ScanSummary getItem(int position) { return items.get(position); }

    @Override public long getItemId(int position) { return items.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) row = inflater.inflate(R.layout.item_history, parent, false);
        ScanRepository.ScanSummary summary = items.get(position);
        TextView time = row.findViewById(R.id.tvHistoryTime);
        TextView network = row.findViewById(R.id.tvHistoryNetwork);
        TextView counts = row.findViewById(R.id.tvHistoryCounts);
        TextView arrow = row.findViewById(R.id.tvHistoryArrow);

        time.setText(Formats.time(summary.timestamp));
        String ssid = summary.ssid.isEmpty() ? "Red local" : summary.ssid;
        String subnet = summary.subnet.isEmpty() ? "—" : summary.subnet;
        network.setText(ssid + " · " + subnet);
        counts.setText(parent.getContext().getString(R.string.history_counts,
                summary.deviceCount, summary.openPorts, Formats.duration(summary.durationMs)));
        arrow.setText("›");
        return row;
    }
}
