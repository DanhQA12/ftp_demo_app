package server.controller;

import common.FTPCommand;
import common.FTPResponseCode;
import common.FileItem;
import server.config.DatabaseConnection;
import server.dao.TransferLogDAO;
import server.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

public class DataTransferThread extends Thread {
    private final ServerSocket dataServerSocket;
    private final PrintWriter controlWriter;
    private final FTPCommand command;
    private final File targetFile;
    private final User user;
    private final TransferLogDAO logDAO;

    public DataTransferThread(ServerSocket dataServerSocket, PrintWriter controlWriter,
                              FTPCommand command, File targetFile, User user, TransferLogDAO logDAO) {
        this.dataServerSocket = dataServerSocket;
        this.controlWriter = controlWriter;
        this.command = command;
        this.targetFile = targetFile;
        this.user = user;
        this.logDAO = logDAO;
    }

    @Override
    public void run() {
        try (Socket dataSocket = dataServerSocket.accept()) {
            sendControlResponse(FTPResponseCode.DATA_OPEN);

            switch (command) {
                case LIST:
                    processList(dataSocket);
                    break;
                case RETR:
                    processDownload(dataSocket);
                    break;
                case STOR:
                    processUpload(dataSocket);
                    break;
            }

            sendControlResponse(FTPResponseCode.CLOSING_DATA);

        } catch (IOException e) {
            sendControlResponse(FTPResponseCode.DATA_CONN_FAILED, "Transfer failed or aborted: " + e.getMessage());
        } finally {
            try {
                if (dataServerSocket != null && !dataServerSocket.isClosed()) {
                    dataServerSocket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    private void processList(Socket dataSocket) throws IOException {
        PrintWriter dataWriter = new PrintWriter(new OutputStreamWriter(dataSocket.getOutputStream()), true);
        File[] files = targetFile.listFiles();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        server.dao.FileShareDAO shareDAO = new server.dao.FileShareDAO();

        if (files != null) {
            for (File file : files) {
                String type = file.isDirectory() ? "d" : "f";
                long size = file.isDirectory() ? 0 : file.length();
                String date = sdf.format(file.lastModified());
                String name = file.getName();

                // Lấy owner và permission từ DB
                String owner = getFileOwner(file);
                String permType = shareDAO.getFilePermissionType(file.getAbsolutePath());

                dataWriter.println(type + "|" + size + "|" + date + "|" + name + "|" + owner + "|" + permType);
            }
        }
    }

    // Truy vấn DB để lấy tên người tạo
    private String getFileOwner(File f) {
        String sql = "SELECT u.username FROM files f JOIN users u ON f.owner_id = u.user_id WHERE f.file_path = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, f.getAbsolutePath());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Backup heuristic (Nếu lỡ file vật lý có mà DB mất record)
        String path = f.getAbsolutePath().replace("\\", "/");
        if (path.contains("/users/")) {
            String[] parts = path.split("/users/");
            if (parts.length > 1) return parts[1].split("/")[0]; // Trích xuất tên user từ URL
        }
        return "system";
    }

    private void processDownload(Socket dataSocket) throws IOException {
        long fileSize = targetFile.length();
        long maxDownloadSize = getMaxDownloadSizeLimit();

        // 1. Kiểm tra dung lượng file so với max_download_size trong server_settings
        if (maxDownloadSize > 0 && fileSize > maxDownloadSize) {
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), fileSize, "RETR", false);
            throw new IOException("Kích thước file vượt quá giới hạn tải xuống tối đa (" + (maxDownloadSize / 1024 / 1024) + "MB)");
        }

        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(targetFile));
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();

            logDAO.logTransfer(user.getUserId(), targetFile.getName(), fileSize, "RETR", true);
        }
    }

    private void processUpload(Socket dataSocket) throws IOException {
        long totalReceived = 0;
        long maxUploadSize = getMaxUploadSizeLimit();
        long availableQuota = getAvailableUserQuota();

        // Giới hạn thực tế là con số nhỏ hơn giữa max_upload_size của Server và Quota còn lại của User
        long maxAllowedSize = (maxUploadSize > 0) ? Math.min(maxUploadSize, availableQuota) : availableQuota;

        try (InputStream dataIn = dataSocket.getInputStream();
             BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile))) {

            byte[] buffer = new byte[65536];
            int bytesRead;

            while ((bytesRead = dataIn.read(buffer)) != -1) {
                totalReceived += bytesRead;

                // 2. Kiểm tra khi luồng nhận byte nếu vượt quá giới hạn thì ngắt ngay
                if (maxAllowedSize > 0 && totalReceived > maxAllowedSize) {
                    fileOut.flush();
                    fileOut.close();

                    if (targetFile.exists()) targetFile.delete(); // Xóa file dở dang
                    logDAO.logTransfer(user.getUserId(), targetFile.getName(), totalReceived, "STOR", false);

                    throw new IOException("File vượt quá kích thước cho phép hoặc dung lượng lưu trữ cá nhân đã đầy!");
                }

                fileOut.write(buffer, 0, bytesRead);
            }
            fileOut.flush();

            // Ghi nhật ký & Cập nhật Quota
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), totalReceived, "STOR", true);
            logDAO.updateUsedStorage(user.getUserId(), totalReceived);

            // Ghi đè và Lưu thông tin chủ sở hữu vào Database
            new server.dao.FileDAO().deleteFileRecord(targetFile.getAbsolutePath());
            new server.dao.FileDAO().addFileRecord(user.getUserId(), targetFile.getName(), targetFile.getAbsolutePath(), false, totalReceived);

        } catch (IOException e) {
            if (targetFile.exists()) targetFile.delete();
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), totalReceived, "STOR", false);
            throw e;
        }
    }

    // --- HÀM TRUY XUẤT DATABASE ---
    private long getMaxUploadSizeLimit() {
        String sql = "SELECT max_upload_size FROM server_settings LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong("max_upload_size");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 5368709120L; // Mặc định 5GB
    }

    private long getMaxDownloadSizeLimit() {
        String sql = "SELECT max_download_size FROM server_settings LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong("max_download_size");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 5368709120L; // Mặc định 5GB
    }

    private long getAvailableUserQuota() {
        boolean isAdmin = user.getRoleName() != null && user.getRoleName().equalsIgnoreCase("Admin");
        if (isAdmin) return Long.MAX_VALUE;

        String sql = "SELECT (max_storage - used_storage) AS available FROM user_storage WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long available = rs.getLong("available");
                    return Math.max(available, 0);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Long.MAX_VALUE; // Nếu là admin hoặc không tìm thấy storage record
    }

    private void sendControlResponse(FTPResponseCode code) {
        controlWriter.print(code.format());
        controlWriter.flush();
    }

    private void sendControlResponse(FTPResponseCode code, String msg) {
        controlWriter.print(code.format(msg));
        controlWriter.flush();
    }


}

