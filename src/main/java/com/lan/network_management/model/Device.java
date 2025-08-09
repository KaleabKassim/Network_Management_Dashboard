package com.lan.network_management.model;
public class Device {
    private String ip;
    private boolean reachable;

    public Device(String ip, boolean reachable) {
        this.ip = ip;
        this.reachable = reachable;
    }

    public String getIp() { return ip; }
    public boolean isReachable() { return reachable; }

    @Override
    public String toString() {
        return ip + (reachable ? " (Online)" : " (Offline)");
    }
}
