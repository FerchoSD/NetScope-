package com.netscope.lan.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.netscope.lan.R;
import com.netscope.lan.core.model.PortResult;

import java.util.ArrayList;
import java.util.List;

public final class PortAdapter {
    public interface OnPortClickListener {
        void onPortClick(PortResult result);
    }

    private final LayoutInflater inflater;
    private final LinearLayout container;
    private final OnPortClickListener listener;
    private List<PortResult> items = new ArrayList<>();

    public PortAdapter(Context context, LinearLayout container, OnPortClickListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.container = container;
        this.listener = listener;
    }

    public void setItems(List<PortResult> ports) {
        items = new ArrayList<>(ports);
        render();
    }

    private void render() {
        container.removeAllViews();
        for (final PortResult port : items) {
            View row = inflater.inflate(R.layout.item_port, container, false);
            TextView number = row.findViewById(R.id.tvPortNumber);
            TextView service = row.findViewById(R.id.tvPortService);
            TextView state = row.findViewById(R.id.tvPortState);
            TextView note = row.findViewById(R.id.tvPortNote);

            number.setText(String.valueOf(port.port));
            service.setText(port.service.isEmpty()
                    ? row.getContext().getString(R.string.unknown_service) : port.service);
            state.setText(PortResult.stateLabel(port.state));

            StringBuilder detail = new StringBuilder();
            if (!port.riskNote.isEmpty()) detail.append(port.riskNote);
            if (!port.banner.isEmpty()) {
                if (detail.length() > 0) detail.append('\n');
                detail.append(port.banner);
            }
            if (detail.length() > 0) {
                note.setText(detail.toString());
                note.setVisibility(View.VISIBLE);
            } else {
                note.setVisibility(View.GONE);
            }

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (listener != null) listener.onPortClick(port);
                }
            });
            container.addView(row);
        }
    }
}
