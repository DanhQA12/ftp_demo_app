package server.model;

import server.dao.DatabaseConnection;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

public class FTPServerModel {
    private ServerSocket serverSocket;
    private boolean running = false;
    private final int port;
    private static final String BASE_DIR = "server_files/";
    private final AuthModel authModel;

    public FTPServerModel(int port) {
        this.port = port;
        this.authModel = new AuthModel();

        // Tự động khởi tạo thư mục gốc
        new File(BASE_DIR + "public_anonymous/").mkdirs();
        new File(BASE_DIR + "users/").mkdirs();
        new File(BASE_DIR + "groups/Public/").mkdirs();
    }

    public void startServer(Consumer<String> logger, Runnable onStop) {
        new Thread(() -> {
            logger.accept("Đang kiểm tra kết nối tới Cơ sở dữ liệu MySQL...");
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    logger.accept("Kết nối Cơ sở dữ liệu MySQL thành công!");
                    authModel.initDefaultAccounts();
                }
            } catch (SQLException e) {
                logger.accept("LỖI KẾT NỐI DATABASE: " + e.getMessage());
                logger.accept("Hệ thống không thể khởi động Server do thiếu kết nối CSDL!");
                onStop.run();
                return;
            }

            try {
                serverSocket = new ServerSocket(port);
                running = true;
                logger.accept("Máy chủ FTP đã khởi động thành công trên cổng " + port + ".");

                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket, logger).start();
                }
            } catch (IOException e) {
                if (running) logger.accept("Lỗi máy chủ Socket: " + e.getMessage());
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

    private class ClientHandler extends Thread {
        private final Socket socket;
        private final Consumer<String> logger;
        private PrintWriter out;
        private BufferedReader in;
        private DataInputStream dataIn;
        private DataOutputStream dataOut;
        private User currentUser = null;

        public ClientHandler(Socket socket, Consumer<String> logger) {
            this.socket = socket;
            this.logger = logger;
        }

        // Lấy đường dẫn thư mục cô lập cho từng người dùng
        private String getUserWorkingDir() {
            if (currentUser == null || "anonymous".equalsIgnoreCase(currentUser.getUsername())) {
                return BASE_DIR + "public_anonymous/";
            }
            return BASE_DIR + "users/" + currentUser.getUsername() + "/";
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                dataIn = new DataInputStream(socket.getInputStream());
                dataOut = new DataOutputStream(socket.getOutputStream());

                logger.accept("Máy khách kết nối từ địa chỉ: " + socket.getInetAddress());

                String command;
                while ((command = in.readLine()) != null) {
                    handleCommand(command);
                }
            } catch (IOException e) {
                String clientName = (currentUser != null) ? currentUser.getUsername() : socket.getInetAddress().toString();
                logger.accept("Máy khách đã ngắt kết nối: " + clientName);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleCommand(String command) throws IOException {
            if (command.startsWith("login ")) {
                String[] parts = command.split(" ", 3);
                if (parts.length == 3) {
                    String user = parts[1];
                    String pass = parts[2];

                    User authenticatedUser = authModel.login(user, pass);
                    if (authenticatedUser != null) {
                        this.currentUser = authenticatedUser;
                        out.println("AUTH_SUCCESS");
                        logger.accept("Người dùng '" + user + "' đăng nhập thành công.");
                    } else {
                        out.println("AUTH_FAILED");
                        logger.accept("Đăng nhập thất bại cho tài khoản: '" + user + "'.");
                    }
                }
                return;
            }

            if (command.equalsIgnoreCase("login_anonymous")) {
                User anonUser = authModel.loginAnonymous();
                if (anonUser != null) {
                    this.currentUser = anonUser;
                    out.println("AUTH_SUCCESS");
                    logger.accept("Khách truy cập ẩn danh đã kết nối.");
                } else {
                    out.println("AUTH_FAILED");
                    logger.accept("Chế độ ẩn danh hiện bị khóa trên hệ thống.");
                }
                return;
            }

            if (command.startsWith("register ")) {
                String[] parts = command.split(" ", 4);
                if (parts.length == 4) {
                    boolean success = authModel.register(parts[1], parts[2], parts[3]);
                    out.println(success ? "REGISTER_SUCCESS" : "REGISTER_FAILED");
                }
                return;
            }

            if (currentUser == null) {
                out.println("UNAUTHORIZED");
                return;
            }

            if (command.startsWith("list")) {
                sendFileList();
            } else if (command.startsWith("upload ")) {
                if (!currentUser.isCanUpload()) {
                    out.println("PERMISSION_DENIED_UPLOAD");
                    logger.accept("Từ chối Upload đối với người dùng: " + currentUser.getUsername());
                    return;
                }
                receiveFile(command.substring(7));
            } else if (command.startsWith("download ")) {
                if (!currentUser.isCanDownload()) {
                    out.println("PERMISSION_DENIED_DOWNLOAD");
                    logger.accept("Từ chối Download đối với người dùng: " + currentUser.getUsername());
                    return;
                }
                sendFile(command.substring(9));
            } else if (command.startsWith("delete ")) {
                deleteFile(command.substring(7));
            }
        }

        private void sendFileList() {
            File dir = new File(getUserWorkingDir());
            if (!dir.exists()) dir.mkdirs();

            String[] files = dir.list();
            if (files != null) {
                for (String file : files) out.println(file);
            }
            out.println("END_OF_LIST");
            logger.accept("Đã gửi danh sách tệp cho người dùng: " + currentUser.getUsername());
        }

        private void receiveFile(String fileName) {
            try {
                long fileSize = dataIn.readLong();
                if (fileSize < 0) return;

                File workDir = new File(getUserWorkingDir());
                if (!workDir.exists()) workDir.mkdirs();

                File file = new File(workDir, fileName);
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    byte[] buffer = new byte[131072];
                    int bytesRead;
                    long receivedBytes = 0;

                    while (receivedBytes < fileSize && (bytesRead = dataIn.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                        receivedBytes += bytesRead;
                    }
                }

                out.println("UPLOAD_SUCCESS");
                logger.accept("Tải lên thành công từ " + currentUser.getUsername() + ": " + fileName);
            } catch (IOException e) {
                out.println("UPLOAD_ERROR");
                logger.accept("Lỗi khi nhận tệp từ " + currentUser.getUsername() + ": " + fileName);
            }
        }

        private void sendFile(String fileName) {
            File file = new File(getUserWorkingDir(), fileName);
            try {
                if (!file.exists()) {
                    dataOut.writeLong(-1);
                    return;
                }

                dataOut.writeLong(file.length());
                try (FileInputStream fileIn = new FileInputStream(file)) {
                    byte[] buffer = new byte[65536];
                    int bytesRead;
                    while ((bytesRead = fileIn.read(buffer)) > 0) {
                        dataOut.write(buffer, 0, bytesRead);
                    }
                }
                logger.accept("Đã gửi tệp cho " + currentUser.getUsername() + ": " + fileName);
            } catch (IOException e) {
                logger.accept("Lỗi khi gửi tệp cho " + currentUser.getUsername() + ": " + fileName);
            }
        }

        private void deleteFile(String fileName) {
            File file = new File(getUserWorkingDir(), fileName);
            if (file.exists() && file.delete()) {
                logger.accept("Tệp đã bị xóa bởi " + currentUser.getUsername() + ": " + fileName);
            } else {
                logger.accept("Xóa tệp thất bại bởi " + currentUser.getUsername() + ": " + fileName);
            }
        }
    }
}