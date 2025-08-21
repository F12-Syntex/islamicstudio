package com.syntex.islamicstudio.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:islamicstudio.db";
    private static Connection conn;

    public static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }
}
