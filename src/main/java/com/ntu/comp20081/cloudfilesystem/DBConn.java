package com.ntu.comp20081.cloudfilesystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConn {

    private static final String URL_DOCKER = "jdbc:mysql://db-node:3306/lbcsystem";
    private static final String URL_LOCAL = "jdbc:mysql://localhost:3306/lbcsystem";
    private static final String USER = "root";
    private static final String PASS = "rootpassword";

    public static Connection getConnection() throws SQLException {
        String headless = System.getenv("HEADLESS_MODE");
        String activeUrl = URL_DOCKER;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(activeUrl, USER, PASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException(e.getMessage());
        }
    }

    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                createLogTableIfNotExists(conn);
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void createLogTableIfNotExists(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS logs ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INT, "
                + "event_type VARCHAR(50), "
                + "details TEXT, "
                + "event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}