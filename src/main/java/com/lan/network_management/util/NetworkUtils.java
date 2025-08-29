package com.lan.network_management.util;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkUtils {

    public static String getSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isSiteLocalAddress()) {
                        String ip = addr.getHostAddress();
                        int prefix = ia.getNetworkPrefixLength();
                        String subnet = calculateSubnet(ip, prefix);
                        return subnet; // Return the first found subnet
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String calculateSubnet(String ip, int prefix) {
        String[] parts = ip.split("\\.");
        int mask = 0xffffffff << (32 - prefix);
        int ipNum = (Integer.parseInt(parts[0]) << 24)
                | (Integer.parseInt(parts[1]) << 16)
                | (Integer.parseInt(parts[2]) << 8)
                | Integer.parseInt(parts[3]);

        int subnetNum = ipNum & mask;
        return String.format("%d.%d.%d.%d",
                (subnetNum >> 24) & 0xff,
                (subnetNum >> 16) & 0xff,
                (subnetNum >> 8) & 0xff,
                subnetNum & 0xff);
    }
}