package com.lan.network_management.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.util.Enumeration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkUtils {

    public static class InterfaceInfo {
        private final String name;
        private final String displayName;
        private final String ipv4Address;
        private final int prefixLength;

        public InterfaceInfo(String name, String displayName, String ipv4Address, int prefixLength) {
            this.name = name;
            this.displayName = displayName;
            this.ipv4Address = ipv4Address;
            this.prefixLength = prefixLength;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public String getIpv4Address() { return ipv4Address; }
        public int getPrefixLength() { return prefixLength; }

        @Override
        public String toString() {
            return (displayName != null ? displayName : name) + (ipv4Address != null ? (" - " + ipv4Address + "/" + prefixLength) : "");
        }
    }

    public static String getLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress inet = addr.getAddress();
                    if (inet.isLoopbackAddress() || inet.isLinkLocalAddress() || !(inet instanceof java.net.Inet4Address)) {
                        continue;
                    }

                    String ip = inet.getHostAddress();
                    int prefixLength = addr.getNetworkPrefixLength(); // e.g., 24
                    String[] octets = ip.split("\\.");
                    // Only handle /24 subnets for simplicity
                    if (prefixLength == 24) {
                        return octets[0] + "." + octets[1] + "." + octets[2];
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDefaultGateway() {
        try {
            Process process = new ProcessBuilder("ipconfig", "/all").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                Pattern ipv4Pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
                boolean expectingContinuationForGateway = false;

                while ((line = reader.readLine()) != null) {
                    // Direct match on the same line (IPv4 shown directly)
                    if (line.contains("Default Gateway")) {
                        Matcher sameLineIpv4 = ipv4Pattern.matcher(line);
                        if (sameLineIpv4.find()) {
                            return sameLineIpv4.group(1);
                        }
                        // No IPv4 on the same line. Windows often prints IPv6 first
                        // on the header line and then an indented IPv4 on the next line(s).
                        expectingContinuationForGateway = true;
                        continue;
                    }

                    if (expectingContinuationForGateway) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                            // Blank line: end of this adapter section
                            expectingContinuationForGateway = false;
                            continue;
                        }
                        // Only consider indented continuation lines; a new field likely
                        // starts without deep indentation or with a label and colon.
                        Matcher contIpv4 = ipv4Pattern.matcher(trimmed);
                        if (contIpv4.find()) {
                            return contIpv4.group(1);
                        }

                        // Heuristic: if the line appears to start a new field (contains ':'),
                        // stop expecting continuation for gateway.
                        if (trimmed.contains(":")) {
                            expectingContinuationForGateway = false;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static java.util.List<InterfaceInfo> getInterfaces() {
        java.util.List<InterfaceInfo> list = new java.util.ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress inet = addr.getAddress();
                    if (inet == null || inet.isLoopbackAddress() || inet.isLinkLocalAddress() || !(inet instanceof java.net.Inet4Address)) {
                        continue;
                    }
                    int prefix = addr.getNetworkPrefixLength();
                    list.add(new InterfaceInfo(ni.getName(), ni.getDisplayName(), inet.getHostAddress(), prefix));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static String getSubnetForInterface(InterfaceInfo iface) {
        if (iface == null || iface.getIpv4Address() == null) return null;
        try {
            String[] octets = iface.getIpv4Address().split("\\.");
            if (iface.getPrefixLength() == 24 && octets.length == 4) {
                return octets[0] + "." + octets[1] + "." + octets[2];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String getMacForIp(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        try {
            Process process = new ProcessBuilder("arp", "-a").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                // Example line formats (Windows):
                //  192.168.1.1           70-4f-57-12-34-56     dynamic
                //  192.168.1.3           d4-3b-04-12-34-56     dynamic
                Pattern row = Pattern.compile("^\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+([0-9A-Fa-f]{2}(?:-[0-9A-Fa-f]{2}){5})\\s+.*$");
                while ((line = reader.readLine()) != null) {
                    Matcher m = row.matcher(line);
                    if (m.find()) {
                        String entryIp = m.group(1);
                        String mac = m.group(2);
                        if (ip.equals(entryIp)) {
                            return mac.toLowerCase();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String findNmapHome() {
        // Try PATH
        try {
            Process p = new ProcessBuilder("where", "nmap").redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String first = r.readLine();
                if (first != null && !first.trim().isEmpty()) {
                    java.io.File exe = new java.io.File(first.trim());
                    if (exe.exists()) {
                        return exe.getParentFile().getAbsolutePath();
                    }
                }
            }
        } catch (Exception ignored) {}

        // Common Windows install dirs
        String[] candidates = new String[]{
                "C\\\\Program Files (x86)\\\\Nmap",
                "C\\\\Program Files\\\\Nmap"
        };
        for (String c : candidates) {
            try {
                java.io.File exe = new java.io.File(c + java.io.File.separator + "nmap.exe");
                if (exe.exists()) {
                    return c;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
}
