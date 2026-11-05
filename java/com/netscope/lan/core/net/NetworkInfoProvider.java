package com.netscope.lan.core.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

public final class NetworkInfoProvider {
    private NetworkInfoProvider() {}

    public static final class Snapshot {
        public boolean available;
        public boolean scannable;
        public String type = "Sin conexión";
        public String ssid = "";
        public String localIp = "";
        public String netmask = "";
        public String gateway = "";
        public String dns = "";
        public String iface = "";
        public int prefix = 24;

        public String cidr() {
            return localIp.isEmpty() ? "" : SubnetMath.cidr(localIp, prefix);
        }
    }

    public static Snapshot get(Context context) {
        Snapshot snapshot = new Snapshot();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return snapshot;
            Network network = cm.getActiveNetwork();
            if (network == null) return snapshot;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null) return snapshot;

            boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            boolean cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
            snapshot.type = wifi ? "Wi-Fi" : ethernet ? "Ethernet" : cellular ? "Móvil" : "Otra";

            LinkProperties props = cm.getLinkProperties(network);
            if (props != null) {
                snapshot.iface = props.getInterfaceName() == null ? "" : props.getInterfaceName();
                List<LinkAddress> addresses = props.getLinkAddresses();
                for (LinkAddress la : addresses) {
                    InetAddress address = la.getAddress();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        snapshot.localIp = address.getHostAddress();
                        int prefix = la.getPrefixLength();
                        snapshot.prefix = prefix > 0 && prefix <= 32 ? prefix : 24;
                        break;
                    }
                }
                for (RouteInfo route : props.getRoutes()) {
                    if (route.isDefaultRoute() && route.getGateway() instanceof Inet4Address) {
                        snapshot.gateway = route.getGateway().getHostAddress();
                        break;
                    }
                }
                StringBuilder dns = new StringBuilder();
                for (InetAddress server : props.getDnsServers()) {
                    if (dns.length() > 0) dns.append(", ");
                    dns.append(server.getHostAddress());
                }
                snapshot.dns = dns.toString();
            }

            snapshot.available = !snapshot.localIp.isEmpty();
            snapshot.scannable = (wifi || ethernet) && snapshot.available
                    && SubnetMath.isPrivate(snapshot.localIp);
            snapshot.netmask = SubnetMath.prefixToMask(snapshot.prefix);

            if (wifi) {
                try {
                    WifiManager wm = (WifiManager) context.getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        WifiInfo info = wm.getConnectionInfo();
                        if (info != null) {
                            String ssid = info.getSSID();
                            if (ssid != null) {
                                ssid = ssid.replace("\"", "");
                                if (!ssid.isEmpty() && !ssid.contains("<unknown")) snapshot.ssid = ssid;
                            }
                        }
                    }
                } catch (SecurityException ignored) {
                    // Android 13: el SSID requiere ubicación; se mostrará "Red oculta".
                }
                if (snapshot.ssid.isEmpty()) snapshot.ssid = "Red oculta";
            } else if (ethernet) {
                snapshot.ssid = "Ethernet";
            } else if (cellular) {
                snapshot.ssid = "Datos móviles";
            }
        } catch (Exception ignored) {
            // Sin conectividad o permisos insuficientes.
        }
        return snapshot;
    }
}
