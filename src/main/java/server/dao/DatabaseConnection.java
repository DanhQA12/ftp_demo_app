package server.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        // 1. Ép nạp Driver MySQL trước
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("LỖI CRITICAL: Chưa gán thư viện MySQL Driver vào Module Dependencies!");
            e.printStackTrace();
        }

        // 2. Đọc file cấu hình
        try (FileInputStream fis = new FileInputStream("server_config.properties")) {
            Properties properties = new Properties();
            properties.load(fis);

            URL = properties.getProperty("db.url");
            USER = properties.getProperty("db.user");
            PASSWORD = properties.getProperty("db.password");
        } catch (IOException e) {
            System.err.println("LỖI CRITICAL: Không tìm thấy file server_config.properties tại gốc dự án!");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (URL == null) {
            throw new SQLException("Đường dẫn kết nối CSDL (db.url) bị rỗng. Kiểm tra lại file server_config.properties!");
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}