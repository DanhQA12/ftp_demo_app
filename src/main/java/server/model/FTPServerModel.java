package server.model;

import server.config.DatabaseConnection;
import server.controller.ClientControlThread;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Consumer;

public class FTPServerModel {
    private ServerSocket serverSocket;
    private boolean running = false;
    private final int port;
    private String rootDir = "server_files/";
    private final AuthModel authModel;

    public FTPServerModel(int port) {
        this.port = port;
        this.authModel = new AuthModel();
        loadConfig();
        initDirectories();
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("server_config.properties")) {
            if (input != null) {
                Properties prop = new Properties();
                prop.load(input);
                this.rootDir = prop.getProperty("ftp.root_dir", "server_files/");
                if (!this.rootDir.endsWith("/")) {
                    this.rootDir += "/";
                }
            }
        } catch (IOException e) {
            System.err.println("Không thể nạp cấu hình thư mục gốc, dùng mặc định.");
        }
    }

    private void initDirectories() {
        new File(rootDir + "public_anonymous/").mkdirs();
        new File(rootDir + "users/").mkdirs();
        // Đã xóa phần khởi tạo groups/Public
    }

    public void startServer(Consumer<String> logger, Runnable onStop) {
        new Thread(() -> {
            logger.accept("Đang kiểm tra kết nối tới MySQL...");
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    logger.accept("Kết nối MySQL thành công!");
                    authModel.initDefaultAccounts();
                }
            } catch (SQLException e) {
                logger.accept("LỖI CSDL: " + e.getMessage());
                onStop.run();
                return;
            }

            try {
                serverSocket = new ServerSocket(port);
                running = true;
                logger.accept("FTP Server đã khởi động trên cổng " + port + " (Kênh Control).");

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientControlThread(clientSocket, rootDir, authModel, logger).start();
                }
            } catch (IOException e) {
                if (running) logger.accept("Lỗi Socket: " + e.getMessage());
            } finally {
                onStop.run();
            }
        }).start();
    }

    public void stopServer(Consumer<String> logger) {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            logger.accept("Máy chủ đã dừng hoạt động.");
        } catch (IOException e) {
            logger.accept("Lỗi khi dừng máy chủ: " + e.getMessage());
        }
    }
}