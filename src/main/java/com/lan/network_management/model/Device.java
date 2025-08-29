package com.lan.network_management.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    private String ip;
    private boolean reachable;
    private String hostname;
    private long pingTime;
    private String macAddress;

    public Device(String ip, boolean reachable) {
        this.ip = ip;
        this.reachable = reachable;
    }
}
