package client.model;

import client.view.TransferProgressDialog;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.BiConsumer;

public class FTPClientModel {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private boolean authenticated = false;

    public boolean connectAndLogin(String ip, int port, String user, String pass) throws IOException {
        socket = new Socket(ip, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        dataIn = new DataInputStream(socket.getInputStream());
        dataOut = new DataOutputStream(socket.getOutputStream());

        if ("anonymous".equalsIgnoreCase(user)) {
            out.println("login_anonymous");
        } else {
            out.println("login " + user + " " + pass);
        }

        String response = in.readLine();
        this.authenticated = "AUTH_SUCCESS".equals(response);
        return this.authenticated;
    }

    public boolean isAuthenticated() {
        return socket != null && socket.isConnected() && !socket.isClosed() && authenticated;
    }

    public List<String> fetchFileList() throws IOException {
        if (!isAuthenticated()) return Collections.emptyList();
        out.println("list");
        List<String> files = new ArrayList<>();
        String response;
        while ((response = in.readLine()) != null && !response.equals("END_OF_LIST")) {
            files.add(response);
        }
        return files;
    }

    public boolean uploadFile(File file, BiConsumer<Long, Long> progressCallback, TransferProgressDialog dialog) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa kết nối hoặc xác thực Server thất bại.");

        out.println("upload " + file.getName());
        dataOut.writeLong(file.length());

        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] buffer = new byte[131072];
            int bytesRead;
            long uploadedBytes = 0;

            while ((bytesRead = fileIn.read(buffer)) > 0) {
                // Kiểm tra trạng thái Tạm dừng / Hủy từ Pop-up Dialog
                if (dialog != null) {
                    while (dialog.isPaused() && !dialog.isCancelled()) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    if (dialog.isCancelled()) throw new IOException("Tiến trình đã bị người dùng hủy.");
                }

                dataOut.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;
                if (progressCallback != null) progressCallback.accept(uploadedBytes, file.length());
            }
            dataOut.flush();
        }

        String response = in.readLine();
        return "UPLOAD_SUCCESS".equals(response);
    }

    public void downloadFile(String fileName, File destinationFolder, BiConsumer<Long, Long> progressCallback, TransferProgressDialog dialog) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa kết nối hoặc xác thực Server thất bại.");

        out.println("download " + fileName);
        long fileSize = dataIn.readLong();
        if (fileSize == -1) throw new FileNotFoundException("Tệp không tồn tại trên máy chủ.");

        File saveFile = new File(destinationFolder, fileName);
        try (FileOutputStream fileOut = new FileOutputStream(saveFile)) {
            byte[] buffer = new byte[131072];
            int bytesRead;
            long downloadedBytes = 0;

            while (downloadedBytes < fileSize && (bytesRead = dataIn.read(buffer)) != -1) {
                // Kiểm tra trạng thái Tạm dừng / Hủy từ Pop-up Dialog
                if (dialog != null) {
                    while (dialog.isPaused() && !dialog.isCancelled()) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    if (dialog.isCancelled()) throw new IOException("Tiến trình đã bị người dùng hủy.");
                }

                fileOut.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;
                if (progressCallback != null) progressCallback.accept(downloadedBytes, fileSize);
            }
        }
    }

    public void deleteFile(String fileName) {
        if (isAuthenticated()) {
            out.println("delete " + fileName);
        }
    }

    public void disconnect() {
        authenticated = false;
        try {
            if (out != null) out.println("exit");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    // Gửi lệnh tạo thư mục mới trên Server
    public boolean createDirectory(String folderName) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");
        out.println("mkdir " + folderName);
        String response = in.readLine();
        return "MKDIR_SUCCESS".equals(response);
    }

    // Gửi lệnh chia sẻ tệp/thư mục cho User khác
    public boolean shareFile(String fileName, String targetUsername) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");
        out.println("share " + fileName + " " + targetUsername);
        String response = in.readLine();
        return "SHARE_SUCCESS".equals(response);
    }
}