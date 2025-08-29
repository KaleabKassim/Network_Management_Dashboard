package com.lan.network_management.controller;

import com.lan.network_management.model.Device;
import com.lan.network_management.service.DeviceDiscoveryService;
import com.lan.network_management.utils.NetworkUtils;
import com.lan.network_management.utils.NetworkUtils.InterfaceInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressIndicator;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashboardController {
    @FXML
    private ListView<Device> deviceList;
    @FXML
    private Button scanButton;
    @FXML
    private Pane topologyPane;
    @FXML
    private ComboBox<InterfaceInfo> interfaceCombo;
    @FXML
    private TextField subnetsField;
    @FXML
    private ProgressIndicator scanProgress;

    private final ObservableList<Device> observableDevices = FXCollections.observableArrayList();
    private ScheduledExecutorService scheduler;
    private String gatewayIp;

    @FXML
    private void initialize() {
        // Populate interfaces
        if (interfaceCombo != null) {
            interfaceCombo.getItems().setAll(NetworkUtils.getInterfaces());
            if (!interfaceCombo.getItems().isEmpty()) {
                interfaceCombo.getSelectionModel().selectFirst();
            }
            interfaceCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                scanDevices();
            });
        }

        deviceList.setItems(observableDevices);
        deviceList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Device device, boolean empty) {
                super.updateItem(device, empty);
                if (empty || device == null) {
                    setText(null);
                } else {
                    String status = device.isReachable() ? "Reachable" : "Unreachable";
                    String hostname = device.getHostname() != null ? device.getHostname() : "";
                    String ping = device.isReachable() ? device.getPingTime() + " ms" : "-";
                    setText(device.getIp() + (hostname.isEmpty() ? "" : (" (" + hostname + ")")) +
                            " | " + status + " | ping: " + ping);
                }
            }
        });
        deviceList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Device selected = deviceList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openDeviceDetails(selected);
                }
            }
        });

        scanButton.setOnAction(e -> scanDevices());
        scanDevices();

        // Add resize listener for topology pane
        if (topologyPane != null) {
            topologyPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0 && !observableDevices.isEmpty()) {
                    renderTopology();
                }
            });
            topologyPane.heightProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0 && !observableDevices.isEmpty()) {
                    renderTopology();
                }
            });
            
            // Handle initial layout
            topologyPane.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.getWidth() > 0 && newVal.getHeight() > 0 && !observableDevices.isEmpty()) {
                    renderTopology();
                }
            });
        }
    }

    private void scanDevices() {
        System.out.println("Scanning for devices...");
        scanButton.setDisable(true);
        if (scanProgress != null) scanProgress.setVisible(true);
        observableDevices.clear();
        stopSchedulerIfRunning();

        new Thread(() -> {
            String subnet = null;
            java.util.List<String> selectedSubnets = null;
            if (subnetsField != null) {
                String raw = subnetsField.getText();
                if (raw != null && !raw.trim().isEmpty()) {
                    selectedSubnets = parseSubnets(raw);
                }
            }
            InterfaceInfo selected = interfaceCombo != null ? interfaceCombo.getSelectionModel().getSelectedItem() : null;
            if (selected != null) {
                subnet = NetworkUtils.getSubnetForInterface(selected);
            }
            if (subnet == null) {
                subnet = NetworkUtils.getLocalSubnet();
            }
            gatewayIp = NetworkUtils.getDefaultGateway();
            DeviceDiscoveryService service = new DeviceDiscoveryService();
            List<Device> devices;
            if (selectedSubnets != null && !selectedSubnets.isEmpty()) {
                devices = service.scanNetworks(selectedSubnets, 200);
            } else if (subnet != null) {
                devices = service.scanNetwork(subnet, 200);
            } else {
                devices = java.util.Collections.emptyList();
            }

            for (Device device : devices) {
                if (device.isReachable()) {
                    try {
                        InetAddress inet = InetAddress.getByName(device.getIp());
                        long start = System.currentTimeMillis();
                        boolean reachable = inet.isReachable(500);
                        long pingTime = System.currentTimeMillis() - start;

                        device.setPingTime(pingTime);
                        device.setHostname(inet.getHostName());
                        device.setReachable(reachable);
                        if (device.getMacAddress() == null || device.getMacAddress().isEmpty()) {
                            String mac = NetworkUtils.getMacForIp(device.getIp());
                            if (mac != null) device.setMacAddress(mac);
                        }
                    } catch (Exception e) {
                        device.setReachable(false);
                    }
                }
            }

            Platform.runLater(() -> {
                for (Device device : devices) {
                    if (device.isReachable()) {
                        observableDevices.add(device);
                    }
                }
                scanButton.setDisable(false);
                if (scanProgress != null) scanProgress.setVisible(false);
                startRealtimePinging();
                renderTopology();
            });
        }).start();
    }

    private void startRealtimePinging() {
        if (observableDevices.isEmpty()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Device device : observableDevices) {
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
                        if (device.getMacAddress() == null || device.getMacAddress().isEmpty()) {
                            String mac = NetworkUtils.getMacForIp(device.getIp());
                            if (mac != null) device.setMacAddress(mac);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            Platform.runLater(() -> {
                deviceList.refresh();
                renderTopology();
            });
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopSchedulerIfRunning() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private java.util.List<String> parseSubnets(String raw) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String token : raw.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            // Accept forms: a.b.c, a.b.c., a.b.c.0/24
            if (t.endsWith("/24")) {
                t = t.substring(0, t.length() - 3);
                if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
            }
            if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
            // Validate 3 octets
            String[] parts = t.split("\\.");
            if (parts.length == 3) {
                list.add(parts[0] + "." + parts[1] + "." + parts[2]);
            }
        }
        return list;
    }

    private void renderTopology() {
        if (topologyPane == null) return;

        double width = topologyPane.getWidth() > 0 ? topologyPane.getWidth() : 800.0;
        double height = topologyPane.getHeight() > 0 ? topologyPane.getHeight() : 500.0;

        topologyPane.getChildren().clear();

        // Gateway positioning - centered at the top
        double gwX = width / 2.0;
        double gwY = Math.max(80.0, height * 0.15);
        double gwRadius = Math.min(25.0, Math.min(width, height) * 0.03);

        if (gatewayIp != null && !gatewayIp.isEmpty()) {
            Circle gw = new Circle(gwX, gwY, gwRadius, Color.DODGERBLUE);
            gw.setStroke(Color.DARKBLUE);
            gw.setStrokeWidth(2);
            Text gwLabel = new Text("Gateway\n" + gatewayIp);
            gwLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            // Center the text above the gateway
            gwLabel.setX(gwX - gwLabel.getLayoutBounds().getWidth() / 2);
            gwLabel.setY(gwY - gwRadius - 10);
            topologyPane.getChildren().addAll(gw, gwLabel);
        } else {
            Text noGw = new Text("Gateway not detected");
            noGw.setStyle("-fx-font-size: 14px; -fx-fill: #666;");
            noGw.setX(gwX - noGw.getLayoutBounds().getWidth() / 2);
            noGw.setY(gwY);
            topologyPane.getChildren().add(noGw);
        }

        int n = observableDevices.size();
        if (n == 0) return;

        // Calculate optimal radius based on available space and number of devices
        double maxRadius = Math.min(width, height) * 0.35;
        double minRadius = Math.min(width, height) * 0.25;
        double radius = Math.max(minRadius, Math.min(maxRadius, Math.min(width, height) / (2.0 + n * 0.1)));
        
        // Center coordinates for the entire topology
        double centerX = width / 2.0;
        double centerY = height / 2.0;

        for (int i = 0; i < n; i++) {
            Device d = observableDevices.get(i);
            // Skip rendering the gateway again if it appears as a scanned device
            if (gatewayIp != null && !gatewayIp.isEmpty() && gatewayIp.equals(d.getIp())) {
                continue;
            }
            // Calculate position in a circle around the center
            double angle = (2 * Math.PI * i) / n;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);

            // Ensure devices don't go outside the visible area
            double nodeRadius = Math.min(15.0, Math.min(width, height) * 0.02);
            x = Math.max(nodeRadius + 10, Math.min(width - nodeRadius - 10, x));
            y = Math.max(nodeRadius + 10, Math.min(height - nodeRadius - 10, y));

            if (gatewayIp != null && !gatewayIp.isEmpty()) {
                double dx = x - gwX;
                double dy = y - gwY;
                double dist = Math.max(1.0, Math.hypot(dx, dy));
                double ux = dx / dist;
                double uy = dy / dist;
                double startX = gwX + ux * (gwRadius + 4.0);
                double startY = gwY + uy * (gwRadius + 4.0);
                double endX = x - ux * (nodeRadius + 4.0);
                double endY = y - uy * (nodeRadius + 4.0);

                Line link = new Line(startX, startY, endX, endY);
                link.setStroke(Color.LIGHTGRAY);
                link.setStrokeWidth(1.5);
                topologyPane.getChildren().add(link);
            }

            Color nodeColor = d.isReachable() ? Color.LIMEGREEN : Color.CRIMSON;
            Circle node = new Circle(x, y, nodeRadius, nodeColor);
            node.setStroke(Color.DARKGRAY);
            node.setStrokeWidth(1.5);

            String host = d.getHostname() != null && !d.getHostname().isEmpty() ? d.getHostname() : d.getIp();
            String ping = d.isReachable() ? (d.getPingTime() + " ms") : "-";
            // Device label with better positioning
            Text label = new Text(host + "\n" + ping);
            label.setStyle("-fx-font-size: 11px;");
            
            // Position label to avoid overlapping
            double labelX = x - label.getLayoutBounds().getWidth() / 2;
            double labelY = y + nodeRadius + 20;
            
            // Adjust label position if it goes outside bounds
            if (labelY + label.getLayoutBounds().getHeight() > height - 10) {
                labelY = y - nodeRadius - 10;
            }
            if (labelX < 5) {
                labelX = 5;
            } else if (labelX + label.getLayoutBounds().getWidth() > width - 5) {
                labelX = width - label.getLayoutBounds().getWidth() - 5;
            }
            
            label.setX(labelX);
            label.setY(labelY);

            topologyPane.getChildren().addAll(node, label);
        }
    }

    private void openDeviceDetails(Device device) {
        try {
            FXMLLoader loader = new FXMLLoader(com.lan.network_management.MainApplication.class.getResource("device-details.fxml"));
            Parent root = loader.load();
            DeviceDetailsController controller = loader.getController();
            controller.setDevice(device);

            Stage stage = new Stage();
            stage.setTitle("Device Details - " + device.getIp());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(deviceList.getScene().getWindow());
            stage.setScene(new Scene(root, 420, 260));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
