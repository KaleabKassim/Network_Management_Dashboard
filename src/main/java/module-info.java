module com.lan.network_management {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    requires static lombok;

    opens com.lan.network_management to javafx.fxml;
    opens com.lan.network_management.controller to javafx.fxml;
    exports com.lan.network_management;
}
