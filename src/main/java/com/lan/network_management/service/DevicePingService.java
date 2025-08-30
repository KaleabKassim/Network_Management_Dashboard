package com.lan.network_management.service;

import java.net.InetAddress;

public class DevicePingService {

    public boolean ping(String host, int timeout) {
        try {
            InetAddress inet = InetAddress.getByName(host);
            return inet.isReachable(timeout);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Pings a device multiple times and returns the number of successful pings.
     * @param host IP or hostname
     * @param timeout timeout per ping in milliseconds
     * @param attempts number of pings to attempt
     * @return number of successful pings
     */
    public int pingMultiple(String host, int timeout, int attempts) {
        int success = 0;
        for (int i = 0; i < attempts; i++) {
            if (ping(host, timeout)) {
                success++;
            }
        }
        return success;
    }
}
