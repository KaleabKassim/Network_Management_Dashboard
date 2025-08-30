package com.lan.network_management.controller;

import com.lan.network_management.model.Device;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressIndicator;

import java.net.InetAddress;

public class DeviceDetailsController {

    @FXML
    private Label ipLabel;
    @FXML
    private Label hostnameLabel;
    @FXML
    private Label macLabel;
    @FXML
    private Label reachableLabel;
    @FXML
    private Label pingLabel;
    @FXML
    private Button pingNowButton;
    @FXML
    private Button scanPortsButton;
    @FXML
    private TextField portsField;
    @FXML
    private ListView<String> openPortsList;
    @FXML
    private ProgressIndicator portsProgress;

    private Device device;

    public void setDevice(Device device) {
        this.device = device;
        refreshView();
    }

    @FXML
    private void initialize() {
        if (pingNowButton != null) {
            pingNowButton.setOnAction(e -> pingNow());
        }
        if (scanPortsButton != null) {
            scanPortsButton.setOnAction(e -> scanPorts());
        }
    }

    private void pingNow() {
        if (device == null) return;
        new Thread(() -> {
            try {
                InetAddress inet = InetAddress.getByName(device.getIp());
                long start = System.currentTimeMillis();
                boolean reachable = inet.isReachable(500);
                long pingTime = System.currentTimeMillis() - start;

                device.setReachable(reachable);
                if (reachable) {
                    device.setPingTime(pingTime);
                    if (device.getHostname() == null || device.getHostname().isEmpty()) {
                        device.setHostname(inet.getHostName());
                    }
                }
            } catch (Exception ignored) {
            }
            Platform.runLater(this::refreshView);
        }).start();
    }

    private void scanPorts() {
        if (device == null) return;
        String ip = device.getIp();
        String spec = portsField != null ? portsField.getText() : null;
        java.util.List<Integer> ports = parsePorts(spec);
        if (ports.isEmpty()) return;

        openPortsList.getItems().clear();
        scanPortsButton.setDisable(true);
        if (portsProgress != null) portsProgress.setVisible(true);

        new Thread(() -> {
            java.util.List<String> openWithNmap = scanWithNmap4j(ip, spec);
            if (openWithNmap.isEmpty()) {
                java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newFixedThreadPool(100);
                java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
                for (Integer port : ports) {
                    futures.add(exec.submit(() -> isOpen(ip, port, 300) ? port : -1));
                }
                java.util.List<Integer> open = new java.util.ArrayList<>();
                for (java.util.concurrent.Future<Integer> f : futures) {
                    try {
                        int p = f.get();
                        if (p > 0) open.add(p);
                    } catch (Exception ignored) {}
                }
                exec.shutdown();
                Platform.runLater(() -> {
                    for (Integer p : open) {
                        openPortsList.getItems().add(p + "/tcp");
                    }
                    scanPortsButton.setDisable(false);
                    if (portsProgress != null) portsProgress.setVisible(false);
                });
            } else {
                Platform.runLater(() -> {
                    openPortsList.getItems().addAll(openWithNmap);
                    scanPortsButton.setDisable(false);
                    if (portsProgress != null) portsProgress.setVisible(false);
                });
            }
        }).start();
    }

    private java.util.List<Integer> parsePorts(String spec) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (spec == null || spec.trim().isEmpty()) {
            // default common ports
            int[] common = { 22, 80, 443, 3389, 5900, 8080 };
            for (int p : common) out.add(p);
            return out;
        }
        for (String token : spec.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            if (t.contains("-")) {
                String[] parts = t.split("-", 2);
                try {
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    if (start > end) { int tmp = start; start = end; end = tmp; }
                    start = Math.max(1, start);
                    end = Math.min(65535, end);
                    for (int p = start; p <= end; p++) out.add(p);
                } catch (Exception ignored) {}
            } else {
                try {
                    int p = Integer.parseInt(t);
                    if (p >= 1 && p <= 65535) out.add(p);
                } catch (Exception ignored) {}
            }
        }

        java.util.Set<Integer> set = new java.util.LinkedHashSet<>(out);
        return new java.util.ArrayList<>(set);
    }

    private java.util.List<String> scanWithNmap4j(String host, String portsSpec) {
        java.util.List<String> results = new java.util.ArrayList<>();
        try {
            Class<?> nmap4jClass = Class.forName("org.nmap4j.Nmap4j");
            java.lang.reflect.Constructor<?> ctor = nmap4jClass.getConstructor(String.class);
            String nmapHome = com.lan.network_management.utils.NetworkUtils.findNmapHome();
            Object nmap4j = ctor.newInstance(nmapHome != null ? nmapHome : "nmap");

            String safeHost = host != null ? host.replaceAll("[^0-9.]", "") : "";
            nmap4jClass.getMethod("includeHosts", String.class).invoke(nmap4j, safeHost);

            String flags = (portsSpec != null && !portsSpec.trim().isEmpty())
                    ? ("-Pn -p " + portsSpec.trim() + " -oG -")
                    : "-Pn -F -oG -";
            nmap4jClass.getMethod("addFlags", String.class).invoke(nmap4j, flags);

            nmap4jClass.getMethod("execute").invoke(nmap4j);

            Boolean hasError = (Boolean) nmap4jClass.getMethod("hasError").invoke(nmap4j);
            if (!Boolean.TRUE.equals(hasError)) {
                Object execResults = nmap4jClass.getMethod("getExecutionResults").invoke(nmap4j);
                if (execResults != null) {
                    String output = (String) execResults.getClass().getMethod("getOutput").invoke(execResults);
                    if (output != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.StringReader(output));
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.startsWith("Host:")) {
                                int idx = line.indexOf("Ports:");
                                if (idx != -1) {
                                    String portsPart = line.substring(idx + 6).trim();
                                    for (String entry : portsPart.split(",")) {
                                        String e = entry.trim();
                                        String[] segs = e.split("/");
                                        if (segs.length >= 2) {
                                            String portStr = segs[0].trim();
                                            String state = segs[1].trim();
                                            String proto = segs.length >= 3 ? segs[2].trim() : "tcp";
                                            if ("open".equalsIgnoreCase(state)) {
                                                results.add(portStr + "/" + proto);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private boolean isOpen(String host, int port, int timeoutMs) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshView() {
        if (device == null) return;
        ipLabel.setText(device.getIp() != null ? device.getIp() : "-");
        hostnameLabel.setText(device.getHostname() != null ? device.getHostname() : "-");
        macLabel.setText(device.getMacAddress() != null ? device.getMacAddress() : "-");
        reachableLabel.setText(device.isReachable() ? "Reachable" : "Unreachable");
        pingLabel.setText(device.isReachable() ? (device.getPingTime() + " ms") : "-");
    }
}


