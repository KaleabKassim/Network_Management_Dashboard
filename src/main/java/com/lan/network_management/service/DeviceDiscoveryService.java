package com.lan.network_management.service;

import com.lan.network_management.model.Device;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class DeviceDiscoveryService {

    public List<Device> scanNetwork(String subnet, int timeout) {
        List<Device> devices = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);

        List<Future<Device>> futures = new ArrayList<>();
        for (int i = 1; i < 255; i++) {
            final String host = subnet + "." + i;
            futures.add(executor.submit(() -> {
                try {
                    InetAddress inet = InetAddress.getByName(host);
                    boolean reachable = inet.isReachable(timeout);
                    return new Device(host, reachable);
                } catch (Exception e) {
                    return new Device(host, false);
                }
            }));
        }

        for (Future<Device> future : futures) {
            try {
                devices.add(future.get());
            } catch (Exception ignored) {}
        }

        executor.shutdown();
        return devices;
    }

    public List<Device> scanNetworks(List<String> subnets, int timeout) {
        List<Device> all = new ArrayList<>();
        if (subnets == null || subnets.isEmpty()) return all;
        ExecutorService outer = Executors.newFixedThreadPool(Math.min(subnets.size(), 8));
        List<Future<List<Device>>> futures = new ArrayList<>();
        for (String subnet : subnets) {
            futures.add(outer.submit(() -> scanNetwork(subnet, timeout)));
        }
        for (Future<List<Device>> f : futures) {
            try {
                all.addAll(f.get());
            } catch (Exception ignored) {}
        }
        outer.shutdown();
        return all;
    }
}
