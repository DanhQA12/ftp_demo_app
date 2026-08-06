package server.controller;

import common.FTPResponseCode;
import server.dao.TransferLogDAO;
import server.dao.FileShareDAO;
import server.model.AuthModel;
import server.model.User;
import server.util.SecurityUtil;

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

    private User currentUser = null;
    private String tempUsername = null;
    private File currentWorkingDir;
    private File userRootDir;

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
            case "USER": handleUser(args); break;
            case "PASS": handlePass(args); break;
            case "PWD":  handlePwd(); break;
            case "CWD":  handleCwd(args); break;
            case "PASV": handlePasv(); break;
            case "LIST": handleList(); break;
            case "RETR": handleRetr(args); break;
            case "STOR": handleStor(args); break;
            case "MKD":  handleMkd(args); break;
            case "DELE": handleDele(args); break;
            case "SHARE": handleShare(args); break;
            case "LIST_SHARED": handleListShared(); break;
            case "QUIT":
                sendResponse(FTPResponseCode.CLOSING_DATA, "Goodbye.");
                closeConnections();
                break;
            default:
                sendResponse(FTPResponseCode.SYNTAX_ERROR, "Lệnh không được hỗ trợ.");
                break;
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
            currentUser = authModel.loginAnonymous();
        } else {
            currentUser = authModel.login(tempUsername, password);
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
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }
        sendResponse(FTPResponseCode.COMMAND_OK, "\"/\" is current directory.");
    }

    private void handleCwd(String dirName) {
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }

        File newDir;
        if (dirName.equals("/")) {
            newDir = userRootDir;
        } else if (dirName.startsWith("/")) {
            String cleanPath = dirName.substring(1);
            newDir = new File(userRootDir, cleanPath); // ghép vào userRootDir, KHÔNG phải serverRootDir
        } else {
            newDir = new File(currentWorkingDir, dirName);
        }

        if (newDir.exists() && newDir.isDirectory()) {
            currentWorkingDir = newDir;
            sendResponse(FTPResponseCode.DIR_CHANGED, "Directory successfully changed.");
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Directory not found or access denied.");
        }
    }

    private void handlePasv() {
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }
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
        File file = new File(currentWorkingDir, fileName);
        new DataTransferThread(dataServerSocket, writer, common.FTPCommand.RETR, file, currentUser, transferLogDAO).start();
        dataServerSocket = null;
    }

    private void handleStor(String fileName) {
        if (!prepareDataTransfer()) return;
        File file = new File(currentWorkingDir, fileName);
        new DataTransferThread(dataServerSocket, writer, common.FTPCommand.STOR, file, currentUser, transferLogDAO).start();
        dataServerSocket = null;
    }

    private void handleMkd(String dirName) {
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }
        File newDir = new File(currentWorkingDir, dirName);
        // Tạm gỡ SecurityUtil.isPathSafe để tránh xung đột khi tạo thư mục trong public_anonymous
        if (newDir.mkdirs()) {
            sendResponse(FTPResponseCode.COMMAND_OK, "MKDIR_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã tạo thư mục: " + dirName);
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Failed to create dir.");
        }
    }

    private void handleDele(String fileName) {
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }
        File target = new File(currentWorkingDir, fileName);
        if (target.exists() && target.delete()) {
            sendResponse(FTPResponseCode.COMMAND_OK, "DELETE_SUCCESS");
            logger.accept(currentUser.getUsername() + " đã xóa: " + fileName);
        } else {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Failed to delete.");
        }
    }

    private void handleShare(String args) {
        if (currentUser == null) { sendResponse(FTPResponseCode.NOT_LOGGED_IN); return; }
        String[] parts = args.split("\\|", 2);
        if (parts.length < 2) {
            sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Invalid syntax");
            return;
        }

        String fileName = parts[0].trim();
        String targetUser = parts[1].trim();
        int targetId = shareDAO.getUserIdByUsername(targetUser);

        if (targetId != -1) {
            boolean ok = shareDAO.createNotification(
                    currentUser.getUserId(), targetId, "Share",
                    "Tệp: " + fileName + " (Được chia sẻ bởi " + currentUser.getUsername() + ")"
            );
            if (ok) {
                sendResponse(FTPResponseCode.COMMAND_OK, "SHARE_SUCCESS");
                logger.accept(currentUser.getUsername() + " đã chia sẻ '" + fileName + "' cho " + targetUser);
                return;
            }
        }
        sendResponse(FTPResponseCode.FILE_ACTION_NOT_TAKEN, "Share failed.");
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
                ? "public_anonymous" : "users/" + currentUser.getUsername();
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
        } catch (IOException ignored) {}
    }
}