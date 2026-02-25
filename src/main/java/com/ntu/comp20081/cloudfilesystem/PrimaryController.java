package com.ntu.comp20081.cloudfilesystem;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import java.security.Key;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class PrimaryController implements Initializable {

    @FXML private TextField userField;
    @FXML private PasswordField passField;
    @FXML private ComboBox<String> roleBox;

    private static final String ALGO = "AES";
    private static final byte[] keyValue = "NtuCloudSystemKey".substring(0, 16).getBytes();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        roleBox.setItems(FXCollections.observableArrayList("ADMIN", "USER"));
        roleBox.getSelectionModel().select("USER");
    }

    @FXML
    public void handleLogin() {
        String username = userField.getText().trim();
        String password = passField.getText().trim();
        String selectedRole = roleBox.getValue();

        if (username.isEmpty() || password.isEmpty() || selectedRole == null) {
            showAlert(AlertType.WARNING, "Auth Error", "Please provide complete node credentials.");
            return;
        }

        try {
            String encryptedInput = encrypt(password);
            String query = "SELECT id, role FROM users WHERE username = ? AND password = ?";
            
            try (Connection conn = DBConn.getConnection(); 
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                
                pstmt.setString(1, username);
                pstmt.setString(2, encryptedInput);
                
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    String actualRole = rs.getString("role");
                    int userId = rs.getInt("id");

                   if ("ADMIN".equalsIgnoreCase(selectedRole) && !"ADMIN".equalsIgnoreCase(actualRole)) {
                        showAlert(AlertType.ERROR, "Access Denied", "This node does not have ADMIN authorization tokens.");
                        return;
                    }

                    saveSessionLocally(userId, username, actualRole);
                    switchToDashboard(username, actualRole, userId);
                } else {
                    showAlert(AlertType.ERROR, "Denied", "Invalid Node Identifier or Access Token.");
                }
            }
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "System Fault", "Auth Error: " + e.getMessage());
        }
    }

    @FXML
    public void handleRegister() {
        String username = userField.getText().trim();
        String password = passField.getText().trim();
        String selectedRole = roleBox.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert(AlertType.INFORMATION, "Registration", "Input details for new credentials.");
            return;
        }

        if ("ADMIN".equals(selectedRole)) {
            showAlert(AlertType.ERROR, "Access Denied", 
                "Direct ADMIN provisioning is prohibited for security. " +
                "Please register as a standard USER; an existing Administrator must elevate your node status.");
            return;
        }

        try {
            String encryptedPassword = encrypt(password);
            String sql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'USER')";
            
            try (Connection conn = DBConn.getConnection(); 
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, username);
                pstmt.setString(2, encryptedPassword);
                pstmt.executeUpdate();
                
                showAlert(AlertType.INFORMATION, "Success", "Node provisioned with AES protection.");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                showAlert(AlertType.ERROR, "Fault", "Node identifier already exists in the Registry.");
            } else {
                showAlert(AlertType.ERROR, "Fault", "Registry unreachable: " + e.getMessage());
            }
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Security Error", e.getMessage());
        }
    }

    private void saveSessionLocally(int id, String username, String role) {
        try (Connection conn = SQLiteDBConn.getConnection(); 
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sessions");
            String sql = "INSERT INTO sessions (user_id, username, role) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, username);
                pstmt.setString(3, role);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Local Sync Error: " + e.getMessage());
        }
    }

    private void switchToDashboard(String user, String role, int id) throws IOException {
        FXMLLoader loader = App.getFXMLLoader("secondary");
        Parent root = loader.load();
        SecondaryController controller = loader.getController();
        controller.setupUser(user, role, id);
        App.setRoot(root); 
    }

    private String encrypt(String data) throws Exception {
        Key key = new SecretKeySpec(keyValue, ALGO);
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encVal);
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}