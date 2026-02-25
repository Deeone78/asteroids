package com.ntu.comp20081.cloudfilesystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDBConn {
    private static final String URL = "jdbc:sqlite:local_session.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void initDatabase() {
        String createSessionTable = "CREATE TABLE IF NOT EXISTS sessions ("
                + "id INTEGER PRIMARY KEY, "
                + "user_id INTEGER, "
                + "username TEXT, "
                + "role TEXT, "
                + "login_time DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createSessionTable);
            System.out.println("SQLite: Local session table ready.");
        } catch (SQLException e) {
            System.err.println("SQLite Init Error: " + e.getMessage());
        }
    }
}