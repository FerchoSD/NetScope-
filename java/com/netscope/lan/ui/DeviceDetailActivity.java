package com.netscope.lan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.netscope.lan.R;
import com.netscope.lan.core.export.DownloadWriter;
import com.netscope.lan.core.export.JsonExporter;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;
import com.netscope.lan.core.net.HostClassifier;
import com.netscope.lan.core.net.ScanController;
import com.netscope.lan.core.net.ServiceCatalog;
import com.netscope.lan.util.Formats;
import com.netscope.lan.util.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DeviceDetailActivity extends Activity {
    public static final String EXTRA_IP = "ip";
    public static final String EXTRA_HOSTNAME = "hostname";
    public static final String EXTRA_VENDOR = "vendor";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TYPE_LABEL = "type_label";
    public static final String EXTRA_LATENCY = "latency";
    public static final String EXTRA_GATEWAY = "gateway";
    public static final String EXTRA_SELF = "self";

    private ScanController controller;
    private ScanReport report;
    private Device device;
    private PortAdapter portAdapter;
    private PortGridView portGrid;

    private TextView tvDetailTitle;
    private TextView tvDetailSubtitle;
    private TextView tvDetailIcon;
    private TextView tvDetailName;
    private TextView tvDetailIp;
    private TextView tvDetailType;
    private TextView tvDetailHostname;
    private TextView tvDetailVendor;
    private TextView tvDetailLatency;
    private TextView tvDetailSources;
    private TextView tvDetailPortSummary;
    private TextView tvDetailOs;
    private TextView tvDetailServer;
    private TextView tvDetailHttpTitle;
    private TextView tvDetailTls;
    private TextView tvDetailServices;
    private TextView tvPortStatus;
    private TextView tvOpenCount;
    private TextView tvNoPorts;
    private TextView tvRiskAlert;
    private LinearLayout portsContainer;
    private LinearLayout customRangeRow;
    private ProgressBar progressPorts;
    private Button btnPresetQuick;
    private Button btnPresetCommon;
    private Button btnPresetWeb;
    private Button btnPresetFull;
    private Button btnPresetCustom;
    private Button btnCustomScan;
    private Button btnStopPorts;
    private Button btnRename;
    private Button btnTrust;
    private EditText etCustomPorts;

    private boolean scanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        controller = ScanController.get(this);
        String ip = getIntent().getStringExtra(EXTRA_IP);
        if (ip == null || ip.isEmpty()) {
            finish();
            return;
        }

        report = controller.getLastReport();
        device = report == null ? null : report.findDevice(ip);
        if (device == null) device = restoreDevice(ip);
        if (report == null) report = new ScanReport();
        device.customName = Prefs.getNameAlias(this, device.ip);
        device.trusted = Prefs.isTrusted(this, device.ip);

        bindViews();
        setupActions();
        updateDeviceInfo();
        portAdapter = new PortAdapter(this, portsContainer, new PortAdapter.OnPortClickListener() {
            @Override public void onPortClick(PortResult result) {
                showOpenPortDialog(result);
            }
        });
        portGrid.setOnPortTapListener(new PortGridView.OnPortTapListener() {
            @Override public void onPortTapped(int port, int state) {
                showPortDialog(port, state);
            }
        });
        renderPorts();
    }

    private Device restoreDevice(String ip) {
        Device restored = new Device(ip);
        restored.hostname = safe(getIntent().getStringExtra(EXTRA_HOSTNAME));
        restored.vendor = safe(getIntent().getStringExtra(EXTRA_VENDOR));
        restored.type = safe(getIntent().getStringExtra(EXTRA_TYPE));
        if (restored.type.isEmpty()) restored.type = "unknown";
        restored.typeLabel = safe(getIntent().getStringExtra(EXTRA_TYPE_LABEL));
        if (restored.typeLabel.isEmpty()) restored.typeLabel = getString(R.string.devices);
        restored.latencyMs = getIntent().getLongExtra(EXTRA_LATENCY, -1);
        restored.gateway = getIntent().getBooleanExtra(EXTRA_GATEWAY, false);
        restored.self = getIntent().getBooleanExtra(EXTRA_SELF, false);
        return restored;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void bindViews() {
        tvDetailTitle = findViewById(R.id.tvDetailTitle);
        tvDetailSubtitle = findViewById(R.id.tvDetailSubtitle);
        tvDetailIcon = findViewById(R.id.tvDetailIcon);
        tvDetailName = findViewById(R.id.tvDetailName);
        tvDetailIp = findViewById(R.id.tvDetailIp);
        tvDetailType = findViewById(R.id.tvDetailType);
        tvDetailHostname = findViewById(R.id.tvDetailHostname);
        tvDetailVendor = findViewById(R.id.tvDetailVendor);
        tvDetailLatency = findViewById(R.id.tvDetailLatency);
        tvDetailSources = findViewById(R.id.tvDetailSources);
        tvDetailPortSummary = findViewById(R.id.tvDetailPortSummary);
        tvDetailOs = findViewById(R.id.tvDetailOs);
        tvDetailServer = findViewById(R.id.tvDetailServer);
        tvDetailHttpTitle = findViewById(R.id.tvDetailHttpTitle);
        tvDetailTls = findViewById(R.id.tvDetailTls);
        tvDetailServices = findViewById(R.id.tvDetailServices);
        tvPortStatus = findViewById(R.id.tvPortStatus);
        tvOpenCount = findViewById(R.id.tvOpenCount);
        tvNoPorts = findViewById(R.id.tvNoPorts);
        tvRiskAlert = findViewById(R.id.tvRiskAlert);
        portsContainer = findViewById(R.id.portsContainer);
        customRangeRow = findViewById(R.id.customRangeRow);
        progressPorts = findViewById(R.id.progressPorts);
        btnPresetQuick = findViewById(R.id.btnPresetQuick);
        btnPresetCommon = findViewById(R.id.btnPresetCommon);
        btnPresetWeb = findViewById(R.id.btnPresetWeb);
        btnPresetFull = findViewById(R.id.btnPresetFull);
        btnPresetCustom = findViewById(R.id.btnPresetCustom);
        btnCustomScan = findViewById(R.id.btnCustomScan);
        btnStopPorts = findViewById(R.id.btnStopPorts);
        btnRename = findViewById(R.id.btnRename);
        btnTrust = findViewById(R.id.btnTrust);
        etCustomPorts = findViewById(R.id.etCustomPorts);
        portGrid = findViewById(R.id.portGrid);
    }

    private void setupActions() {
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnExportDevice).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportDevice(); }
        });
        btnRename.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRenameDialog(); }
        });
        btnTrust.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTrust(); }
        });
        btnPresetQuick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startPorts(ServiceCatalog.quickPorts()); }
        });
        btnPresetCommon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startPorts(ServiceCatalog.commonPorts()); }
        });
        btnPresetWeb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startPorts(ServiceCatalog.webPorts()); }
        });
        btnPresetFull.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmFullScan(); }
        });
        btnPresetCustom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                customRangeRow.setVisibility(View.VISIBLE);
                etCustomPorts.requestFocus();
            }
        });
        btnCustomScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startCustomScan(); }
        });
        btnStopPorts.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                controller.cancel();
                tvPortStatus.setText(R.string.cancel_requested);
                btnStopPorts.setEnabled(false);
            }
        });
    }

    private void updateDeviceInfo() {
        String name = device.displayName();
        tvDetailTitle.setText(name);
        tvDetailSubtitle.setText(device.ip);
        tvDetailIcon.setText(HostClassifier.iconLetter(device.type));
        tvDetailName.setText(name);
        tvDetailIp.setText(device.ip);
        tvDetailType.setText(device.typeLabel);
        tvDetailHostname.setText(device.hostname.isEmpty() ? "—" : device.hostname);
        tvDetailVendor.setText(device.vendor.isEmpty()
                ? getString(R.string.vendor_unknown) : device.vendor);
        tvDetailLatency.setText(Formats.latency(device.latencyMs));
        if (device.sources.isEmpty()) {
            tvDetailSources.setText(R.string.sources_none);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String source : device.sources) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(source);
            }
            tvDetailSources.setText(sb.toString());
        }
        tvDetailOs.setText(orUnknown(device.osGuess));
        tvDetailServer.setText(orUnknown(device.server));
        tvDetailHttpTitle.setText(orUnknown(device.httpTitle));
        tvDetailTls.setText(orUnknown(device.tls));
        if (device.services.isEmpty()) {
            tvDetailServices.setText(R.string.intel_unknown);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String service : device.services) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(service);
            }
            tvDetailServices.setText(sb.toString());
        }
        btnTrust.setText(device.trusted ? R.string.trust_remove : R.string.trust_mark);
    }

    private String orUnknown(String value) {
        return value == null || value.isEmpty() ? getString(R.string.intel_unknown) : value;
    }

    private void showRenameDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.rename_hint);
        input.setText(Prefs.getNameAlias(this, device.ip));
        input.setSelection(input.getText().length());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_title)
                .setView(input)
                .setPositiveButton(R.string.rename_save, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        Prefs.setNameAlias(DeviceDetailActivity.this, device.ip, value);
                        device.customName = value;
                        updateDeviceInfo();
                        toast(getString(R.string.renamed));
                    }
                })
                .setNeutralButton(R.string.rename_clear, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Prefs.setNameAlias(DeviceDetailActivity.this, device.ip, "");
                        device.customName = "";
                        updateDeviceInfo();
                        toast(getString(R.string.renamed));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleTrust() {
        boolean trusted = !Prefs.isTrusted(this, device.ip);
        Prefs.setTrusted(this, device.ip, trusted);
        device.trusted = trusted;
        device.isNew = false;
        updateDeviceInfo();
        toast(getString(trusted ? R.string.trust_marked : R.string.trust_removed));
    }

    private void renderPorts() {
        List<PortResult> ports = new ArrayList<>(device.openPorts);
        Collections.sort(ports, new Comparator<PortResult>() {
            @Override public int compare(PortResult a, PortResult b) {
                return Integer.compare(a.port, b.port);
            }
        });
        portAdapter.setItems(ports);
        portGrid.refresh();
        tvOpenCount.setText(String.valueOf(ports.size()));
        tvNoPorts.setVisibility(ports.isEmpty() ? View.VISIBLE : View.GONE);
        String summary = getString(R.string.ports_summary, ports.size(),
                device.closedPorts, device.filteredPorts);
        tvDetailPortSummary.setText(summary);
        if (!scanning) tvPortStatus.setText(summary);
        updateRiskAlert(ports);
    }

    private void updateRiskAlert(List<PortResult> ports) {
        StringBuilder notes = new StringBuilder();
        for (PortResult port : ports) {
            if (port.risk < 2 || port.riskNote.isEmpty()) continue;
            if (notes.length() > 0) notes.append("\n");
            notes.append(port.port).append(" · ").append(port.riskNote);
            if (notes.length() > 220) break;
        }
        if (notes.length() > 0) {
            tvRiskAlert.setText(getString(R.string.danger_ports, notes.toString()));
            tvRiskAlert.setVisibility(View.VISIBLE);
        } else {
            tvRiskAlert.setVisibility(View.GONE);
        }
    }

    private void confirmFullScan() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.full_scan_warning)
                .setPositiveButton(R.string.preset_full, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        startPorts(ServiceCatalog.fullPorts());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startCustomScan() {
        try {
            int[] ports = ServiceCatalog.parsePorts(etCustomPorts.getText().toString());
            startPorts(ports);
        } catch (IllegalArgumentException e) {
            toast(getString(R.string.invalid_range));
        }
    }

    private void startPorts(final int[] ports) {
        if (scanning) return;
        if (controller.isBusy()) {
            toast(getString(R.string.error_busy));
            return;
        }
        if (ports.length == 0) {
            toast(getString(R.string.invalid_range));
            return;
        }

        scanning = true;
        setScanningUi(true);
        progressPorts.setProgress(0);
        tvPortStatus.setText(getString(R.string.scan_running, 0, ports.length));

        controller.startPortScan(device, ports, Prefs.getTimeoutMs(this),
                Prefs.getConcurrency(this), true, new ScanController.PortScanListener() {
                    @Override public void onOpen(PortResult result) {
                        renderPorts();
                    }
                    @Override public void onProgress(int done, int total) {
                        progressPorts.setProgress(total == 0 ? 0 : (int) (done * 100L / total));
                        tvPortStatus.setText(getString(R.string.scan_running, done, total));
                    }
                    @Override public void onFinished(List<PortResult> openPorts, long durationMs) {
                        scanning = false;
                        setScanningUi(false);
                        progressPorts.setProgress(100);
                        renderPorts();
                        updateDeviceInfo();
                        tvPortStatus.setText(getString(R.string.scan_done_ports,
                                openPorts.size(), Formats.duration(durationMs)));
                    }
                    @Override public void onError(String message) {
                        scanning = false;
                        setScanningUi(false);
                        tvPortStatus.setText(message);
                        if (!message.toLowerCase().contains("cancelad")) toast(message);
                    }
                });
    }

    private void setScanningUi(boolean active) {
        progressPorts.setVisibility(active ? View.VISIBLE : View.GONE);
        btnStopPorts.setVisibility(active ? View.VISIBLE : View.GONE);
        btnStopPorts.setEnabled(active);
        btnPresetQuick.setEnabled(!active);
        btnPresetCommon.setEnabled(!active);
        btnPresetWeb.setEnabled(!active);
        btnPresetFull.setEnabled(!active);
        btnPresetCustom.setEnabled(!active);
        btnCustomScan.setEnabled(!active);
    }

    private void showPortDialog(int port, int state) {
        String service = ServiceCatalog.name(port);
        String message = PortResult.stateLabel(state) + " · "
                + (service.isEmpty() ? getString(R.string.unknown_service) : service);
        String note = ServiceCatalog.riskNote(port);
        if (!note.isEmpty()) message += "\n\n" + note;
        String banner = "";
        for (PortResult result : device.openPorts) {
            if (result.port == port && !result.banner.isEmpty()) {
                banner = result.banner;
                break;
            }
        }
        if (!banner.isEmpty()) message += "\n\n" + getString(R.string.banner) + ": " + banner;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.port_detail_title, port))
                .setMessage(message)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showOpenPortDialog(PortResult result) {
        StringBuilder message = new StringBuilder();
        message.append(PortResult.stateLabel(result.state)).append(" · ")
                .append(result.service.isEmpty() ? getString(R.string.unknown_service) : result.service);
        if (result.rttMs >= 0) message.append("\n").append(getString(R.string.latency))
                .append(": ").append(Formats.latency(result.rttMs));
        if (!result.riskNote.isEmpty()) message.append("\n\n").append(result.riskNote);
        if (!result.banner.isEmpty()) message.append("\n\n").append(result.banner);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.port_detail_title, result.port))
                .setMessage(message.toString())
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void exportDevice() {
        try {
            String content = JsonExporter.exportDevice(report, device);
            String name = "NetScope_" + device.ip.replace('.', '-') + "_"
                    + Formats.fileTimestamp(System.currentTimeMillis()) + ".json";
            DownloadWriter.Saved saved = DownloadWriter.save(this, content, name, "application/json");
            toast(getString(R.string.export_saved, saved.path));
        } catch (Exception e) {
            toast(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (device != null) {
            updateDeviceInfo();
            renderPorts();
        }
    }
}
