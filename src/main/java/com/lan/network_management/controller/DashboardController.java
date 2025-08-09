package com.lan.network_management.controller;

import com.lan.network_management.model.Device;
import com.lan.network_management.service.DeviceDiscoveryService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

import java.util.List;

public class DashboardController {
    @FXML
    private ListView<String> deviceList;
    @FXML
    private Button scanButton;

    @FXML
    private void initialize() {
        scanButton.setOnAction(e -> scanDevices());
    }

    private void scanDevices() {
        scanButton.setDisable(true);
        deviceList.getItems().clear();

        new Thread(() -> {
            DeviceDiscoveryService service = new DeviceDiscoveryService();
            List<Device> devices = service.scanNetwork("192.168.1", 200);

            Platform.runLater(() -> {
                for (Device device : devices) {
                    if (device.isReachable()) {
                        deviceList.getItems().add(device.toString());
                    }
                }
                scanButton.setDisable(false);
            });
        }).start();
    }
}
