package com.ntu.comp20081.cloudfilesystem;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.Key;
import java.sql.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javafx.collections.*;
import javafx.concurrent.*;
import javafx.fxml.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.stage.*;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import java.util.zip.CRC32;

import org.eclipse.paho.client.mqttv3.*;
import com.jcraft.jsch.*;

public class SecondaryController implements Initializable {

    @FXML
    private Label welcomeLabel, progressStatusLabel, delayTimerLabel;
    @FXML
    private VBox adminContainer;
    @FXML
    private Button btnRemoveUser, btnManageAccess, btnMetrics;
    @FXML
    private Circle statusCircle1, statusCircle2;
    @FXML
    private Label statusLabel1, statusLabel2;
    @FXML
    private ProgressBar operationProgressBar;
    @FXML
    private ProgressIndicator activeOperationIndicator;
    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, Integer> colId;
    @FXML
    private TableColumn<User, String> colUsername, colRole;
    @FXML
    private TableView<FileModel> fileTable;
    @FXML
    private TableColumn<FileModel, String> nameColumn, sizeColumn, dateColumn, colAccess;

    private String currentUserRole;
    private int currentUserId;

    public enum LoadBalancerType {
        ROUND_ROBIN, SJN, PRIORITY
    }

    private LoadBalancerType selectedAlgorithm = LoadBalancerType.ROUND_ROBIN;

    private final List<File> activeNodes = new ArrayList<>();
    private int rrIndex = 0;

    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private final ObservableList<FileModel> fileList = FXCollections.observableArrayList();

    private static final String ALGO = "AES";
    private static final byte[] keyValue = "NtuCloudSystemKey".substring(0, 16).getBytes();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));
        colAccess.setCellValueFactory(new PropertyValueFactory<>("permission"));
    }

    public void setupUser(String username, String role, int userId) {
        this.currentUserRole = role.toUpperCase();
        this.currentUserId = userId;
        this.welcomeLabel.setText("Infrastructure Node | " + username + " (" + role + ")");

        boolean isAdmin = "ADMIN".equals(this.currentUserRole);
        adminContainer.setVisible(isAdmin);
        adminContainer.setManaged(isAdmin);
        btnRemoveUser.setVisible(isAdmin);
        btnManageAccess.setVisible(isAdmin);
        btnMetrics.setVisible(isAdmin);

        if (isAdmin) {
            loadUserList();
        }
        loadFileList();
        performHealthCheck();
        logEvent("NODE_AUTH", "User authenticated and session synchronized.");
    }

    private void performHealthCheck() {
        activeNodes.clear();
        File root = new File("cloud_storage");
        if (!root.exists())
            root.mkdirs();
        File[] nodes = root.listFiles(File::isDirectory);
        if (nodes != null)
            Collections.addAll(activeNodes, nodes);

        statusLabel1.setText(activeNodes.size() + " STORAGE NODES ONLINE");
        statusCircle1.setFill(activeNodes.size() >= 2 ? Color.web("#27ae60") : Color.web("#f39c12"));
    }

    @FXML
    private void handleUpload() {
        performHealthCheck();

        if (activeNodes.size() < 2) {
            showAlert(AlertType.ERROR, "Infrastructure Fault", "Minimum 2 storage nodes required.");
            return;
        }

        FileChooser fc = new FileChooser();
        File file = fc.showOpenDialog(fileTable.getScene().getWindow());
        if (file == null)
            return;

        lockFile(file.getName(), true);

        int delaySeconds = 30 + new java.util.Random().nextInt(61);

        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Requesting Nodes via MQTT...");

                String targetNodes = requestNodesViaMQTT();
                String[] nodes = targetNodes.split(",");

                for (int i = 1; i <= delaySeconds; i++) {
                    if (isCancelled())
                        break;
                    Thread.sleep(1000);
                    updateProgress(i, delaySeconds);
                    final int currentSec = i;
                    javafx.application.Platform
                            .runLater(() -> delayTimerLabel.setText(currentSec + "s / " + delaySeconds + "s"));
                }

                byte[] content = Files.readAllBytes(file.toPath());
                CRC32 crc = new CRC32();
                crc.update(content);
                long fileChecksum = crc.getValue();

                int mid = content.length / 2;
                byte[] enc1 = performCrypto(Cipher.ENCRYPT_MODE, Arrays.copyOfRange(content, 0, mid));
                byte[] enc2 = performCrypto(Cipher.ENCRYPT_MODE, Arrays.copyOfRange(content, mid, content.length));

                updateMessage("Transferring Chunks via OpenSSH...");

                uploadToNodeSSH(nodes[0], file.getName() + ".part1", enc1);
                uploadToNodeSSH(nodes[1], file.getName() + ".part2", enc2);

                saveFileToDB(file.getName(), file.length(), fileChecksum);
                return null;
            }
        };

        uploadTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            lockFile(file.getName(), false);
            loadFileList();
            logEvent("UPLOAD_" + selectedAlgorithm, "Distributed via MQTT/SSH.");
        });

        uploadTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            lockFile(file.getName(), false);
            showAlert(AlertType.ERROR, "Fault", uploadTask.getException().getMessage());
        });

        bindTaskToUI(uploadTask, "Upload Successful");
        new Thread(uploadTask).start();
    }

    @FXML
    private void handleDownload() {
        FileModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || isFileLocked(selected.getFileName()))
            return;

        if (!"OWNER".equals(selected.getPermission()) &&
                (selected.getPermission() == null || !selected.getPermission().contains("READ"))) {
            showAlert(AlertType.ERROR, "Denied", "READ permission required.");
            return;
        }

        lockFile(selected.getFileName(), true);
        FileChooser fc = new FileChooser();
        fc.setInitialFileName(selected.getFileName());
        File dest = fc.showSaveDialog(fileTable.getScene().getWindow());

        if (dest == null) {
            lockFile(selected.getFileName(), false);
            return;
        }

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Retrieving Chunks via OpenSSH...");

                long expectedCrc = getExpectedChecksum(selected.getFileName());
                String[] nodeHosts = { "server1", "server2" };

                byte[] enc1 = downloadFromNodeSSH(nodeHosts[0], selected.getFileName() + ".part1");
                byte[] enc2 = downloadFromNodeSSH(nodeHosts[1], selected.getFileName() + ".part2");

                updateMessage("Reconstructing Data...");

                byte[] p1 = performCrypto(Cipher.DECRYPT_MODE, enc1);
                byte[] p2 = performCrypto(Cipher.DECRYPT_MODE, enc2);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(p1);
                bos.write(p2);
                byte[] reassembled = bos.toByteArray();

                CRC32 checkCrc = new CRC32();
                checkCrc.update(reassembled);

                if (checkCrc.getValue() != expectedCrc) {
                    throw new IOException("INTEGRITY ERROR: CRC32 Mismatch.");
                }

                Files.write(dest.toPath(), reassembled);
                return null;
            }
        };

        downloadTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            lockFile(selected.getFileName(), false);
            showAlert(AlertType.INFORMATION, "Success", "File verified and reassembled.");
        });

        downloadTask.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, e -> {
            lockFile(selected.getFileName(), false);
            showAlert(AlertType.ERROR, "Fault", downloadTask.getException().getMessage());
        });

        bindTaskToUI(downloadTask, "Syncing...");
        new Thread(downloadTask).start();
    }

    private String requestNodesViaMQTT() throws Exception {
        String broker = "tcp://mqtt-broker:1883";
        MqttClient client = new MqttClient(broker, MqttClient.generateClientId(), null);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        client.connect(options);

        client.publish("loadbalancer/request", new MqttMessage(selectedAlgorithm.toString().getBytes()));

        final String[] response = new String[1];
        client.subscribe("loadbalancer/response", (topic, msg) -> {
            response[0] = new String(msg.getPayload());
        });

        long timeout = System.currentTimeMillis() + 5000;
        while (response[0] == null && System.currentTimeMillis() < timeout) {
            Thread.sleep(100);
        }
        client.disconnect();
        return response[0] != null ? response[0] : "server1,server2";
    }

    private void uploadToNodeSSH(String host, String remoteName, byte[] data) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession("ntu-user", getServiceHost(host), getSSHPort(host));
        session.setPassword("ntu-user");
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        channel.put(new ByteArrayInputStream(data), "/home/ntu-user/storage/" + remoteName);

        channel.disconnect();
        session.disconnect();
    }

    private byte[] downloadFromNodeSSH(String host, String remoteName) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession("ntu-user", getServiceHost(host), getSSHPort(host));
        session.setPassword("ntu-user");
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        InputStream is = channel.get("/home/ntu-user/storage/" + remoteName);
        byte[] data = is.readAllBytes();

        channel.disconnect();
        session.disconnect();
        return data;
    }

    private void saveFileToDB(String name, long size, long checksum) throws SQLException {
        String sql = "INSERT INTO files (filename, filesize, user_id, checksum) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setLong(2, size);
            ps.setInt(3, this.currentUserId);
            ps.setLong(4, checksum);
            ps.executeUpdate();
        }
    }

    private long getExpectedChecksum(String fileName) throws SQLException {
        String sql = "SELECT checksum FROM files WHERE filename = ?";
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong("checksum") : 0;
        }
    }

    @FXML
    private void handleManageAccess() {
        if (!"ADMIN".equals(this.currentUserRole)) {
            showAlert(AlertType.ERROR, "Access Denied", "Only administrators can modify roles.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Node Registry Management");
        TextField userField = new TextField();
        userField.setPromptText("Enter Target Username");
        RadioButton rbAdmin = new RadioButton("Promote to ADMIN");
        RadioButton rbUser = new RadioButton("Demote to standard USER");
        ToggleGroup group = new ToggleGroup();
        rbAdmin.setToggleGroup(group);
        rbUser.setToggleGroup(group);
        rbAdmin.setSelected(true);
        VBox content = new VBox(15, new Label("Target Node:"), userField, new Separator(), new Label("Assign Role:"),
                rbAdmin, rbUser);
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String target = userField.getText().trim();
            if (!target.isEmpty())
                updateNodeRole(target, rbAdmin.isSelected() ? "ADMIN" : "USER");
        }
    }

    private void updateNodeRole(String username, String role) {
        String sql = "UPDATE users SET role = ? WHERE username = ?";
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, username);
            if (ps.executeUpdate() > 0) {
                logEvent("ROLE_CHANGE", username + " updated to " + role);
                showAlert(AlertType.INFORMATION, "Success", username + " is now a " + role);
                loadUserList();
            }
        } catch (SQLException e) {
            showAlert(AlertType.ERROR, "Fault", "Registry sync failed.");
        }
    }

    @FXML
    private void processAccessGrant(String targetUsername, String fileName, String permissionString) {
        try (Connection conn = DBConn.getConnection()) {
            int targetId = getUserId(targetUsername, conn);
            int fileId = getFileId(fileName, conn);
            if (targetId != -1 && fileId != -1) {
                String sql = "INSERT INTO file_permissions (file_id, shared_with_user_id, permission_type, granted_by_id) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE permission_type = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, fileId);
                    ps.setInt(2, targetId);
                    ps.setString(3, permissionString);
                    ps.setInt(4, this.currentUserId);
                    ps.setString(5, permissionString);
                    ps.executeUpdate();
                    logEvent("RBAC_GRANT", targetUsername + " granted " + permissionString + " on " + fileName);
                    showAlert(AlertType.INFORMATION, "Success", "Permissions updated.");
                    loadFileList();
                }
            }
        } catch (SQLException e) {
            showAlert(AlertType.ERROR, "Fault", "RBAC sync failed.");
        }
    }

    @FXML
    private void handleShareFile() {
        FileModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null || !"OWNER".equals(selected.getPermission())) {
            showAlert(AlertType.ERROR, "Access Denied", "Owner only.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Advanced RBAC");
        TextField userField = new TextField();
        CheckBox readBox = new CheckBox("READ");
        CheckBox writeBox = new CheckBox("WRITE");
        CheckBox deleteBox = new CheckBox("DELETE");
        VBox content = new VBox(10, new Label("Target Node:"), userField, new Label("Assign Rights:"),
                new HBox(15, readBox, writeBox, deleteBox));
        content.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String perms = (readBox.isSelected() ? "READ," : "") + (writeBox.isSelected() ? "WRITE," : "")
                    + (deleteBox.isSelected() ? "DELETE" : "");
            if (perms.endsWith(","))
                perms = perms.substring(0, perms.length() - 1);
            if (!userField.getText().trim().isEmpty() && !perms.isEmpty())
                processAccessGrant(userField.getText().trim(), selected.getFileName(), perms);
        }
    }

    @FXML
    private void handleDeleteUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getUsername().equals("admin"))
            return;
        if (new Alert(AlertType.CONFIRMATION, "Delete node?").showAndWait().get() == ButtonType.OK) {
            try (Connection conn = DBConn.getConnection();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setInt(1, selected.getId());
                ps.executeUpdate();
                loadUserList();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleOpenMetrics() {
        try {
            Stage stage = new Stage();
            stage.setTitle("Analytics");
            stage.setScene(new Scene(FXMLLoader.load(getClass().getResource("metrics.fxml"))));
            stage.show();
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Fault", "Analytics failed.");
        }
    }

    @FXML
    public void loadFileList() {
        fileList.clear();
        String sql = "SELECT f.filename, f.filesize, f.upload_date, IF(f.user_id = ?, 'OWNER', GROUP_CONCAT(fp.permission_type SEPARATOR ', ')) as access_type FROM files f LEFT JOIN file_permissions fp ON f.id = fp.file_id AND fp.shared_with_user_id = ? WHERE f.user_id = ? OR fp.shared_with_user_id = ? GROUP BY f.id";
        try (Connection conn = DBConn.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, this.currentUserId);
            ps.setInt(2, this.currentUserId);
            ps.setInt(3, this.currentUserId);
            ps.setInt(4, this.currentUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double sizeMB = rs.getLong("filesize") / (1024.0 * 1024.0);
                fileList.add(new FileModel(rs.getString("filename"),
                        new java.text.DecimalFormat("0.00").format(sizeMB) + " MB", rs.getString("upload_date"),
                        rs.getString("access_type")));
            }
            fileTable.setItems(fileList);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeleteFile() {
        FileModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null
                || (!"OWNER".equals(selected.getPermission()) && !selected.getPermission().contains("DELETE"))) {
            showAlert(AlertType.ERROR, "Denied", "DELETE authorization required.");
            return;
        }
        if (new Alert(AlertType.CONFIRMATION, "Purge chunks?").showAndWait().get() == ButtonType.OK) {
            try (Connection conn = DBConn.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement("DELETE FROM files WHERE filename = ?")) {
                pstmt.setString(1, selected.getFileName());
                if (pstmt.executeUpdate() > 0) {
                    deleteFromNodeSSH("server1", selected.getFileName() + ".part1");
                    deleteFromNodeSSH("server2", selected.getFileName() + ".part2");
                    loadFileList();
                }
            } catch (Exception e) {
                showAlert(AlertType.ERROR, "Fault", e.getMessage());
            }
        }
    }

    private void deleteFromNodeSSH(String host, String remoteName) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession("ntu-user", getServiceHost(host), getSSHPort(host));
        session.setPassword("ntu-user");
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        channel.rm("/home/ntu-user/storage/" + remoteName);
        channel.disconnect();
        session.disconnect();
    }

    private byte[] performCrypto(int mode, byte[] data) throws Exception {
        Key key = new SecretKeySpec(keyValue, ALGO);
        Cipher c = Cipher.getInstance(ALGO);
        c.init(mode, key);
        return c.doFinal(data);
    }

    private void logEvent(String type, String detail) {
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("INSERT INTO logs (user_id, event_type, details) VALUES (?, ?, ?)")) {
            ps.setInt(1, this.currentUserId);
            ps.setString(2, type);
            ps.setString(3, detail);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void lockFile(String fileName, boolean lock) {
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE files SET is_locked = ? WHERE filename = ?")) {
            ps.setInt(1, lock ? 1 : 0);
            ps.setString(2, fileName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean isFileLocked(String fileName) {
        try (Connection conn = DBConn.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT is_locked FROM files WHERE filename = ?")) {
            ps.setString(1, fileName);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("is_locked") == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    private int getUserId(String name, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private int getFileId(String name, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM files WHERE filename = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    private void bindTaskToUI(Task<?> task, String successMsg) {
        operationProgressBar.progressProperty().bind(task.progressProperty());
        progressStatusLabel.textProperty().bind(task.messageProperty());
        activeOperationIndicator.setVisible(true);
        task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> {
            operationProgressBar.progressProperty().unbind();
            progressStatusLabel.textProperty().unbind();
            progressStatusLabel.setText(successMsg);
            activeOperationIndicator.setVisible(false);
        });
    }

    @FXML
    public void loadUserList() {
        userList.clear();
        try (Connection conn = DBConn.getConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, username, role FROM users")) {
            while (rs.next()) {
                userList.add(new User(rs.getInt("id"), rs.getString("username"), rs.getString("role")));
            }
            userTable.setItems(userList);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() throws IOException {
        App.setRoot("primary");
    }

    @FXML
    private void handleOpenTerminal() {
        try {
            Stage stage = new Stage();
            stage.setTitle("Console");
            stage.setScene(new Scene(FXMLLoader.load(getClass().getResource("terminal.fxml"))));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private String getServiceHost(String dockerHost) {
        String headless = System.getenv("HEADLESS_MODE");
        return dockerHost;
    }

    private int getSSHPort(String host) {
        return 22;
    }
}