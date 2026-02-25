package com.ntu.comp20081.cloudfilesystem;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import java.io.*;
import java.nio.file.*;
import java.security.Key;
import java.sql.*;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class DashboardController {

    @FXML private TableView<FileModel> fileTable;
    @FXML private TableColumn<FileModel, String> nameColumn;
    @FXML private TableColumn<FileModel, String> sizeColumn;
    @FXML private TableColumn<FileModel, String> dateColumn;
    @FXML private TableColumn<FileModel, String> colAccess; 

    private ObservableList<FileModel> fileList = FXCollections.observableArrayList();
    private int currentUserId = 4; 

    private static final String ALGO = "AES";
    private static final byte[] keyValue = "NtuCloudSystemKey".substring(0, 16).getBytes();

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));
        colAccess.setCellValueFactory(new PropertyValueFactory<>("permission"));
        loadFiles();
    }

    @FXML
    private void loadFiles() {
        fileList.clear();
        String query = "SELECT f.filename, f.filesize, f.upload_date, " +
                       "IF(f.user_id = ?, 'OWNER', fp.permission_type) as access_type " +
                       "FROM files f " +
                       "LEFT JOIN file_permissions fp ON f.id = fp.file_id AND fp.shared_with_user_id = ? " +
                       "WHERE f.user_id = ? OR fp.shared_with_user_id = ?";

        try (Connection conn = DBConn.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setInt(1, this.currentUserId);
            pstmt.setInt(2, this.currentUserId);
            pstmt.setInt(3, this.currentUserId);
            pstmt.setInt(4, this.currentUserId);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                long bytes = rs.getLong("filesize");
                String formattedSize = (bytes >= 1024 * 1024) ? 
                                       String.format("%.1f MB", (double) bytes / (1024 * 1024)) : 
                                       (bytes / 1024) + " KB";

                
                fileList.add(new FileModel(
                    rs.getString("filename"), 
                    formattedSize, 
                    rs.getString("upload_date"),
                    rs.getString("access_type")
                ));
            }
            fileTable.setItems(fileList);
        } catch (SQLException e) {
            showError("Database Error", e.getMessage());
        }
    }

    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload to Cloud");
        File file = fileChooser.showOpenDialog(fileTable.getScene().getWindow());
        
        if (file != null) {
            try {
                byte[] fullContent = Files.readAllBytes(file.toPath());
                int mid = fullContent.length / 2;

                byte[] encChunk1 = performCrypto(Cipher.ENCRYPT_MODE, Arrays.copyOfRange(fullContent, 0, mid));
                byte[] encChunk2 = performCrypto(Cipher.ENCRYPT_MODE, Arrays.copyOfRange(fullContent, mid, fullContent.length));
                
                Files.write(new File("cloud_storage/server1/" + file.getName() + ".part1").toPath(), encChunk1);
                Files.write(new File("cloud_storage/server1/" + file.getName() + ".part2").toPath(), encChunk2);
                Files.write(new File("cloud_storage/server2/" + file.getName() + ".part1").toPath(), encChunk1);
                Files.write(new File("cloud_storage/server2/" + file.getName() + ".part2").toPath(), encChunk2);
                
                saveFileMetadata(file);
            } catch (Exception e) {
                showError("Processing Error", e.getMessage());
            }
        }
    }

    private byte[] performCrypto(int mode, byte[] data) throws Exception {
        Key key = new SecretKeySpec(keyValue, ALGO);
        Cipher c = Cipher.getInstance(ALGO);
        c.init(mode, key);
        return c.doFinal(data);
    }

    private void saveFileMetadata(File file) {
        String sql = "INSERT INTO files (filename, filesize, user_id) VALUES (?, ?, ?)";
        try (Connection conn = DBConn.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, file.getName());
            pstmt.setLong(2, file.length());
            pstmt.setInt(3, this.currentUserId);
            pstmt.executeUpdate();
            loadFiles();
        } catch (SQLException e) {
            showError("Upload Error", e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        try {
            App.setRoot("primary");
        } catch (Exception e) {
            showError("Navigation Error", e.getMessage());
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}