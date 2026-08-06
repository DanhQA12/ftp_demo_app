package server.controller;

import common.FTPCommand;
import common.FTPResponseCode;
import common.FileItem;
import server.dao.TransferLogDAO;
import server.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;

public class DataTransferThread extends Thread {
    private final ServerSocket dataServerSocket;
    private final PrintWriter controlWriter; // Để gửi mã 150 và 226 về Kênh Control
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
        try (Socket dataSocket = dataServerSocket.accept()) { // Chờ Client kết nối vào cổng PASV
            // Ngay khi Client kết nối, báo qua kênh Control là bắt đầu truyền
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

            // Truyền xong, báo qua kênh Control là thành công
            sendControlResponse(FTPResponseCode.CLOSING_DATA);

        } catch (IOException e) {
            sendControlResponse(FTPResponseCode.DATA_CONN_FAILED, "Transfer failed or aborted.");
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

        if (files != null) {
            for (File file : files) {
                // Tạo đối tượng FileItem và dùng hàm toString() để giả lập chuẩn FTP LIST (Unix-style)
                FileItem item = new FileItem(
                        file.getName(),
                        file.isDirectory() ? 0 : file.length(),
                        sdf.format(file.lastModified()),
                        file.isDirectory()
                );
                dataWriter.println(item.toString());
            }
        }
    }

    private void processDownload(Socket dataSocket) throws IOException {
        long fileSize = targetFile.length();
        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(targetFile));
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[65536]; // Buffer 64KB
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
            dataOut.flush();

            // Ghi nhật ký vào DB
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), fileSize, "RETR", true);
        }
    }

    private void processUpload(Socket dataSocket) throws IOException {
        // Upload cần cẩn thận vì đang ghi file lên máy chủ
        long totalReceived = 0;
        try (InputStream dataIn = dataSocket.getInputStream();
             BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile))) {

            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalReceived += bytesRead;
            }
            fileOut.flush();

            // Ghi nhật ký & Cập nhật Quota
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), totalReceived, "STOR", true);
            logDAO.updateUsedStorage(user.getUserId(), totalReceived);

        } catch (IOException e) {
            // Nếu đứt mạng giữa chừng, xóa file rác và log lỗi
            if (targetFile.exists()) targetFile.delete();
            logDAO.logTransfer(user.getUserId(), targetFile.getName(), totalReceived, "STOR", false);
            throw e;
        }
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