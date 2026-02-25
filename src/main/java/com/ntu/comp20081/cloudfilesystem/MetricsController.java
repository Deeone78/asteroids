package com.ntu.comp20081.cloudfilesystem;

import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import java.io.File;
import java.text.DecimalFormat;

public class MetricsController {
    @FXML
    private BarChart<String, Number> storageChart;
    @FXML
    private VBox statusContainer;

    private static final DecimalFormat df = new DecimalFormat("0.00");

    public void initialize() {
        refreshData();
    }

    @FXML
    private void refreshData() {
        storageChart.getData().clear();
        statusContainer.getChildren().clear();

        File root = new File("cloud_storage");
        if (!root.exists()) root.mkdirs();

        File[] nodes = root.listFiles(File::isDirectory);

        if (nodes != null) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Capacity Distribution");

            for (File node : nodes) {
                double sizeMB = getFolderSize(node) / (1024.0 * 1024.0);
                
                series.getData().add(new XYChart.Data<>(node.getName().toUpperCase(), sizeMB));

                HBox row = new HBox();
                row.setAlignment(Pos.CENTER_LEFT);
                row.setSpacing(20);

                Label nameLabel = new Label(node.getName().toUpperCase() + " Status:");
                nameLabel.setTextFill(javafx.scene.paint.Color.web("#bdc3c7"));
                
                Label statusLabel = new Label("HEALTHY");
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2ecc71;");
                
                javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                row.getChildren().addAll(nameLabel, spacer, statusLabel);
                statusContainer.getChildren().add(row);
            }
            storageChart.getData().add(series);
        }
    }

    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                length += file.isFile() ? file.length() : getFolderSize(file);
            }
        }
        return length;
    }
}