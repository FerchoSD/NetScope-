package com.netscope.lan.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.netscope.lan.R;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.net.HostClassifier;
import com.netscope.lan.util.Formats;

import java.util.ArrayList;
import java.util.List;

public final class DeviceAdapter {
    public interface OnDeviceClickListener {
        void onDeviceClick(Device device);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final LinearLayout container;
    private final OnDeviceClickListener listener;
    private List<Device> items = new ArrayList<>();

    public DeviceAdapter(Context context, LinearLayout container, OnDeviceClickListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.container = container;
        this.listener = listener;
    }

    public void setItems(List<Device> devices) {
        items = new ArrayList<>(devices);
        render();
    }

    private void render() {
        container.removeAllViews();
        for (final Device device : items) {
            View row = inflater.inflate(R.layout.item_device, container, false);
            bind(row, device);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (listener != null) listener.onDeviceClick(device);
                }
            });
            container.addView(row);
        }
    }

    private void bind(View row, Device device) {
        TextView icon = row.findViewById(R.id.tvDeviceIcon);
        TextView name = row.findViewById(R.id.tvDeviceName);
        TextView ip = row.findViewById(R.id.tvDeviceIp);
        TextView type = row.findViewById(R.id.tvDeviceType);
        TextView latency = row.findViewById(R.id.tvDeviceLatency);
        TextView ports = row.findViewById(R.id.tvDevicePorts);
        TextView portList = row.findViewById(R.id.tvDevicePortList);
        TextView risk = row.findViewById(R.id.tvDeviceRisk);
        TextView flag = row.findViewById(R.id.tvDeviceFlag);

        icon.setText(HostClassifier.iconLetter(device.type));
        name.setText(device.displayName());
        ip.setText(device.ip);
        type.setText(device.typeLabel);
        latency.setText(Formats.latency(device.latencyMs));
        ports.setText(String.valueOf(device.openPortCount()));

        String portLine = Formats.ports(device.openPorts, 5);
        if (portLine.isEmpty()) {
            portList.setVisibility(View.GONE);
        } else {
            portList.setText(portLine);
            portList.setVisibility(View.VISIBLE);
        }

        int level = 0;
        for (PortResult result : device.openPorts) level = Math.max(level, result.risk);
        if (level >= 2) {
            risk.setText(R.string.risk_high);
            risk.setVisibility(View.VISIBLE);
        } else if (level == 1) {
            risk.setText(R.string.risk_medium);
            risk.setVisibility(View.VISIBLE);
        } else {
            risk.setVisibility(View.GONE);
        }

        if (device.isNew) {
            flag.setText(R.string.trust_chip_new);
            flag.setTextColor(context.getColor(R.color.warn));
            flag.setVisibility(View.VISIBLE);
        } else if (device.trusted) {
            flag.setText(R.string.trust_chip_trusted);
            flag.setTextColor(context.getColor(R.color.accent));
            flag.setVisibility(View.VISIBLE);
        } else {
            flag.setVisibility(View.GONE);
        }
    }
}
