package server.controller;

import common.FTPResponseCode;
import server.dao.NotificationDAO;
import server.dao.TransferLogDAO;
import server.dao.FileShareDAO;
import server.model.AuthModel;
import server.model.User;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientControlThread extends Thread {
    private final Socket controlSocket;
    private final String serverRootDir;
    private final AuthModel authModel;
    private final Consumer<String> logger;

    private BufferedReader reader;
    private PrintWriter writer;
    private ServerSocket dataServerSocket = null;
    private final TransferLogDAO transferLogDAO = new TransferLogDAO();
    private final FileShareDAO shareDAO = new FileShareDAO();
    private final NotificationDAO notifDAO = new NotificationDAO();

    private User currentUser = null;
    private String tempUsername = null;
    private File currentWorkingDir;
    private File userRootDir;
    private File renameFromTarget = null;


    public ClientControlThread(Socket socket, String serverRootDir, AuthModel authModel, Consumer<String> logger) {
        this.controlSocket = socket;
        this.serverRootDir = serverRootDir;
        this.authModel = authModel;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream()), true);

            String clientIp = controlSocket.getInetAddress().getHostAddress();
            logger.accept("Kết nối mới (Kênh Control) từ: " + clientIp);

            sendResponse(FTPResponseCode.READY);

            String line;
            while ((line = reader.readLine()) != null) {
                processCommand(line);
            }
        } catch (IOException e) {
            logger.accept("Mất kết nối với Client: " + controlSocket.getInetAddress());
        } finally {
            closeConnections();
        }
    }

    private void processCommand(String line) {
        String[] parts = line.split(" ", 2);
        String cmdStr = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (cmdStr) {
            case "USER":
                handleUser(args);
                break;
            case "PASS":
                handlePass(args);
                break;
            case "PWD":
                handlePwd();
                break;
            case "CWD":
                handleCwd(args);
                break;
            case "PASV":
                handlePasv();
                break;
            case "LIST":
                handleList();
                break;
            case "RETR":
                handleRetr(args);
                break;
            case "STOR":
                handleStor(args);
                break;
            case "MKD":
                handleMkd(args);
                break;
            case "DELE":
                handleDele(args);
                break;
            case "SHARE":
                handleShare(args);
                break;
            case "LIST_SHARED":
                handleListShared();
                break;
            case "UNSHARE":
                handleUnshare(args);
                break;
            case "RNFR":
                handleRnfr(args);
                break;
            case "RNTO":
                handleRnto(args);
                break;
            case "QUOTA":
                handleQuota();
                break;
            case "GET_NOTIFS":
                handleGetNotifs();
                break;
            case "SET_PERM":
                handleSetPerm(args);
                break;
            case "QUIT":
                sendResponse(FTPResponseCode.CLOSING_DATA, "Goodbye.");
                closeConnections();
                break;
            default:
                sendResponse(FTPResponseCode.SYNTAX_ERROR, "Lệnh không được hỗ trợ.");
                break;
        }
    }

    // Admin -> full quyền. User thường -> KHÔNG được xem "/users"
    private boolean isAccessAllowed(File target) {
        boolean isAdmin = currentUser.getRoleName() != null && currentUser.getRoleName().equalsIgnoreCase("Admin");
        if (isAdmin) return true;

        String targetPath = target.getAbsolutePath().replace("\\", "/");
        String usersRoot = new File(serverRootDir, "users").getAbsolutePath().replace("\\", "/");
        String ownPath = userRootDir.getAbsolutePath().replace("\\", "/");

        if (targetPath.equals(usersRoot)) return false; // chặn xem thư mục cha chứa mọi user

        if (targetPath.startsWith(usersRoot + "/") && !targetPath.startsWith(ownPath)) {
            return false; // chặn vào thư mục riêng của NGƯỜI KHÁC
        }

        return true; // Cho phép xem Public / Shared của chính mình
    }

    private boolean prepareDataTransfer() {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return false;
        }
        if (dataServerSocket == null || dataServerSocket.isClosed()) {
            sendResponse(FTPResponseCode.DATA_CONN_FAILED, "Use PASV first.");
            return false;
        }
        return true;
    }

    private void setupUserEnvironment() {
        String dirName = "anonymous".equalsIgnoreCase(currentUser.getUsername())
                ? "public" : "users/" + currentUser.getUsername();
        userRootDir = new File(serverRootDir, dirName);
        if (!userRootDir.exists()) userRootDir.mkdirs();
        currentWorkingDir = userRootDir;
    }

    private void sendResponse(FTPResponseCode code) {
        writer.print(code.format());
        writer.flush();
    }

    private void sendResponse(FTPResponseCode code, String customMessage) {
        writer.print(code.format(customMessage));
        writer.flush();
    }

    private void closeConnections() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void handlePasv() {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }
        try {
            if (dataServerSocket != null && !dataServerSocket.isClosed()) dataServerSocket.close();
            dataServerSocket = new ServerSocket(0);

            String ip = controlSocket.getLocalAddress().getHostAddress();
            if (ip.equals("0.0.0.0") || ip.equals("127.0.0.1")) ip = "127.0.0.1";

            int port = dataServerSocket.getLocalPort();
            String[] ipParts = ip.split("\\.");
            int p1 = port / 256;
            int p2 = port % 256;

            sendResponse(FTPResponseCode.ENTERING_PASSIVE, String.format("Entering Passive Mode (%s,%s,%s,%s,%d,%d)",
                    ipParts[0], ipParts[1], ipParts[2], ipParts[3], p1, p2));
        } catch (IOException e) {
            sendResponse(FTPResponseCode.DATA_CONN_FAILED);
        }
    }

    private void handleList() {
        if (!prepareDataTransfer()) return;
        new DataTransferThread(dataServerSocket, writer, common.FTPCommand.LIST, currentWorkingDir, currentUser, transferLogDAO).start();
        dataServerSocket = null;
    }

    private void handleRetr(String fileName) {
        if (!prepareDataTransfer()) return;

        // 1. Kiểm tra block chức năng Download
        if (!currentUser.isCanDownload()) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Tài khoản của bạn đã bị khóa chức năng Download.");
            return;
        }

        File file = new File(currentWorkingDir, fileName);
        new DataTransferThread(dataServerSocket, writer, common.FTPCommand.RETR, file, currentUser, transferLogDAO).start();
        dataServerSocket = null;
    }

    private void handleStor(String fileName) {
        if (!prepareDataTransfer()) return;

        if (!canModifyDirectory()) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Thư mục này là Read-Only, bạn không có quyền tạo hoặc tải file lên.");
            return;
        }

        // 1. Kiểm tra block chức năng Upload
        if (!currentUser.isCanUpload()) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Tài khoản của bạn đã bị khóa chức năng Upload.");
            return;
        }

        // 2. Chặn tk anonymous không được upload
        if ("anonymous".equalsIgnoreCase(currentUser.getUsername())) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Anonymous chỉ được phép tải xuống.");
            return;
        }

        File file = new File(currentWorkingDir, fileName);
        new DataTransferThread(dataServerSocket, writer, common.FTPCommand.STOR, file, currentUser, transferLogDAO).start();
        dataServerSocket = null;
    }

    private void handleMkd(String dirName) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        if (!canModifyDirectory()) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Thư mục này là Read-Only, bạn không thể tạo Folder.");
            return;
        }

        if ("anonymous".equalsIgnoreCase(currentUser.getUsername())) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Anonymous không thể tạo Folder ở đây.");
            return;
        }

        File newDir = new File(currentWorkingDir, dirName);
        if (newDir.mkdirs()) {
            sendResponse(FTPResponseCode.COMMAND_OK, "MKDIR_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã tạo Folder: " + dirName);
            new server.dao.FileDAO().addFileRecord(currentUser.getUserId(), dirName, newDir.getAbsolutePath(), true, 0);
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Tạo Folder không thành công.");
        }
    }

    private void handleDele(String fileName) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        if ("anonymous".equalsIgnoreCase(currentUser.getUsername())) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Anonymous không có quyền xóa.");
            return;
        }

        File target = new File(currentWorkingDir, fileName);

        // Kiểm tra quyền: Admin được xóa tất cả. User thường chỉ được xóa file do chính họ tạo ra
        boolean isAdmin = currentUser.getRoleName() != null && currentUser.getRoleName().equalsIgnoreCase("Admin");

        if (!isAdmin) {
            // Kiểm tra trong DB xem file này có thuộc sở hữu của currentUser không
            int ownerId = new server.dao.FileDAO().getFileOwnerId(target.getAbsolutePath());
            if (ownerId != -1 && ownerId != currentUser.getUserId()) {
                sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Permission denied: Bạn chỉ có quyền xóa file do chính bạn tải lên.");
                return;
            }
        }

        if (target.exists() && target.delete()) {
            sendResponse(FTPResponseCode.COMMAND_OK, "DELETE_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã xóa: " + fileName);
            new server.dao.FileDAO().deleteFileRecord(target.getAbsolutePath());
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Xóa thất bại.");
        }
    }

    private void handleShare(String args) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        // Client sẽ gửi: Tên_File | Username_Người_Nhận | Quyền (READ_ONLY/FULL_CONTROL)
        String[] parts = args.split("\\|");
        if (parts.length < 3) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Sai cú pháp");
            return;
        }

        String fileName = parts[0].trim();
        String targetUser = parts[1].trim();
        String permType = parts[2].trim();

        // Tìm ID người nhận
        int targetId = shareDAO.getUserIdByUsername(targetUser);
        if (targetId == -1) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Không tìm thấy user.");
            return;
        }

        // Tìm ID của File trong DB
        File targetFile = new File(currentWorkingDir, fileName);
        int fileId = shareDAO.getFileIdByPath(targetFile.getAbsolutePath());
        if (fileId == -1) {
            // Nếu DB chưa có file này, ép tạo mới vào DB
            new server.dao.FileDAO().addFileRecord(currentUser.getUserId(), fileName, targetFile.getAbsolutePath(), targetFile.isDirectory(), targetFile.length());
            fileId = shareDAO.getFileIdByPath(targetFile.getAbsolutePath());
            if (fileId == -1) {
                sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Lỗi đồng bộ file DB.");
                return;
            }
        }

        //  Phân quyền
        int permId = "FULL_CONTROL".equals(permType) ? 2 : 1;

        // Lưu vào DB Share
        boolean ok = shareDAO.shareFileToUser(fileId, targetId, permId, currentUser.getUserId());

        if (ok) {
            sendResponse(FTPResponseCode.COMMAND_OK, "SHARE_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã chia sẻ '" + fileName + "' cho " + targetUser + " (Quyền: " + permType + ")");
            notifDAO.addNotification(targetId, currentUser.getUserId(), "Chia sẻ file",
                    "User " + currentUser.getUsername() + " đã chia sẻ file '" + fileName + "' cho bạn.");
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Share failed in DB.");
        }

    }

    private void handleUser(String username) {
        this.tempUsername = username;
        sendResponse(FTPResponseCode.NEED_PASSWORD);
    }

    private void handlePass(String password) {
        if (tempUsername == null) {
            sendResponse(FTPResponseCode.SYNTAX_ERROR, "Cần gửi USER trước.");
            return;
        }
        if ("anonymous".equalsIgnoreCase(tempUsername)) {
            // Kiểm tra xem hệ thống có mở khóa Anonymous không (Bạn cần viết hàm lấy cờ này từ DB Server_Settings)
            boolean isAnonymousEnabled = true;
            if (!isAnonymousEnabled) {
                sendResponse(FTPResponseCode.NOT_LOGGED_IN, "Server đã khóa tính năng Anonymous.");
                tempUsername = null;
                return;
            }
            currentUser = authModel.loginAnonymous();
        } else {
            currentUser = authModel.login(tempUsername, password);
            // Kiểm tra thêm quyền allow_anonymous của user bất kỳ nếu họ đang cố truy cập thư mục chung
        }

        if (currentUser != null) {
            setupUserEnvironment();
            sendResponse(FTPResponseCode.LOGGED_IN);
            logger.accept("User '" + currentUser.getUsername() + "' đã đăng nhập.");
        } else {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN, "Sai mật khẩu.");
        }
        tempUsername = null;
    }

    private void handlePwd() {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }
        sendResponse(FTPResponseCode.COMMAND_OK, "\"/\" là thư mục hiện tại.");
    }

    private void handleCwd(String dirName) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        File newDir;
        if (dirName.equals("/")) {
            newDir = new File(serverRootDir);
        } else if (dirName.startsWith("/")) {
            newDir = new File(serverRootDir, dirName.substring(1));
        } else {
            newDir = new File(currentWorkingDir, dirName);
        }

        if (!newDir.exists() || !newDir.isDirectory()) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Không tìm thấy Folder hoặc truy cập bị từ chối.");
            return;
        }

        if (!isAccessAllowed(newDir)) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Không có quyền truy cập thư mục này.");
            return;
        }

        currentWorkingDir = newDir;
        sendResponse(FTPResponseCode.DIR_CHANGED, "Thay đổi Folder thành công.");
    }

    private void handleGetNotifs() {
        if (currentUser == null || "anonymous".equalsIgnoreCase(currentUser.getUsername())) {
            sendResponse(FTPResponseCode.COMMAND_OK, "0"); // Không có thông báo
            return;
        }

        java.util.List<String> unread = notifDAO.getAndMarkUnreadNotifications(currentUser.getUserId());
        if (unread.isEmpty()) {
            sendResponse(FTPResponseCode.COMMAND_OK, "0");
        } else {
            writer.println("213-You have " + unread.size() + " new notifications:");
            for (String msg : unread) {
                writer.println(msg);
            }
            writer.println("213 End of notifications.");
            writer.flush();
        }
    }

    private void handleListShared() {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        java.util.List<String> sharedFiles = shareDAO.getSharedFilesForUser(currentUser.getUserId());

        for (String fileData : sharedFiles) {
            writer.println(fileData);
        }
        writer.println("END_OF_LIST");
        writer.flush();
    }

    private void handleUnshare(String fileName) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        if (shareDAO.removeShare(currentUser.getUserId(), fileName)) {
            sendResponse(FTPResponseCode.COMMAND_OK, "UNSHARE_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã gỡ file chia sẻ: " + fileName);
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Khong tim thay file chia se hoac loi xoa.");
        }
    }

    private void handleRnfr(String path) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        File target;
        if (path.startsWith("/")) target = new File(serverRootDir, path.substring(1));
        else target = new File(currentWorkingDir, path);

        if (target.exists()) {
            renameFromTarget = target;
            writer.println("350 Ready for RNTO.");
            writer.flush();
        } else {
            writer.println("550 File not found.");
            writer.flush();
        }
    }

    private void handleRnto(String path) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        if (!canModifyDirectory()) {
            writer.println("550 Permission denied: Thư mục đích là Read-Only.");
            writer.flush();
            renameFromTarget = null;
            return;
        }

        if (renameFromTarget == null) {
            writer.println("503 Bad sequence of commands.");
            writer.flush();
            return;
        }

        File target;
        if (path.startsWith("/")) target = new File(serverRootDir, path.substring(1));
        else target = new File(currentWorkingDir, path);

        // Kế thừa luật phân quyền: Admin toàn quyền, User thường chỉ di chuyển được file của mình
        boolean isAdmin = currentUser.getRoleName() != null && currentUser.getRoleName().equalsIgnoreCase("Admin");
        if (!isAdmin) {
            int ownerId = new server.dao.FileDAO().getFileOwnerId(renameFromTarget.getAbsolutePath());
            if (ownerId != -1 && ownerId != currentUser.getUserId()) {
                writer.println("550 Permission denied: Chi co the di chuyen file cua ban.");
                writer.flush();
                renameFromTarget = null;
                return;
            }
        }

        try {
            // Sử dụng Files.move của NIO để ép buộc di chuyển, tránh lỗi kẹt file trên Windows
            Files.move(renameFromTarget.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            new server.dao.FileDAO().deleteFileRecord(renameFromTarget.getAbsolutePath());
            new server.dao.FileDAO().addFileRecord(currentUser.getUserId(), target.getName(), target.getAbsolutePath(), target.isDirectory(), target.length());

            writer.println("250 Rename/Move successful.");
            writer.flush();
            logger.accept(currentUser.getUsername() + " da di chuyen file toi " + target.getName());
        } catch (IOException e) {
            writer.println("553 Rename/Move failed: " + e.getMessage());
            writer.flush();
        }
        renameFromTarget = null;
    }

    private void handleQuota() {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        // Nhận diện Admin trực tiếp qua Username (chắc chắn 100% thay vì dựa vào RoleName)
        boolean isAdmin = "admin".equalsIgnoreCase(currentUser.getUsername());

        long used = 0;
        long max = isAdmin ? -1 : 0; // Nếu là Admin thì luôn là -1 (Vô hạn)

        String sql = "SELECT used_storage, max_storage FROM user_storage WHERE user_id = ?";
        try (java.sql.Connection conn = server.config.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, currentUser.getUserId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    used = rs.getLong("used_storage");
                    if (!isAdmin) {
                        max = rs.getLong("max_storage");
                    }
                } else if (!isAdmin) {
                    // Nếu tài khoản lỡ bị thiếu record trong DB, cấp tạm mức an toàn 5GB để không bị lỗi UI
                    max = 5368709120L;
                }
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }

        writer.println("213 QUOTA " + used + " " + max);
        writer.flush();
    }

    private void handleSetPerm(String args) {
        if (currentUser == null) {
            sendResponse(FTPResponseCode.NOT_LOGGED_IN);
            return;
        }

        String[] parts = args.split("\\|");
        if (parts.length < 2) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Sai cú pháp. Yêu cầu: Tên_File|Quyền");
            return;
        }

        String fileName = parts[0].trim();
        String permType = parts[1].trim(); // Nhận "FULL_CONTROL" hoặc "READ_ONLY"

        File targetFile = new File(currentWorkingDir, fileName);
        String absolutePath = targetFile.getAbsolutePath();

        server.dao.FileDAO fileDAO = new server.dao.FileDAO();

        // 1. Kiểm tra xem file đã được đồng bộ trong CSDL chưa
        int fileId = fileDAO.getFileIdByPath(absolutePath);
        if (fileId == -1) {
            if (targetFile.exists()) {
                // Tự động thêm file vào DB nếu nó tồn tại vật lý nhưng chưa có trong DB
                fileDAO.addFileRecord(currentUser.getUserId(), fileName, absolutePath, targetFile.isDirectory(), targetFile.length());
            } else {
                sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "File không tồn tại trên Server.");
                return;
            }
        }

        // 2. Quyền Admin được phép đổi, hoặc Chủ sở hữu được phép đổi
        boolean isAdmin = currentUser.getRoleName() != null && currentUser.getRoleName().equalsIgnoreCase("Admin");
        if (!isAdmin) {
            int ownerId = fileDAO.getFileOwnerId(absolutePath);
            if (ownerId != -1 && ownerId != currentUser.getUserId()) {
                sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Từ chối: Bạn không phải chủ sở hữu file này.");
                return;
            }
        }

        // 3. Quan trọng nhất: CẬP NHẬT QUYỀN VÀO CƠ SỞ DỮ LIỆU
        // Lệnh LIST đọc quyền từ DB, nên phải update vào DB thì Client mới thấy thay đổi
        boolean ok = fileDAO.updateFilePermission(absolutePath, permType);

        if (ok) {
            sendResponse(FTPResponseCode.COMMAND_OK, "SET_PERM_SUCCESS");
            logger.accept("User " + currentUser.getUsername() + " đã đổi quyền file '" + fileName + "' thành " + permType);
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Lỗi cập nhật CSDL.");
        }
    }

    private boolean canModifyDirectory() {
        // 1. Nếu là Admin thì luôn được toàn quyền
        boolean isAdmin = currentUser.getRoleName() != null && currentUser.getRoleName().equalsIgnoreCase("Admin");
        if (isAdmin) return true;

        String currentPath = currentWorkingDir.getAbsolutePath().replace("\\", "/");

        // 2. Nếu đang ở trong thư mục cá nhân của chính mình thì được toàn quyền
        String personalPath = new File(serverRootDir, "users/" + currentUser.getUsername()).getAbsolutePath().replace("\\", "/");
        if (currentPath.equals(personalPath) || currentPath.startsWith(personalPath + "/")) {
            return true;
        }

        // 3. KIỂM TRA PHÂN CẤP TỪ TRÊN XUỐNG (Thực hiện quét từ thư mục hiện tại ngược lên gốc /public)
        File checkDir = currentWorkingDir;
        while (checkDir != null && checkDir.exists()) {
            int fileId = shareDAO.getFileIdByPath(checkDir.getAbsolutePath());
            if (fileId != -1) {
                String sql = "SELECT pt.permission_name FROM file_permissions fp " +
                        "JOIN permission_types pt ON fp.permission_type_id = pt.permission_type_id " +
                        "WHERE fp.file_id = ? ORDER BY fp.granted_at DESC LIMIT 1";
                try (java.sql.Connection conn = server.config.DatabaseConnection.getConnection();
                     java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, fileId);
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String perm = rs.getString("permission_name");
                            // Nếu tìm thấy cấu hình ở cấp thư mục hiện tại hoặc thư mục cha, áp dụng ngay lập tức
                            if ("FULL_CONTROL".equalsIgnoreCase(perm)) {
                                return true;
                            } else if ("READ_ONLY".equalsIgnoreCase(perm)) {
                                return false;
                            }
                        }
                    }
                } catch (java.sql.SQLException e) {
                    e.printStackTrace();
                }
            }

            // Nếu đã duyệt đến thư mục gốc public thì dừng lại
            if (checkDir.getAbsolutePath().replace("\\", "/").equals(new File(serverRootDir, "public").getAbsolutePath().replace("\\", "/"))) {
                break;
            }
            checkDir = checkDir.getParentFile();
        }

        // 4. Mặc định nếu nằm trong /public mà không có bất kỳ cấu hình FULL_CONTROL nào từ cha ông thì là Read-Only
        return !currentPath.contains("/public");
    }
}