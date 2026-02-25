package com.ntu.comp20081.cloudfilesystem;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TerminalController {
    @FXML
    private TextArea terminalOutput;
    @FXML
    private TextField terminalInput;

    private String currentDirectory = "cloud_storage";
    private List<String> commandHistory = new ArrayList<>();

    @FXML
    private void handleCommand() {
        String input = terminalInput.getText().trim();
        terminalInput.clear();
        if (input.isEmpty())
            return;

        commandHistory.add(input);
        terminalOutput
                .appendText("\nadmin@cloud-os:~" + currentDirectory.replace("cloud_storage", "") + "$ " + input + "\n");

        String[] parts = input.split(" ");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                executeLs();
                break;
            case "cd":
                executeCd(parts);
                break;
            case "pwd":
                terminalOutput.appendText(currentDirectory + "\n");
                break;
            case "mkdir":
                executeMkdir(parts);
                break;
            case "cp":
                executeCp(parts);
                break;
            case "mv":
                executeMv(parts);
                break;
            case "rm":
                executeRm(parts);
                break;
            case "whoami":
                executeWhoAmI();
                break;
            case "ps":
                executePs();
                break;
            case "tree":
                executeTree(new File(currentDirectory), "");
                break;
            case "nano":
                executeNano(parts);
                break;
            case "help":
                showHelp();
                break;
            case "history":
                commandHistory.forEach(h -> terminalOutput.appendText(h + "\n"));
                break;
            case "clear":
                terminalOutput.clear();
                break;
            default:
                terminalOutput.appendText("bash: " + cmd + ": command not found\n");
        }
    }

    private void executeLs() {
        File dir = new File(currentDirectory);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String type = f.isDirectory() ? "<DIR> " : "      ";
                terminalOutput.appendText(type + f.getName() + "\n");
            }
        }
    }

    private void executeCd(String[] parts) {
        if (parts.length < 2 || parts[1].equals("~")) {
            currentDirectory = "cloud_storage";
            return;
        }
        if (parts[1].equals("..")) {
            if (!currentDirectory.equals("cloud_storage")) {
                currentDirectory = currentDirectory.substring(0, currentDirectory.lastIndexOf("/"));
            }
            return;
        }
        File nextDir = new File(currentDirectory + "/" + parts[1]);
        if (nextDir.exists() && nextDir.isDirectory()) {
            currentDirectory += "/" + parts[1];
        } else {
            terminalOutput.appendText("cd: no such directory: " + parts[1] + "\n");
        }
    }

    private void executeMkdir(String[] parts) {
        if (parts.length < 2) {
            terminalOutput.appendText("mkdir: missing operand\n");
            return;
        }
        File newDir = new File(currentDirectory + "/" + parts[1]);
        if (newDir.mkdir())
            terminalOutput.appendText("Directory created.\n");
        else
            terminalOutput.appendText("mkdir: failed to create directory.\n");
    }

    private void executeWhoAmI() {
        try (Connection conn = SQLiteDBConn.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT username FROM sessions LIMIT 1")) {
            if (rs.next())
                terminalOutput.appendText(rs.getString("username") + "\n");
        } catch (SQLException e) {
            terminalOutput.appendText("unknown_user\n");
        }
    }

    private void executePs() {
        terminalOutput.appendText("PID   TTY          TIME CMD\n");
        terminalOutput.appendText("101   ?        00:00:15 load_balancer\n");
        terminalOutput.appendText("204   ?        00:00:02 mysql_daemon\n");
        terminalOutput.appendText("501   pts/0    00:00:00 java_gui_shell\n");
    }

    private void executeTree(File dir, String indent) {
        terminalOutput.appendText(indent + "└── " + dir.getName() + "\n");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory())
                    executeTree(f, indent + "    ");
                else
                    terminalOutput.appendText(indent + "    ├── " + f.getName() + "\n");
            }
        }
    }

    private void executeNano(String[] parts) {
        if (parts.length < 2) {
            terminalOutput.appendText("nano: missing file\n");
            return;
        }
        terminalOutput.appendText("[SYSTEM] Virtual editor opened for " + parts[1] + ".\n");
        terminalOutput.appendText("[SYSTEM] Requirement 14: Use dashboard GUI for full content editing.\n");
    }

    private void showHelp() {
        terminalOutput.appendText("\n--- CLOUD-OS TERMINAL HELP ---\n");
        terminalOutput.appendText("ls           - List files/folders in current directory [cite: 193]\n");
        terminalOutput.appendText("cd [dir]     - Change directory \n");
        terminalOutput.appendText("pwd          - Print working directory \n");
        terminalOutput.appendText("mkdir [name] - Create new folder \n");
        terminalOutput.appendText("cp [s] [d]   - Copy file [cite: 192]\n");
        terminalOutput.appendText("mv [s] [d]   - Move/Rename file [cite: 191]\n");
        terminalOutput.appendText("rm [name]    - Delete file\n");
        terminalOutput.appendText("whoami       - Show current session user \n");
        terminalOutput.appendText("ps           - View cloud processes \n");
        terminalOutput.appendText("tree         - View directory hierarchy \n");
        terminalOutput.appendText("nano [file]  - Edit file content \n");
        terminalOutput.appendText("history      - Show command history\n");
        terminalOutput.appendText("clear        - Clear terminal output\n");
    }

    private void executeCp(String[] parts) {
        if (parts.length < 3) {
            terminalOutput.appendText("cp: missing destination file operand\n");
            return;
        }

        String sourceName = parts[1];
        String destName = parts[2];

        File sourceFile = new File(currentDirectory + "/" + sourceName);
        File destFile = new File(currentDirectory + "/" + destName);

        if (sourceFile.exists()) {
            try {
                Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                try (Connection conn = DBConn.getConnection()) {
                    int ownerId = 1;
                    String findOwnerSql = "SELECT user_id FROM files WHERE filename = ?";
                    try (PreparedStatement psOwner = conn.prepareStatement(findOwnerSql)) {
                        psOwner.setString(1, sourceName);
                        ResultSet rs = psOwner.executeQuery();
                        if (rs.next())
                            ownerId = rs.getInt("user_id");
                    }

                    String insertSql = "INSERT INTO files (filename, filesize, user_id) VALUES (?, ?, ?)";
                    try (PreparedStatement psInsert = conn.prepareStatement(insertSql)) {
                        psInsert.setString(1, destName);
                        psInsert.setLong(2, destFile.length());
                        psInsert.setInt(3, ownerId);
                        psInsert.executeUpdate();
                    }

                    terminalOutput.appendText("File '" + sourceName + "' copied to '" + destName + "' successfully.\n");
                    terminalOutput.appendText("Cloud Registry: Metadata synchronized for the new asset.\n");
                }
            } catch (Exception e) {
                terminalOutput.appendText("cp: error during copy process - " + e.getMessage() + "\n");
            }
        } else {
            terminalOutput.appendText("cp: cannot stat '" + sourceName + "': No such file\n");
        }
    }

    private void executeMv(String[] parts) {
        if (parts.length < 3) {
            terminalOutput.appendText("mv: missing destination file operand\n");
            return;
        }

        String sourceName = parts[1];
        String destName = parts[2];

        File sourceFile = new File(currentDirectory + "/" + sourceName);
        File destFile = new File(currentDirectory + "/" + destName);

        if (sourceFile.exists()) {
            try {
                Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                String sql = "UPDATE files SET filename = ? WHERE filename = ?";
                try (Connection conn = DBConn.getConnection();
                        PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setString(1, destName);
                    ps.setString(2, sourceName);
                    int rowsUpdated = ps.executeUpdate();

                    terminalOutput.appendText("File renamed from '" + sourceName + "' to '" + destName + "'.\n");
                    if (rowsUpdated > 0) {
                        terminalOutput.appendText("Cloud Registry: Asset identifier updated successfully.\n");
                    }
                }
            } catch (Exception e) {
                terminalOutput.appendText("mv: error during move process - " + e.getMessage() + "\n");
            }
        } else {
            terminalOutput.appendText("mv: cannot stat '" + sourceName + "': No such file\n");
        }
    }

    private void executeRm(String[] parts) {
        if (parts.length < 2) {
            terminalOutput.appendText("rm: missing operand\n");
            return;
        }
        String fileName = parts[1];
        File fileToDelete = new File(currentDirectory + "/" + fileName);

        if (fileToDelete.exists()) {
            if (fileToDelete.delete()) {
                try (Connection conn = DBConn.getConnection();
                        PreparedStatement ps = conn.prepareStatement("DELETE FROM files WHERE filename = ?")) {
                    ps.setString(1, fileName);
                    int rowsAffected = ps.executeUpdate();

                    terminalOutput.appendText("Physical file and registry entry removed.\n");
                    if (rowsAffected > 0) {
                        terminalOutput.appendText("Audit Log: File metadata purged from lbcsystem.\n");
                    }
                } catch (SQLException e) {
                    terminalOutput.appendText("DB Error: Could not sync registry - " + e.getMessage() + "\n");
                }
            } else {
                terminalOutput.appendText("rm: cannot remove '" + fileName + "': Permission denied\n");
            }
        } else {
            terminalOutput.appendText("rm: cannot remove '" + fileName + "': No such file\n");
        }
    }
}