package com.netscope.lan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.netscope.lan.R;
import com.netscope.lan.core.db.ScanRepository;
import com.netscope.lan.core.diff.ScanDiffEngine;
import com.netscope.lan.core.export.CsvExporter;
import com.netscope.lan.core.export.DownloadWriter;
import com.netscope.lan.core.export.JsonExporter;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.ScanDiff;
import com.netscope.lan.core.model.ScanReport;
import com.netscope.lan.core.net.NetworkInfoProvider;
import com.netscope.lan.core.net.ScanController;
import com.netscope.lan.core.net.ScanEngine;
import com.netscope.lan.core.net.SubnetMath;
import com.netscope.lan.util.Formats;
import com.netscope.lan.util.PermissionHelper;
import com.netscope.lan.util.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private ScanController controller;
    private ScanRepository repository;
    private DeviceAdapter deviceAdapter;
    private HistoryAdapter historyAdapter;

    private final Map<String, Device> liveDevices = new LinkedHashMap<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable renderRunnable = new Runnable() {
        @Override public void run() { renderDevices(); }
    };

    private ScrollView pageRed;
    private View pageHistory;
    private ScrollView pageSettings;
    private LinearLayout devicesContainer;
    private LinearLayout emptyState;
    private ListView listHistory;
    private View emptyHistory;
    private RadarView radar;
    private ProgressBar progressScan;
    private TextView tvStage;
    private TextView tvStatus;
    private TextView tvNetworkSummary;
    private TextView tvConnType;
    private TextView tvSsid;
    private TextView tvLocalIp;
    private TextView tvGateway;
    private TextView tvNetmask;
    private TextView tvDns;
    private TextView tvIface;
    private TextView tvStatDevices;
    private TextView tvStatPorts;
    private TextView tvStatTime;
    private TextView tvDeviceCount;
    private TextView tvNavRed;
    private TextView tvNavHistory;
    private TextView tvNavSettings;
    private Button btnScan;
    private Button btnStop;
    private Button btnExportLastHistory;
    private Button btnExportLastSettings;
    private Button btnClearHistory;
    private SeekBar seekTimeout;
    private SeekBar seekConcurrency;
    private TextView tvTimeoutValue;
    private TextView tvConcurrencyValue;
    private Switch switchMdns;
    private Switch switchSsdp;
    private Switch switchNetbios;
    private Switch switchReverseDns;
    private Switch switchDevMode;
    private LinearLayout devOptions;
    private SeekBar seekMaxHosts;
    private TextView tvMaxHostsValue;

    private ScanReport currentReport;
    private boolean scanning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        controller = ScanController.get(this);
        repository = new ScanRepository(this);

        bindViews();
        setupAdapters();
        setupNavigation();
        setupActions();
        setupSettings();

        loadNetworkInfo();
        renderDevices();
        loadHistory();

        if (Prefs.isLegalAccepted(this)) {
            PermissionHelper.requestRuntimePermissions(this);
        } else {
            showLegalDialog();
        }
    }

    private void bindViews() {
        pageRed = findViewById(R.id.pageRed);
        pageHistory = findViewById(R.id.pageHistory);
        pageSettings = findViewById(R.id.pageSettings);
        devicesContainer = findViewById(R.id.devicesContainer);
        emptyState = findViewById(R.id.emptyState);
        listHistory = findViewById(R.id.listHistory);
        emptyHistory = findViewById(R.id.emptyHistory);
        radar = findViewById(R.id.radarView);
        progressScan = findViewById(R.id.progressScan);
        tvStage = findViewById(R.id.tvStage);
        tvStatus = findViewById(R.id.tvStatus);
        tvNetworkSummary = findViewById(R.id.tvNetworkSummary);
        tvConnType = findViewById(R.id.tvConnType);
        tvSsid = findViewById(R.id.tvSsid);
        tvLocalIp = findViewById(R.id.tvLocalIp);
        tvGateway = findViewById(R.id.tvGateway);
        tvNetmask = findViewById(R.id.tvNetmask);
        tvDns = findViewById(R.id.tvDns);
        tvIface = findViewById(R.id.tvIface);
        tvStatDevices = findViewById(R.id.tvStatDevices);
        tvStatPorts = findViewById(R.id.tvStatPorts);
        tvStatTime = findViewById(R.id.tvStatTime);
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        tvNavRed = findViewById(R.id.tvNavRed);
        tvNavHistory = findViewById(R.id.tvNavHistory);
        tvNavSettings = findViewById(R.id.tvNavSettings);
        btnScan = findViewById(R.id.btnScan);
        btnStop = findViewById(R.id.btnStop);
        btnExportLastHistory = findViewById(R.id.btnExportLastHistory);
        btnExportLastSettings = findViewById(R.id.btnExportLastSettings);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        seekTimeout = findViewById(R.id.seekTimeout);
        seekConcurrency = findViewById(R.id.seekConcurrency);
        tvTimeoutValue = findViewById(R.id.tvTimeoutValue);
        tvConcurrencyValue = findViewById(R.id.tvConcurrencyValue);
        switchMdns = findViewById(R.id.switchMdns);
        switchSsdp = findViewById(R.id.switchSsdp);
        switchNetbios = findViewById(R.id.switchNetbios);
        switchReverseDns = findViewById(R.id.switchReverseDns);
        switchDevMode = findViewById(R.id.switchDevMode);
        devOptions = findViewById(R.id.devOptions);
        seekMaxHosts = findViewById(R.id.seekMaxHosts);
        tvMaxHostsValue = findViewById(R.id.tvMaxHostsValue);
    }

    private void setupAdapters() {
        deviceAdapter = new DeviceAdapter(this, devicesContainer, new DeviceAdapter.OnDeviceClickListener() {
            @Override public void onDeviceClick(Device device) {
                startActivity(new Intent(MainActivity.this, DeviceDetailActivity.class)
                        .putExtra(DeviceDetailActivity.EXTRA_IP, device.ip));
            }
        });
        historyAdapter = new HistoryAdapter(this);
        listHistory.setAdapter(historyAdapter);
    }

    private void setupNavigation() {
        findViewById(R.id.navRed).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPage(0); }
        });
        findViewById(R.id.navHistory).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPage(1); }
        });
        findViewById(R.id.navSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPage(2); }
        });
        showPage(0);
    }

    private void setupActions() {
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startNetworkScan(); }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { controller.cancel(); }
        });
        btnExportLastHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportCurrent(); }
        });
        btnExportLastSettings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportCurrent(); }
        });
        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClearHistory(); }
        });
        listHistory.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                showHistory(historyAdapter.getItem(position));
            }
        });
    }

    private void setupSettings() {
        int timeout = Prefs.getTimeoutMs(this);
        seekTimeout.setProgress(Math.max(0, Math.min(1800, timeout - 100)));
        tvTimeoutValue.setText(timeout + " ms");
        seekTimeout.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvTimeoutValue.setText((progress + 100) + " ms");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setTimeoutMs(MainActivity.this, seekBar.getProgress() + 100);
            }
        });

        int concurrency = Prefs.getConcurrency(this);
        seekConcurrency.setProgress(Math.max(0, Math.min(112, concurrency - 8)));
        tvConcurrencyValue.setText(String.valueOf(concurrency));
        seekConcurrency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvConcurrencyValue.setText(String.valueOf(progress + 8));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setConcurrency(MainActivity.this, seekBar.getProgress() + 8);
            }
        });

        switchMdns.setChecked(Prefs.isMdns(this));
        switchSsdp.setChecked(Prefs.isSsdp(this));
        switchNetbios.setChecked(Prefs.isNetbios(this));
        switchReverseDns.setChecked(Prefs.isReverseDns(this));

        switchMdns.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setMdns(MainActivity.this, isChecked);
            }
        });
        switchSsdp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setSsdp(MainActivity.this, isChecked);
            }
        });
        switchNetbios.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setNetbios(MainActivity.this, isChecked);
            }
        });
        switchReverseDns.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setReverseDns(MainActivity.this, isChecked);
            }
        });

        boolean devMode = Prefs.isDevMode(this);
        switchDevMode.setChecked(devMode);
        devOptions.setVisibility(devMode ? View.VISIBLE : View.GONE);
        switchDevMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setDevMode(MainActivity.this, isChecked);
                devOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        int maxHosts = Prefs.getMaxHosts(this);
        seekMaxHosts.setProgress(Math.max(0, Math.min(992, maxHosts - 32)));
        tvMaxHostsValue.setText(String.valueOf(maxHosts));
        seekMaxHosts.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvMaxHostsValue.setText(String.valueOf(progress + 32));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setMaxHosts(MainActivity.this, seekBar.getProgress() + 32);
            }
        });
    }

    private void showLegalDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.legal_title)
                .setMessage(R.string.legal_text)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Prefs.setLegalAccepted(MainActivity.this);
                        PermissionHelper.requestRuntimePermissions(MainActivity.this);
                    }
                })
                .show();
    }

    private void loadNetworkInfo() {
        NetworkInfoProvider.Snapshot snapshot = NetworkInfoProvider.get(this);
        tvConnType.setText(snapshot.type);
        tvSsid.setText(snapshot.ssid.isEmpty() ? "—" : snapshot.ssid);
        tvLocalIp.setText(snapshot.localIp.isEmpty() ? "—" : snapshot.localIp);
        tvGateway.setText(snapshot.gateway.isEmpty() ? "—" : snapshot.gateway);
        tvNetmask.setText(snapshot.netmask.isEmpty() ? "—" : snapshot.netmask);
        tvDns.setText(snapshot.dns.isEmpty() ? "—" : snapshot.dns);
        tvIface.setText(snapshot.iface.isEmpty() ? "—" : snapshot.iface);

        if (snapshot.scannable) {
            tvNetworkSummary.setText(snapshot.ssid + " · " + snapshot.localIp);
            if (!scanning) {
                tvStatus.setText(R.string.ready);
                tvStatus.setTextColor(getColor(R.color.accent));
            }
        } else {
            tvNetworkSummary.setText(getString(R.string.status_no_network) + " · " + snapshot.type);
            if (!scanning) {
                tvStatus.setText(R.string.status_no_network);
                tvStatus.setTextColor(getColor(R.color.warn));
            }
        }
    }

    private void startNetworkScan() {
        if (scanning || controller.isBusy()) {
            toast(getString(R.string.error_busy));
            return;
        }
        if (!Prefs.isLegalAccepted(this)) {
            showLegalDialog();
            return;
        }
        NetworkInfoProvider.Snapshot snapshot = NetworkInfoProvider.get(this);
        if (!snapshot.scannable) {
            toast(getString(R.string.no_wifi_text));
            return;
        }

        scanning = true;
        currentReport = null;
        liveDevices.clear();
        renderDevices();
        updateLiveStats();
        progressScan.setProgress(0);
        setScanningUi(true);
        tvStatus.setText(R.string.status_scanning);
        tvStatus.setTextColor(getColor(R.color.primary));
        tvStage.setText(R.string.stage_scanning);
        radar.setDeviceCount(0);
        radar.setScanning(true);

        controller.startNetworkScan(Prefs.scanConfig(this), new ScanEngine.Listener() {
            @Override public void onStage(String stage) {
                tvStage.setText(stage);
            }
            @Override public void onProgress(int done, int total) {
                progressScan.setProgress(total == 0 ? 0 : (int) (done * 100L / total));
            }
            @Override public void onDevice(Device device) {
                liveDevices.put(device.ip, device);
                updateLiveStats();
                ui.removeCallbacks(renderRunnable);
                ui.postDelayed(renderRunnable, 120);
            }
            @Override public void onFinished(ScanReport report) {
                finishNetworkScan(report);
            }
            @Override public void onError(String message) {
                scanning = false;
                radar.setScanning(false);
                setScanningUi(false);
                tvStage.setText(message);
                toast(message);
                loadNetworkInfo();
            }
        });
    }

    private void finishNetworkScan(ScanReport report) {
        scanning = false;
        currentReport = report;
        liveDevices.clear();
        for (Device device : report.devices) liveDevices.put(device.ip, device);
        renderDevices();
        updateStats(report);
        radar.setScanning(false);
        radar.setDeviceCount(report.devices.size());
        progressScan.setProgress(100);
        setScanningUi(false);
        loadNetworkInfo();

        if (report.cancelled) {
            tvStage.setText(R.string.scan_cancelled_short);
        } else {
            tvStage.setText(getString(R.string.device_summary, report.devices.size(),
                    report.totalOpenPorts(), Formats.duration(report.durationMs)));
            toast(getString(R.string.scan_complete));
        }
        saveReport(report);
    }

    private void saveReport(final ScanReport report) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    repository.saveReport(report);
                    final ScanReport previous = repository.previousReport(report);
                    final ScanDiff diff = previous == null ? null : ScanDiffEngine.compare(report, previous);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            loadHistory();
                            if (diff != null && !diff.isEmpty()) {
                                toast(getString(R.string.diff_summary, diff.addedIps.size(),
                                        diff.removedIps.size(), diff.portChanges.size()));
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }, "netscope-save").start();
    }

    private void setScanningUi(boolean active) {
        btnScan.setVisibility(active ? View.GONE : View.VISIBLE);
        btnStop.setVisibility(active ? View.VISIBLE : View.GONE);
        progressScan.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void updateLiveStats() {
        int ports = 0;
        for (Device device : liveDevices.values()) ports += device.openPortCount();
        tvStatDevices.setText(String.valueOf(liveDevices.size()));
        tvStatPorts.setText(String.valueOf(ports));
        tvStatTime.setText("—");
        radar.setDeviceCount(liveDevices.size());
    }

    private void updateStats(ScanReport report) {
        if (report == null) {
            tvStatDevices.setText("0");
            tvStatPorts.setText("0");
            tvStatTime.setText("—");
            tvDeviceCount.setText("0");
            return;
        }
        tvStatDevices.setText(String.valueOf(report.devices.size()));
        tvStatPorts.setText(String.valueOf(report.totalOpenPorts()));
        tvStatTime.setText(Formats.duration(report.durationMs));
        tvDeviceCount.setText(String.valueOf(report.devices.size()));
    }

    private void renderDevices() {
        List<Device> sorted = new ArrayList<>(liveDevices.values());
        Collections.sort(sorted, new Comparator<Device>() {
            @Override public int compare(Device a, Device b) {
                return Long.compare(SubnetMath.ipToLong(a.ip), SubnetMath.ipToLong(b.ip));
            }
        });
        deviceAdapter.setItems(sorted);
        tvDeviceCount.setText(String.valueOf(sorted.size()));
        emptyState.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPage(int index) {
        pageRed.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageHistory.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        int active = getColor(R.color.primary);
        int inactive = getColor(R.color.text_dim);
        tvNavRed.setTextColor(index == 0 ? active : inactive);
        tvNavHistory.setTextColor(index == 1 ? active : inactive);
        tvNavSettings.setTextColor(index == 2 ? active : inactive);
        if (index == 1) loadHistory();
    }

    private void loadHistory() {
        List<ScanRepository.ScanSummary> scans = repository.listScans();
        historyAdapter.setItems(scans);
        emptyHistory.setVisibility(scans.isEmpty() ? View.VISIBLE : View.GONE);
        btnExportLastHistory.setEnabled(controller.getLastReport() != null || !scans.isEmpty());
    }

    private void showHistory(ScanRepository.ScanSummary summary) {
        ScanReport report = repository.loadReport(summary.id);
        if (report == null) return;
        controller.setLastReport(report);
        currentReport = report;
        liveDevices.clear();
        for (Device device : report.devices) liveDevices.put(device.ip, device);
        renderDevices();
        updateStats(report);
        showPage(0);
        tvStage.setText(getString(R.string.history_detail_line, Formats.time(report.timestamp)));

        ScanReport previous = repository.previousReport(report);
        if (previous != null) {
            ScanDiff diff = ScanDiffEngine.compare(report, previous);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.diff_title)
                    .setMessage(diff.toText())
                    .setPositiveButton(R.string.close, null)
                    .show();
        }
    }

    private void exportCurrent() {
        final ScanReport report = controller.getLastReport();
        if (report == null) {
            toast(getString(R.string.no_report_yet));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.export_choose)
                .setItems(new CharSequence[]{getString(R.string.export_json), getString(R.string.export_csv)},
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                exportReport(report, which == 0);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void exportReport(final ScanReport report, boolean json) {
        try {
            String content = json ? JsonExporter.export(report) : CsvExporter.export(report);
            String extension = json ? "json" : "csv";
            String mime = json ? "application/json" : "text/csv";
            String name = "NetScope_" + Formats.fileTimestamp(report.timestamp) + "." + extension;
            final DownloadWriter.Saved saved = DownloadWriter.save(this, content, name, mime);
            toast(getString(R.string.export_saved, saved.path));
            if (saved.uri != null) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.export_saved, saved.path))
                        .setPositiveButton(R.string.share_report, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                DownloadWriter.share(MainActivity.this, saved, mime, getString(R.string.app_name));
                            }
                        })
                        .setNegativeButton(R.string.close, null)
                        .show();
            }
        } catch (Exception e) {
            toast(getString(R.string.export_failed, e.getMessage()));
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_history_confirm)
                .setPositiveButton(R.string.clear_history, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        repository.clear();
                        loadHistory();
                        toast(getString(R.string.deleted));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNetworkInfo();
        renderDevices();
        if (currentReport != null) updateStats(currentReport);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionHelper.REQUEST_CODE) return;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                toast(getString(R.string.perm_denied));
                break;
            }
        }
        loadNetworkInfo();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
