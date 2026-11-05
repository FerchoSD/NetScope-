package com.netscope.lan.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.netscope.lan.R;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;
import com.netscope.lan.core.net.DistanceEstimator;
import com.netscope.lan.core.net.HttpAudit;
import com.netscope.lan.core.net.ScanController;
import com.netscope.lan.core.net.ServiceCatalog;
import com.netscope.lan.core.net.WebContentScanner;
import com.netscope.lan.core.net.WifiScanner;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LabActivity extends Activity {
    public static final String EXTRA_IP = "lab_ip";
    public static final String EXTRA_PORT = "lab_port";
    public static final String EXTRA_TLS = "lab_tls";

    private static final int[] WEB_PORT_ORDER = {
            443, 8443, 9443, 80, 8080, 8008, 8081, 8888, 8000, 3000, 5000, 8090, 9000, 10000
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "netscope-lab");
        thread.setDaemon(true);
        return thread;
    });

    private EditText etIp;
    private EditText etPort;
    private Switch switchTls;
    private Button btnPickDevice;
    private Button btnAuditHttp;
    private Button btnWordlist;
    private Button btnWifiInventory;
    private Button btnProximity;
    private Button btnLabCancel;
    private ProgressBar progressLab;
    private TextView tvLabOutput;
    private ScrollView scrollLab;

    private ScanController controller;
    private volatile boolean cancelled;
    private volatile WebContentScanner activeScanner;
    private int taskToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab);
        controller = ScanController.get(this);
        bindViews();
        setupActions();

        String ip = getIntent().getStringExtra(EXTRA_IP);
        if (ip != null && !ip.isEmpty()) etIp.setText(ip);
        int port = getIntent().getIntExtra(EXTRA_PORT, 80);
        if (port <= 0 || port > 65535) port = 80;
        etPort.setText(String.valueOf(port));
        switchTls.setChecked(getIntent().getBooleanExtra(EXTRA_TLS, HttpAudit.isTlsPort(port)));
        btnWordlist.setText(getString(R.string.lab_wordlist, WebContentScanner.wordlistSize()));
    }

    private void bindViews() {
        etIp = findViewById(R.id.etLabIp);
        etPort = findViewById(R.id.etLabPort);
        switchTls = findViewById(R.id.switchLabTls);
        btnPickDevice = findViewById(R.id.btnPickDevice);
        btnAuditHttp = findViewById(R.id.btnAuditHttp);
        btnWordlist = findViewById(R.id.btnWordlist);
        btnWifiInventory = findViewById(R.id.btnWifiInventory);
        btnProximity = findViewById(R.id.btnProximity);
        btnLabCancel = findViewById(R.id.btnLabCancel);
        progressLab = findViewById(R.id.progressLab);
        tvLabOutput = findViewById(R.id.tvLabOutput);
        scrollLab = findViewById(R.id.scrollLab);
    }

    private void setupActions() {
        findViewById(R.id.btnLabBack).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        btnPickDevice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDevice(); }
        });
        btnAuditHttp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startAudit(); }
        });
        btnWordlist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startWordlist(); }
        });
        btnWifiInventory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startWifiInventory(); }
        });
        btnProximity.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startProximity(); }
        });
        btnLabCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancelTask(); }
        });
    }

    private void pickDevice() {
        ScanReport report = controller.getLastReport();
        if (report == null || report.devices.isEmpty()) {
            toast(getString(R.string.lab_no_devices));
            return;
        }
        final List<Device> devices = report.devices;
        CharSequence[] labels = new CharSequence[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            labels[i] = device.displayName() + " · " + device.ip;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.lab_pick_device)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Device device = devices.get(which);
                        etIp.setText(device.ip);
                        int port = preferredPort(device);
                        etPort.setText(String.valueOf(port));
                        switchTls.setChecked(HttpAudit.isTlsPort(port));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static int preferredPort(Device device) {
        for (int port : WEB_PORT_ORDER) {
            if (device.hasOpenPort(port)) return port;
        }
        for (PortResult result : device.openPorts) {
            if (ServiceCatalog.isHttp(result.port)) return result.port;
        }
        return 80;
    }

    private Target target() {
        String ip = etIp.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(etPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (ip.isEmpty() || ip.contains(" ") || port < 1 || port > 65535) {
            toast(getString(R.string.lab_invalid_target));
            return null;
        }
        return new Target(ip, port, switchTls.isChecked());
    }

    private void startAudit() {
        final Target target = target();
        if (target == null) return;
        final int token = beginTask(getString(R.string.lab_running_audit) + "\n" + target.label() + "\n\n");
        executor.execute(new Runnable() {
            @Override public void run() {
                final HttpAudit.Result result = HttpAudit.audit(target.ip, target.port, target.tls, 1500);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (token != taskToken) return;
                        append(result.summary());
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startWordlist() {
        final Target target = target();
        if (target == null) return;
        final int token = beginTask(getString(R.string.lab_running_wordlist) + "\n" + target.label() + "\n\n");
        final WebContentScanner scanner = new WebContentScanner();
        activeScanner = scanner;
        executor.execute(new Runnable() {
            @Override public void run() {
                List<WebContentScanner.Finding> findings = scanner.scan(target.ip, target.port,
                        target.tls, 900, 6, new WebContentScanner.Listener() {
                            @Override public void onFinding(final WebContentScanner.Finding finding) {
                                ui.post(new Runnable() {
                                    @Override public void run() {
                                        if (token != taskToken) return;
                                        StringBuilder line = new StringBuilder();
                                        line.append("· ").append(finding.path).append(" → HTTP ").append(finding.status);
                                        if (finding.length >= 0) line.append(" · ").append(finding.length).append(" B");
                                        if (!finding.location.isEmpty()) line.append(" → ").append(finding.location);
                                        append(line.toString());
                                    }
                                });
                            }
                            @Override public void onProgress(final int done, final int total) {
                                ui.post(new Runnable() {
                                    @Override public void run() {
                                        if (token != taskToken) return;
                                        progressLab.setProgress(total == 0 ? 0 : done * 100 / total);
                                    }
                                });
                            }
                        });
                final int count = findings.size();
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (token != taskToken) return;
                        activeScanner = null;
                        if (cancelled) {
                            append(getString(R.string.lab_stopped));
                        } else if (count == 0) {
                            append(getString(R.string.lab_wordlist_none));
                        } else {
                            append(getString(R.string.lab_wordlist_done, count, WebContentScanner.wordlistSize()));
                        }
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startProximity() {
        final Target target = target();
        if (target == null) return;
        final int token = beginTask(getString(R.string.lab_running_proximity) + "\n" + target.label() + "\n\n");
        executor.execute(new Runnable() {
            @Override public void run() {
                long best = -1;
                for (int attempt = 0; attempt < 3; attempt++) {
                    if (cancelled) break;
                    long start = System.nanoTime();
                    Socket socket = null;
                    try {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(target.ip, target.port), 1200);
                        long elapsed = (System.nanoTime() - start) / 1000000L;
                        if (best < 0 || elapsed < best) best = elapsed;
                    } catch (Exception ignored) {
                    } finally {
                        if (socket != null) {
                            try { socket.close(); } catch (Exception ignored) {}
                        }
                    }
                }
                final long rtt = best;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (token != taskToken) return;
                        if (rtt < 0) {
                            append("No se pudo abrir una conexión TCP al objetivo.");
                        } else {
                            append(getString(R.string.lab_proximity_result) + ": "
                                    + DistanceEstimator.proximityLabel(rtt) + " (" + rtt + " ms)");
                        }
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startWifiInventory() {
        final int token = beginTask(getString(R.string.lab_running_wifi) + "\n\n");
        executor.execute(new Runnable() {
            @Override public void run() {
                WifiScanner.Network current = WifiScanner.connected(LabActivity.this);
                List<WifiScanner.Network> networks = WifiScanner.results(LabActivity.this);
                StringBuilder sb = new StringBuilder();
                if (current == null || current.ssid.isEmpty()) {
                    sb.append(getString(R.string.lab_no_wifi)).append('\n');
                } else {
                    sb.append("Red conectada: ").append(current.ssid).append('\n');
                    if (!current.bssid.isEmpty()) sb.append("BSSID: ").append(current.bssid).append('\n');
                    sb.append("Banda: ").append(current.band.isEmpty() ? "—" : current.band)
                            .append(" · canal ")
                            .append(current.channel > 0 ? String.valueOf(current.channel) : "—").append('\n');
                    sb.append("Señal: ").append(current.signalPercent).append("% (")
                            .append(current.rssi).append(" dBm)\n");
                    sb.append(getString(R.string.lab_distance_ap)).append(": ")
                            .append(DistanceEstimator.distanceLabel(current.rssi, current.frequency)).append('\n');
                }
                if (!networks.isEmpty()) {
                    sb.append('\n').append("Redes cercanas (").append(networks.size()).append("):\n");
                    int shown = 0;
                    for (WifiScanner.Network network : networks) {
                        if (shown++ >= 12) {
                            sb.append("…\n");
                            break;
                        }
                        sb.append("· ").append(network.ssid)
                                .append(" · ").append(network.band.isEmpty() ? "—" : network.band)
                                .append(" · canal ")
                                .append(network.channel > 0 ? String.valueOf(network.channel) : "—")
                                .append(" · ").append(network.rssi).append(" dBm")
                                .append(" · ").append(DistanceEstimator.distanceLabel(network.rssi, network.frequency))
                                .append(" · ").append(network.security).append('\n');
                    }
                }
                final String text = sb.toString().trim();
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (token != taskToken) return;
                        append(text.isEmpty() ? getString(R.string.lab_no_wifi) : text);
                        endTask(token);
                    }
                });
            }
        });
    }

    private int beginTask(String header) {
        taskToken++;
        cancelled = false;
        setRunning(true);
        progressLab.setProgress(3);
        tvLabOutput.setText(header);
        scrollBottom();
        return taskToken;
    }

    private void endTask(int token) {
        if (token != taskToken) return;
        activeScanner = null;
        progressLab.setProgress(100);
        setRunning(false);
    }

    private void cancelTask() {
        cancelled = true;
        taskToken++;
        WebContentScanner scanner = activeScanner;
        if (scanner != null) scanner.cancel();
        activeScanner = null;
        append(getString(R.string.lab_stopped));
        progressLab.setProgress(0);
        setRunning(false);
    }

    private void setRunning(boolean active) {
        btnPickDevice.setEnabled(!active);
        btnAuditHttp.setEnabled(!active);
        btnWordlist.setEnabled(!active);
        btnWifiInventory.setEnabled(!active);
        btnProximity.setEnabled(!active);
        btnLabCancel.setVisibility(active ? View.VISIBLE : View.GONE);
        btnLabCancel.setEnabled(active);
        progressLab.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void append(String text) {
        tvLabOutput.append(text.endsWith("\n") ? text : text + "\n");
        scrollBottom();
    }

    private void scrollBottom() {
        scrollLab.post(new Runnable() {
            @Override public void run() { scrollLab.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        taskToken++;
        WebContentScanner scanner = activeScanner;
        if (scanner != null) scanner.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class Target {
        final String ip;
        final int port;
        final boolean tls;

        Target(String ip, int port, boolean tls) {
            this.ip = ip;
            this.port = port;
            this.tls = tls;
        }

        String label() {
            return ip + ":" + port + (tls ? " · TLS" : "");
        }
    }
}
