package db;

import config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection {
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            Config.get("db.url", "LAYAG_DB_URL", "jdbc:mysql://localhost:3306/couriersystem"),
            Config.get("db.user", "LAYAG_DB_USER", "root"),
            Config.get("db.password", "LAYAG_DB_PASSWORD", "")
        );
    }
}