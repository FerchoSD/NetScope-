package com.netscope.lan.core.net;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public final class ServiceCatalog {
    private static final Map<Integer, String> SERVICES = new HashMap<>();
    private static final Map<Integer, Integer> RISK = new HashMap<>();
    private static final Map<Integer, String> RISK_NOTE = new HashMap<>();

    private ServiceCatalog() {}

    private static void s(int port, String name) { SERVICES.put(port, name); }

    static {
        s(20, "FTP-DATA"); s(21, "FTP"); s(22, "SSH"); s(23, "Telnet"); s(25, "SMTP");
        s(53, "DNS"); s(67, "DHCP"); s(68, "DHCP"); s(69, "TFTP"); s(80, "HTTP");
        s(88, "Kerberos"); s(110, "POP3"); s(111, "rpcbind"); s(123, "NTP");
        s(135, "MSRPC"); s(137, "NetBIOS-NS"); s(138, "NetBIOS-DGM"); s(139, "NetBIOS");
        s(143, "IMAP"); s(161, "SNMP"); s(162, "SNMP-trap"); s(179, "BGP");
        s(389, "LDAP"); s(443, "HTTPS"); s(445, "SMB"); s(465, "SMTPS");
        s(500, "IKE"); s(512, "rexec"); s(513, "rlogin"); s(514, "Syslog/rsh");
        s(515, "LPD"); s(548, "AFP"); s(554, "RTSP"); s(587, "SMTP"); s(623, "IPMI");
        s(631, "IPP"); s(636, "LDAPS"); s(873, "rsync"); s(993, "IMAPS");
        s(995, "POP3S"); s(1080, "SOCKS"); s(1433, "MSSQL"); s(1521, "Oracle");
        s(1723, "PPTP"); s(1883, "MQTT"); s(1900, "SSDP"); s(2049, "NFS");
        s(2082, "cPanel"); s(2083, "cPanel SSL"); s(2181, "ZooKeeper");
        s(2222, "SSH alt"); s(2375, "Docker"); s(2376, "Docker TLS");
        s(3000, "HTTP dev"); s(3128, "Squid"); s(3260, "iSCSI"); s(3306, "MySQL");
        s(3389, "RDP"); s(3478, "STUN"); s(3690, "SVN"); s(4000, "HTTP dev");
        s(4443, "HTTPS alt"); s(4505, "Salt"); s(4506, "Salt"); s(5000, "HTTP dev");
        s(5001, "HTTPS dev"); s(5060, "SIP"); s(5061, "SIP TLS"); s(5222, "XMPP");
        s(5353, "mDNS"); s(5432, "PostgreSQL"); s(5555, "ADB"); s(5601, "Kibana");
        s(5672, "RabbitMQ"); s(5900, "VNC"); s(5984, "CouchDB"); s(6000, "X11");
        s(6379, "Redis"); s(6443, "Kubernetes API"); s(7001, "WebLogic");
        s(8000, "HTTP alt"); s(8008, "HTTP alt"); s(8009, "AJP"); s(8080, "HTTP proxy");
        s(8081, "HTTP alt"); s(8086, "InfluxDB"); s(8088, "HTTP alt"); s(8090, "HTTP alt");
        s(8123, "HTTP alt"); s(8200, "Vault"); s(8443, "HTTPS alt"); s(8554, "RTSP alt");
        s(8888, "HTTP alt"); s(9000, "PHP-FPM/MinIO"); s(9001, "MinIO");
        s(9042, "Cassandra"); s(9090, "Prometheus"); s(9092, "Kafka");
        s(9100, "JetDirect/Node"); s(9200, "Elasticsearch"); s(9300, "Elasticsearch");
        s(9443, "HTTPS alt"); s(10000, "Webmin"); s(11211, "Memcached");
        s(15672, "RabbitMQ UI"); s(27017, "MongoDB"); s(32400, "Plex");
        s(50000, "HTTP alt"); s(50070, "Hadoop"); s(62078, "iPhone sync");

        riskHigh(21, "FTP sin cifrar: credenciales expuestas");
        riskHigh(23, "Telnet sin cifrar: acceso remoto inseguro");
        riskHigh(69, "TFTP sin autenticación");
        riskHigh(111, "rpcbind expuesto: superficie de ataque");
        riskHigh(135, "MSRPC expuesto");
        riskHigh(139, "NetBIOS expuesto: enumeración de recursos");
        riskHigh(445, "SMB expuesto: riesgo de ransomware");
        riskHigh(512, "rexec sin cifrar");
        riskHigh(513, "rlogin sin cifrar");
        riskHigh(514, "rsh sin cifrar");
        riskHigh(623, "IPMI expuesto: gestión fuera de banda");
        riskHigh(1433, "Base de datos MSSQL expuesta");
        riskHigh(1521, "Base de datos Oracle expuesta");
        riskHigh(2049, "NFS expuesto sin cifrar");
        riskHigh(2375, "Docker API sin TLS: control total del host");
        riskHigh(3306, "Base de datos MySQL expuesta");
        riskHigh(3389, "RDP expuesto: objetivo habitual de fuerza bruta");
        riskHigh(5432, "Base de datos PostgreSQL expuesta");
        riskHigh(5555, "ADB expuesto: control del dispositivo");
        riskHigh(5900, "VNC expuesto: escritorio remoto");
        riskHigh(6379, "Redis expuesto sin autenticación habitual");
        riskHigh(9200, "Elasticsearch expuesto sin autenticación habitual");
        riskHigh(11211, "Memcached expuesto: amplificación DDoS");
        riskHigh(27017, "MongoDB expuesto");
        riskMedium(80, "Servicio web HTTP sin cifrar");
        riskMedium(161, "SNMP: revisa la comunidad configurada");
        riskMedium(389, "LDAP sin cifrar");
        riskMedium(1883, "MQTT: revisa autenticación");
        riskMedium(2376, "Docker TLS: verifica certificados");
        riskMedium(3000, "Servicio de desarrollo expuesto");
        riskMedium(5000, "Servicio de desarrollo expuesto");
        riskMedium(6443, "API de Kubernetes expuesta");
        riskMedium(8000, "Servicio HTTP expuesto");
        riskMedium(8080, "Proxy/servicio HTTP expuesto");
        riskMedium(8443, "Panel HTTPS expuesto");
        riskMedium(8888, "Servicio HTTP expuesto");
        riskMedium(9000, "Panel de gestión expuesto");
        riskMedium(10000, "Webmin expuesto");
        riskMedium(15672, "Panel de RabbitMQ expuesto");
    }

    private static void riskHigh(int port, String note) { RISK.put(port, 2); RISK_NOTE.put(port, note); }
    private static void riskMedium(int port, String note) { RISK.put(port, 1); RISK_NOTE.put(port, note); }

    public static String name(int port) {
        String v = SERVICES.get(port);
        return v == null ? "" : v;
    }

    public static int risk(int port) {
        Integer v = RISK.get(port);
        return v == null ? 0 : v;
    }

    public static String riskNote(int port) {
        String v = RISK_NOTE.get(port);
        return v == null ? "" : v;
    }

    public static boolean isHttp(int port) {
        return port == 80 || port == 443 || port == 3000 || port == 5000 || port == 8000
                || port == 8008 || port == 8080 || port == 8081 || port == 8088 || port == 8090
                || port == 8443 || port == 8888 || port == 9000 || port == 9443 || port == 10000;
    }

    public static int[] quickPorts() {
        return new int[]{21, 22, 23, 25, 53, 80, 139, 443, 445, 554, 631, 3389,
                5000, 5900, 8008, 8080, 8443, 8888, 9100, 62078};
    }

    public static int[] commonPorts() {
        return new int[]{21, 22, 23, 25, 53, 67, 68, 69, 80, 88, 110, 111, 123, 135, 137,
                138, 139, 143, 161, 162, 179, 389, 443, 445, 465, 500, 512, 513, 514, 515,
                548, 554, 587, 623, 631, 636, 873, 993, 995, 1080, 1433, 1521, 1723, 1883,
                1900, 2049, 2082, 2083, 2181, 2222, 2375, 2376, 3000, 3128, 3260, 3306, 3389,
                3478, 3690, 4000, 4443, 4505, 4506, 5000, 5001, 5060, 5061, 5222, 5353, 5432,
                5555, 5601, 5672, 5900, 5984, 6000, 6379, 6443, 7001, 8000, 8008, 8009, 8080,
                8081, 8086, 8088, 8090, 8123, 8200, 8443, 8554, 8888, 9000, 9001, 9042, 9090,
                9092, 9100, 9200, 9300, 9443, 10000, 11211, 15672, 27017, 32400, 50000, 50070,
                62078};
    }

    public static int[] webPorts() {
        return new int[]{80, 443, 3000, 5000, 8000, 8008, 8080, 8081, 8088, 8090, 8443, 8888, 9000, 9443, 10000};
    }

    public static int[] fullPorts() {
        int[] ports = new int[65535];
        for (int i = 0; i < 65535; i++) ports[i] = i + 1;
        return ports;
    }

    public static int[] parsePorts(String text) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("vacío");
        TreeSet<Integer> set = new TreeSet<>();
        String[] tokens = text.trim().split("[,\\s]+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            try {
                if (dash > 0) {
                    int a = Integer.parseInt(token.substring(0, dash).trim());
                    int b = Integer.parseInt(token.substring(dash + 1).trim());
                    if (a < 1 || b > 65535 || a > b) throw new IllegalArgumentException("rango");
                    for (int p = a; p <= b; p++) set.add(p);
                } else {
                    int p = Integer.parseInt(token);
                    if (p < 1 || p > 65535) throw new IllegalArgumentException("puerto");
                    set.add(p);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("formato");
            }
        }
        if (set.isEmpty()) throw new IllegalArgumentException("vacío");
        int[] out = new int[set.size()];
        int i = 0;
        for (Integer p : set) out[i++] = p;
        return out;
    }
}
