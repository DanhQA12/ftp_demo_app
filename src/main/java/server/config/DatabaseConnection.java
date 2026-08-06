package server.config;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DatabaseConnection {

    public static Connection getConnection() {
        try (InputStream input = DatabaseConnection.class.getClassLoader()
                .getResourceAsStream("server_config.properties")) {

            Properties prop = new Properties();
            if (input == null) {
                throw new RuntimeException("Không tìm thấy file server_config.properties trong thư mục resources.");
            }
            prop.load(input);

            Class.forName("com.mysql.cj.jdbc.Driver");

            return DriverManager.getConnection(
                    prop.getProperty("db.url"),
                    prop.getProperty("db.username"),
                    prop.getProperty("db.password")
            );

        } catch (Exception e) {
            System.err.println("Lỗi kết nối Database: " + e.getMessage());
        }
        return null;
    }
}