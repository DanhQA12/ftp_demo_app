package client.model;

import client.view.TransferProgressDialog;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class FTPClientModel {
    private Socket controlSocket;
    private PrintWriter writer;
    private BufferedReader reader;
    private boolean authenticated = false;

    public boolean connectAndLogin(String ip, int port, String user, String pass) throws IOException {
        controlSocket = new Socket();
        controlSocket.connect(new java.net.InetSocketAddress(ip, port), 5000);
        controlSocket.setSoTimeout(10000);

        writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream()), true);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

        String response = reader.readLine();
        if (response == null || !response.startsWith("220")) return false;

        writer.println("USER " + user);
        response = reader.readLine();

        if (response != null && response.startsWith("331")) {
            writer.println("PASS " + pass);
            response = reader.readLine();
            this.authenticated = response != null && response.startsWith("230");
        } else {
            this.authenticated = response != null && response.startsWith("230");
        }

        return this.authenticated;
    }

    public boolean isAuthenticated() {
        return controlSocket != null && controlSocket.isConnected() && !controlSocket.isClosed() && authenticated;
    }

    public boolean changeWorkingDirectory(String path) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");
        writer.println("CWD " + path);
        String response = reader.readLine();
        return response != null && (response.startsWith("250") || response.startsWith("200"));
    }

    private Socket openDataConnection() throws IOException {
        writer.println("PASV");
        String response = reader.readLine();

        if (response == null || !response.startsWith("227")) {
            throw new IOException("Không thể chuyển sang Passive Mode: " + response);
        }

        int start = response.indexOf('(');
        int end = response.indexOf(')');
        if (start < 0 || end < 0) throw new IOException("Định dạng PASV không hợp lệ");

        String[] parts = response.substring(start + 1, end).split(",");
        String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        int dataPort = (Integer.parseInt(parts[4]) * 256) + Integer.parseInt(parts[5]);

        Socket dataSocket = new Socket();
        dataSocket.connect(new java.net.InetSocketAddress(ip, dataPort), 5000);
        dataSocket.setSoTimeout(5000);
        return dataSocket;
    }

    public List<String> fetchFileList() throws IOException {
        if (!isAuthenticated()) return Collections.emptyList();

        Socket dataSocket = openDataConnection();
        writer.println("LIST");

        String response = reader.readLine();
        if (response == null || (!response.startsWith("150") && !response.startsWith("125"))) {
            dataSocket.close();
            return Collections.emptyList();
        }

        List<String> files = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                files.add(line);
            }
        } catch (java.net.SocketTimeoutException e) {
            dataSocket.close();
            throw new IOException("Timeout: Server phản hồi quá chậm.");
        }

        reader.readLine();
        return files;
    }

    public List<String> fetchSharedFileList() throws IOException {
        if (!isAuthenticated()) return Collections.emptyList();
        writer.println("list_shared");
        List<String> files = new ArrayList<>();
        String response;
        while ((response = reader.readLine()) != null && !response.equals("END_OF_LIST")) {
            files.add(response);
        }
        return files;
    }

    // Tải tệp LÊN Server
    public boolean uploadFile(File file, BiConsumer<Long, Long> progressCallback, TransferProgressDialog dialog) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");

        Socket dataSocket = openDataConnection();
        writer.println("STOR " + file.getName());

        String response = reader.readLine();
        if (response == null || !response.startsWith("150")) {
            dataSocket.close();
            return false;
        }

        try (FileInputStream fileIn = new FileInputStream(file);
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[65536];
            int bytesRead;
            long uploadedBytes = 0;

            while ((bytesRead = fileIn.read(buffer)) > 0) {
                // Tích hợp logic Pause/Resume/Cancel
                if (dialog != null) {
                    while (dialog.isPaused() && !dialog.isCancelled()) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    if (dialog.isCancelled()) throw new IOException("Đã hủy.");
                }
                dataOut.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;
                if (progressCallback != null) progressCallback.accept(uploadedBytes, file.length());
            }
            dataOut.flush();
        }

        response = reader.readLine();
        return response != null && response.startsWith("226");
    }

    // CẬP NHẬT: Thêm biến fileSize để thanh tiến trình TransferProgressDialog chạy chuẩn xác
    public void downloadFile(String fileName, File destinationFolder, BiConsumer<Long, Long> progressCallback, TransferProgressDialog dialog, long fileSize) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");

        Socket dataSocket = openDataConnection();
        writer.println("RETR " + fileName);

        String response = reader.readLine();
        if (response == null || !response.startsWith("150")) {
            dataSocket.close();
            throw new FileNotFoundException("Lỗi tải tệp: " + response);
        }

        File saveFile = new File(destinationFolder, fileName);
        try (InputStream dataIn = dataSocket.getInputStream();
             FileOutputStream fileOut = new FileOutputStream(saveFile)) {

            byte[] buffer = new byte[65536];
            int bytesRead;
            long downloadedBytes = 0;
            // Nếu biết trước size thì gán, không thì lấy mặc định 100MB để tránh chia cho 0
            long estimatedSize = fileSize > 0 ? fileSize : 100 * 1024 * 1024;

            while ((bytesRead = dataIn.read(buffer)) != -1) {
                // Tích hợp logic Pause/Resume/Cancel
                if (dialog != null) {
                    while (dialog.isPaused() && !dialog.isCancelled()) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    if (dialog.isCancelled()) throw new IOException("Đã hủy.");
                }
                fileOut.write(buffer, 0, bytesRead);
                downloadedBytes += bytesRead;
                if (progressCallback != null) progressCallback.accept(downloadedBytes, estimatedSize);
            }
        }
        reader.readLine();
    }

    public void deleteFile(String fileName) {
        if (isAuthenticated()) {
            writer.println("DELE " + fileName);
            try { reader.readLine(); } catch (IOException ignored) {}
        }
    }

    public boolean createDirectory(String folderName) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");
        writer.println("MKD " + folderName);
        String response = reader.readLine();
        return response != null && response.startsWith("200");
    }

    public boolean shareFile(String fileName, String targetUsername, String permissionType) throws IOException {
        writer.println("SHARE " + fileName + "|" + targetUsername + "|" + permissionType);
        String response = reader.readLine();
        return response != null && response.contains("200");
    }

    public boolean unshareFile(String fileName) {
        if (!isAuthenticated()) return false;
        writer.println("UNSHARE " + fileName);
        try {
            String response = reader.readLine();
            return response != null && response.startsWith("200");
        } catch (IOException e) {
            return false;
        }
    }

    public void disconnect() {
        authenticated = false;
        try {
            if (writer != null) {
                writer.println("QUIT");
                writer.flush();
            }
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}

        // Reset các luồng
        controlSocket = null;
        writer = null;
        reader = null;
    }

    public boolean renameOrMoveFile(String oldPath, String newPath) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");

        writer.println("RNFR " + oldPath);
        String response = reader.readLine();
        if (response == null || !response.startsWith("350")) {
            return false;
        }

        writer.println("RNTO " + newPath);
        response = reader.readLine();
        return response != null && response.startsWith("250");
    }

    public List<String> fetchNotifications() {
        if (!isAuthenticated()) return Collections.emptyList();
        List<String> notifs = new ArrayList<>();
        try {
            writer.println("GET_NOTIFS");
            String response = reader.readLine();
            if (response != null && response.startsWith("213-")) {
                String line;
                while ((line = reader.readLine()) != null && !line.startsWith("213 ")) {
                    notifs.add(line);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return notifs;
    }

    public long[] getQuota() {
        if (!isAuthenticated()) return new long[]{0, 0};
        try {
            writer.println("QUOTA");
            String response = reader.readLine();
            if (response != null && response.startsWith("213 QUOTA")) {
                String[] parts = response.split(" ");
                if (parts.length >= 4) {
                    return new long[]{Long.parseLong(parts[2]), Long.parseLong(parts[3])};
                }
            }
        } catch (Exception ignored) {}
        return new long[]{0, 0};
    }

    public boolean setFilePermissions(String fileName, String permType) throws IOException {
        if (!isAuthenticated()) throw new IOException("Chưa xác thực.");
        writer.println("SET_PERM " + fileName + "|" + permType);
        String response = reader.readLine();
        return response != null && response.startsWith("200");
    }

}