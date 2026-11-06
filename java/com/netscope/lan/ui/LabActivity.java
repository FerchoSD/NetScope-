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
import com.netscope.lan.core.net.AdbClient;
import com.netscope.lan.core.net.DistanceEstimator;
import com.netscope.lan.core.net.HttpAudit;
import com.netscope.lan.core.net.NetworkInfoProvider;
import com.netscope.lan.core.net.PortScanner;
import com.netscope.lan.core.net.ScanController;
import com.netscope.lan.core.net.ScanEngine;
import com.netscope.lan.core.net.ServiceCatalog;
import com.netscope.lan.core.net.TargetSpec;
import com.netscope.lan.core.net.WebContentScanner;
import com.netscope.lan.core.net.WifiScanner;
import com.netscope.lan.util.Formats;
import com.netscope.lan.util.Prefs;

import java.net.InetAddress;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LabActivity extends Activity {
    public static final String EXTRA_IP = "lab_ip";
    public static final String EXTRA_PORT = "lab_port";
    public static final String EXTRA_TLS = "lab_tls";

    private static final int[] WEB_PORT_ORDER = {
            443, 8443, 9443, 5001, 80, 8080, 8008, 8081, 8888, 8000, 3000, 5000, 8090, 9000, 10000
    };
    private static final int MAX_OUTPUT = 120000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "netscope-lab");
        thread.setDaemon(true);
        return thread;
    });

    private EditText etIp;
    private EditText etPort;
    private EditText etAdbTarget;
    private EditText etAdbCommand;
    private Switch switchTls;
    private TextView tvLabResolved;
    private Button btnPickDevice;
    private Button btnDiscoverPorts;
    private Button btnAuditHttp;
    private Button btnWordlist;
    private Button btnWifiInventory;
    private Button btnWifiIps;
    private Button btnProximity;
    private Button btnAdbTest;
    private Button btnAdbRun;
    private Button btnLabCancel;
    private ProgressBar progressLab;
    private TextView tvLabOutput;
    private ScrollView scrollLab;

    private ScanController controller;
    private volatile boolean cancelled;
    private volatile int taskToken;
    private volatile WebContentScanner activeScanner;
    private volatile PortScanner activePortScanner;
    private volatile AdbClient activeAdb;

    private interface TargetAction {
        void run(Target target, int token);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab);
        controller = ScanController.get(this);
        bindViews();
        setupActions();

        String ip = getIntent().getStringExtra(EXTRA_IP);
        if (ip != null && !ip.isEmpty()) etIp.setText(ip);
        int port = getIntent().getIntExtra(EXTRA_PORT, -1);
        if (port > 0 && port <= 65535) {
            etPort.setText(String.valueOf(port));
            switchTls.setChecked(getIntent().getBooleanExtra(EXTRA_TLS, HttpAudit.isTlsPort(port)));
        }
        btnWordlist.setText(getString(R.string.lab_wordlist, WebContentScanner.wordlistSize()));
        tvLabResolved.setText(getString(R.string.lab_resolved_empty));
    }

    private void bindViews() {
        etIp = findViewById(R.id.etLabIp);
        etPort = findViewById(R.id.etLabPort);
        etAdbTarget = findViewById(R.id.etAdbTarget);
        etAdbCommand = findViewById(R.id.etAdbCommand);
        switchTls = findViewById(R.id.switchLabTls);
        tvLabResolved = findViewById(R.id.tvLabResolved);
        btnPickDevice = findViewById(R.id.btnPickDevice);
        btnDiscoverPorts = findViewById(R.id.btnDiscoverPorts);
        btnAuditHttp = findViewById(R.id.btnAuditHttp);
        btnWordlist = findViewById(R.id.btnWordlist);
        btnWifiInventory = findViewById(R.id.btnWifiInventory);
        btnWifiIps = findViewById(R.id.btnWifiIps);
        btnProximity = findViewById(R.id.btnProximity);
        btnAdbTest = findViewById(R.id.btnAdbTest);
        btnAdbRun = findViewById(R.id.btnAdbRun);
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
        btnDiscoverPorts.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { discoverPorts(); }
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
        btnWifiIps.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startWifiIpScan(); }
        });
        btnProximity.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startProximity(); }
        });
        btnAdbTest.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runAdb("id"); }
        });
        btnAdbRun.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                runAdb(etAdbCommand.getText().toString().trim());
            }
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
                        tvLabResolved.setText(getString(R.string.lab_resolved, device.ip, device.ip));
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

    private TargetSpec currentSpec() {
        return TargetSpec.parse(etIp.getText().toString(), etPort.getText().toString(),
                switchTls.isChecked());
    }

    private void discoverPorts() {
        final TargetSpec spec;
        try {
            spec = currentSpec();
        } catch (IllegalArgumentException e) {
            toast(getString(R.string.lab_invalid_target));
            return;
        }
        final int token = beginTask(getString(R.string.lab_running_discovery) + "\n"
                + spec.host + "\n\n");
        executor.execute(new Runnable() {
            @Override public void run() {
                final String ip;
                try {
                    ip = InetAddress.getByName(spec.host).getHostAddress();
                } catch (Exception e) {
                    uiPost(token, new Runnable() {
                        @Override public void run() {
                            append(getString(R.string.lab_resolve_error, spec.host));
                            endTask(token);
                        }
                    });
                    return;
                }
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        tvLabResolved.setText(getString(R.string.lab_resolved, spec.host, ip));
                    }
                });
                final List<PortResult> open = discoverPortsFor(ip, token);
                if (cancelled || token != taskToken) return;
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        if (open.isEmpty()) {
                            append(getString(R.string.lab_ports_none, discoveryPorts().length));
                        } else {
                            int chosen = choosePort(open, spec.port);
                            etPort.setText(String.valueOf(chosen));
                            switchTls.setChecked(HttpAudit.isTlsPort(chosen));
                            append(getString(R.string.lab_ports_found, open.size()));
                            append(getString(R.string.lab_port_selected, chosen, portName(chosen)));
                        }
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startAudit() {
        withTarget(getString(R.string.lab_running_audit), new TargetAction() {
            @Override public void run(final Target target, final int token) {
                final HttpAudit.Result result = HttpAudit.audit(target.ip, target.port,
                        target.tls, 1500);
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        append(result.summary());
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startWordlist() {
        withTarget(getString(R.string.lab_running_wordlist), new TargetAction() {
            @Override public void run(final Target target, final int token) {
                final WebContentScanner scanner = new WebContentScanner();
                activeScanner = scanner;
                List<WebContentScanner.Finding> findings = scanner.scan(target.ip, target.port,
                        target.tls, 900, 6, new WebContentScanner.Listener() {
                            @Override public void onFinding(final WebContentScanner.Finding finding) {
                                ui.post(new Runnable() {
                                    @Override public void run() {
                                        if (token != taskToken) return;
                                        StringBuilder line = new StringBuilder();
                                        line.append("· ").append(finding.path)
                                                .append(" → HTTP ").append(finding.status);
                                        if (finding.length >= 0) {
                                            line.append(" · ").append(finding.length).append(" B");
                                        }
                                        if (!finding.location.isEmpty()) {
                                            line.append(" → ").append(finding.location);
                                        }
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
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        activeScanner = null;
                        if (cancelled) {
                            append(getString(R.string.lab_stopped));
                        } else if (count == 0) {
                            append(getString(R.string.lab_wordlist_none));
                        } else {
                            append(getString(R.string.lab_wordlist_done, count,
                                    WebContentScanner.wordlistSize()));
                        }
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startProximity() {
        withTarget(getString(R.string.lab_running_proximity), new TargetAction() {
            @Override public void run(final Target target, final int token) {
                long best = -1;
                for (int attempt = 0; attempt < 3; attempt++) {
                    if (cancelled) break;
                    long start = System.nanoTime();
                    java.net.Socket socket = null;
                    try {
                        socket = new java.net.Socket();
                        socket.connect(new java.net.InetSocketAddress(target.ip, target.port), 1200);
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
                uiPost(token, new Runnable() {
                    @Override public void run() {
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
                NetworkInfoProvider.Snapshot net = NetworkInfoProvider.get(LabActivity.this);
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
                            .append(DistanceEstimator.distanceLabel(current.rssi, current.frequency))
                            .append('\n');
                }
                if (!net.localIp.isEmpty()) {
                    sb.append('\n').append("Direcciones de la red conectada:\n");
                    sb.append("· IP local: ").append(net.localIp).append('\n');
                    sb.append("· Máscara: ").append(net.netmask.isEmpty() ? "—" : net.netmask)
                            .append(" · CIDR ").append(net.cidr()).append('\n');
                    sb.append("· Gateway: ").append(net.gateway.isEmpty() ? "—" : net.gateway).append('\n');
                    sb.append("· DNS: ").append(net.dns.isEmpty() ? "—" : net.dns).append('\n');
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
                sb.append('\n').append(getString(R.string.lab_wifi_other_note));
                final String text = sb.toString().trim();
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        append(text.isEmpty() ? getString(R.string.lab_no_wifi) : text);
                        endTask(token);
                    }
                });
            }
        });
    }

    private void startWifiIpScan() {
        if (controller.isBusy()) {
            toast(getString(R.string.error_busy));
            return;
        }
        final int token = beginTask(getString(R.string.lab_running_wifi_ips) + "\n\n");
        controller.startNetworkScan(Prefs.scanConfig(this), new ScanEngine.Listener() {
            @Override public void onStage(final String stage) {
                uiPost(token, new Runnable() {
                    @Override public void run() { append("· " + stage); }
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
            @Override public void onDevice(final Device device) {
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        String portsLine = Formats.ports(device.openPorts, 12);
                        if (portsLine.isEmpty()) portsLine = getString(R.string.no_open_ports_short);
                        append("· " + device.ip + " · " + device.displayName() + " · " + portsLine);
                    }
                });
            }
            @Override public void onFinished(final ScanReport report) {
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        append(getString(R.string.lab_wifi_ips_done, report.devices.size(),
                                report.totalOpenPorts()));
                        endTask(token);
                    }
                });
            }
            @Override public void onError(final String message) {
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        append(message);
                        endTask(token);
                    }
                });
            }
        });
    }

    private void runAdb(final String command) {
        if (command == null || command.trim().isEmpty()) {
            toast(getString(R.string.lab_adb_empty_command));
            return;
        }
        final String host;
        final int port;
        try {
            TargetSpec spec = TargetSpec.parse(etAdbTarget.getText().toString(), "", false);
            host = spec.host;
            port = spec.hasPort() ? spec.port : 5555;
        } catch (IllegalArgumentException e) {
            toast(getString(R.string.lab_adb_invalid_target));
            return;
        }
        final int token = beginTask(getString(R.string.lab_adb_running) + "\n"
                + host + ":" + port + " → " + command + "\n\n");
        append(getString(R.string.lab_adb_auth_note));
        final AdbClient client = new AdbClient();
        activeAdb = client;
        executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    client.shell(LabActivity.this, host, port, command, 90000,
                            new AdbClient.Listener() {
                                @Override public void onOutput(final String text) {
                                    ui.post(new Runnable() {
                                        @Override public void run() {
                                            if (token != taskToken) return;
                                            appendRaw(text.replace("\r\n", "\n").replace('\r', '\n'));
                                        }
                                    });
                                }
                            });
                    uiPost(token, new Runnable() {
                        @Override public void run() {
                            appendRaw("\n");
                            append(getString(R.string.lab_adb_done));
                            endTask(token);
                        }
                    });
                } catch (Exception e) {
                    final String message = e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage();
                    uiPost(token, new Runnable() {
                        @Override public void run() {
                            append(getString(R.string.lab_adb_error, message));
                            endTask(token);
                        }
                    });
                } finally {
                    activeAdb = null;
                }
            }
        });
    }

    private void withTarget(final String header, final TargetAction action) {
        final TargetSpec spec;
        try {
            spec = currentSpec();
        } catch (IllegalArgumentException e) {
            toast(getString(R.string.lab_invalid_target));
            return;
        }
        final String label = spec.host
                + (spec.hasPort() ? ":" + spec.port : " · puerto automático")
                + (spec.tls ? " · TLS" : "");
        final int token = beginTask(header + "\n" + label + "\n\n");
        executor.execute(new Runnable() {
            @Override public void run() {
                final String ip;
                try {
                    ip = InetAddress.getByName(spec.host).getHostAddress();
                } catch (Exception e) {
                    uiPost(token, new Runnable() {
                        @Override public void run() {
                            append(getString(R.string.lab_resolve_error, spec.host));
                            endTask(token);
                        }
                    });
                    return;
                }
                uiPost(token, new Runnable() {
                    @Override public void run() {
                        tvLabResolved.setText(getString(R.string.lab_resolved, spec.host, ip));
                    }
                });

                int port = spec.port;
                boolean tls = spec.tls;
                if (port < 0) {
                    appendFromWorker(token, getString(R.string.lab_discovering_ports));
                    List<PortResult> open = discoverPortsFor(ip, token);
                    if (cancelled || token != taskToken) return;
                    if (!open.isEmpty()) {
                        port = choosePort(open, -1);
                        tls = HttpAudit.isTlsPort(port);
                        final int chosen = port;
                        final boolean chosenTls = tls;
                        uiPost(token, new Runnable() {
                            @Override public void run() {
                                etPort.setText(String.valueOf(chosen));
                                switchTls.setChecked(chosenTls);
                                append(getString(R.string.lab_port_selected, chosen, portName(chosen)));
                            }
                        });
                    }
                }
                if (port < 0) {
                    final int scanned = discoveryPorts().length;
                    uiPost(token, new Runnable() {
                        @Override public void run() {
                            append(getString(R.string.lab_ports_none, scanned));
                            endTask(token);
                        }
                    });
                    return;
                }
                action.run(new Target(spec.host, ip, port, tls), token);
            }
        });
    }

    private List<PortResult> discoverPortsFor(String ip, final int token) {
        final int[] ports = discoveryPorts();
        PortScanner scanner = new PortScanner();
        activePortScanner = scanner;
        try {
            return scanner.scan(ip, ports, 900, 32, true, new PortScanner.Listener() {
                @Override public void onOpen(final PortResult result) {
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (token != taskToken) return;
                            StringBuilder line = new StringBuilder();
                            line.append("· ").append(result.port).append("/tcp");
                            if (!result.service.isEmpty()) line.append(" ").append(result.service);
                            if (result.risk == 2) line.append(" · ").append(getString(R.string.risk_high));
                            else if (result.risk == 1) line.append(" · ").append(getString(R.string.risk_medium));
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
        } finally {
            activePortScanner = null;
        }
    }

    private static int[] discoveryPorts() {
        TreeSet<Integer> ports = new TreeSet<>();
        for (int port : ServiceCatalog.commonPorts()) ports.add(port);
        for (int port : ServiceCatalog.webPorts()) ports.add(port);
        int[] out = new int[ports.size()];
        int index = 0;
        for (Integer port : ports) out[index++] = port;
        return out;
    }

    private static int choosePort(List<PortResult> open, int preferred) {
        for (PortResult result : open) {
            if (preferred > 0 && result.port == preferred) return preferred;
        }
        for (int port : WEB_PORT_ORDER) {
            for (PortResult result : open) {
                if (result.port == port) return port;
            }
        }
        for (PortResult result : open) {
            if (ServiceCatalog.isHttp(result.port)) return result.port;
        }
        return open.get(0).port;
    }

    private static String portName(int port) {
        String name = ServiceCatalog.name(port);
        return name.isEmpty() ? "TCP" : name;
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
        activePortScanner = null;
        activeAdb = null;
        progressLab.setProgress(100);
        setRunning(false);
    }

    private void cancelTask() {
        cancelled = true;
        taskToken++;
        WebContentScanner scanner = activeScanner;
        if (scanner != null) scanner.cancel();
        PortScanner ports = activePortScanner;
        if (ports != null) ports.cancel();
        AdbClient adb = activeAdb;
        if (adb != null) adb.cancel();
        if (controller.isBusy()) controller.cancel();
        activeScanner = null;
        activePortScanner = null;
        activeAdb = null;
        append(getString(R.string.lab_stopped));
        progressLab.setProgress(0);
        setRunning(false);
    }

    private void setRunning(boolean active) {
        etIp.setEnabled(!active);
        etPort.setEnabled(!active);
        switchTls.setEnabled(!active);
        etAdbTarget.setEnabled(!active);
        etAdbCommand.setEnabled(!active);
        btnPickDevice.setEnabled(!active);
        btnDiscoverPorts.setEnabled(!active);
        btnAuditHttp.setEnabled(!active);
        btnWordlist.setEnabled(!active);
        btnWifiInventory.setEnabled(!active);
        btnWifiIps.setEnabled(!active);
        btnProximity.setEnabled(!active);
        btnAdbTest.setEnabled(!active);
        btnAdbRun.setEnabled(!active);
        btnLabCancel.setVisibility(active ? View.VISIBLE : View.GONE);
        btnLabCancel.setEnabled(active);
        progressLab.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void uiPost(final int token, final Runnable action) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (token == taskToken) action.run();
            }
        });
    }

    private void appendFromWorker(final int token, final String text) {
        uiPost(token, new Runnable() {
            @Override public void run() { append(text); }
        });
    }

    private void append(String text) {
        appendRaw(text.endsWith("\n") ? text : text + "\n");
    }

    private void appendRaw(String text) {
        if (text == null || text.isEmpty()) return;
        tvLabOutput.append(text);
        CharSequence content = tvLabOutput.getText();
        int length = content.length();
        if (length > MAX_OUTPUT) {
            tvLabOutput.setText(content.subSequence(length - MAX_OUTPUT, length));
        }
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
        PortScanner ports = activePortScanner;
        if (ports != null) ports.cancel();
        AdbClient adb = activeAdb;
        if (adb != null) adb.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class Target {
        final String host;
        final String ip;
        final int port;
        final boolean tls;

        Target(String host, String ip, int port, boolean tls) {
            this.host = host;
            this.ip = ip;
            this.port = port;
            this.tls = tls;
        }
    }
}
