package client.controller;

import client.model.FTPClientModel;
import client.view.AppLoginView;
import client.view.FTPClientView;
import client.view.TransferProgressDialog;
import server.model.AuthModel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public class FTPClientController {
    private static final String CONFIG_FILE = "server_config.properties";
    private static final String DEFAULT_PORT = "5000";

    private String serverIp;
    private int serverPort;

    private final FTPClientModel model;
    private final FTPClientView view;
    private final String username;
    private final String password;

    private boolean isNotificationRead = false;

    public FTPClientController(FTPClientModel model, FTPClientView view, String username, String password) {
        this.model = model;
        this.view = view;
        this.username = username;
        this.password = password;

        initControllerEvents();
        connectToServer(false);
    }

    private void initControllerEvents() {
        // Nút bấm chính
        this.view.getBtnRefresh().addActionListener(e -> {
            if (!model.isAuthenticated()) {
                connectToServer(true);
            } else {
                refreshFileList();
            }
        });

        this.view.getBtnNotification().addActionListener(e -> handleShowNotificationDialog());
        this.view.getBtnUpload().addActionListener(e -> uploadFiles());
        this.view.getBtnDownload().addActionListener(e -> downloadFileWithFolderChooser());
        this.view.getBtnMkdir().addActionListener(e -> handleCreateDirectory());
        this.view.getBtnShare().addActionListener(e -> handleShareFile());
        this.view.getBtnDelete().addActionListener(e -> deleteFile());
        this.view.getBtnExit().addActionListener(e -> handleLogout());

        // Sự kiện chuyển Tab trên JTabbedPane
        this.view.getTabbedPane().addChangeListener(e -> {
            int selectedIndex = this.view.getTabbedPane().getSelectedIndex();
            this.view.attachTableToTab(selectedIndex);
            refreshFileList();
        });

        // Context Menu (Chuột phải)
        this.view.getMenuShare().addActionListener(e -> handleShareFile());
        this.view.getMenuMkdir().addActionListener(e -> handleCreateDirectory());
        this.view.getMenuDownload().addActionListener(e -> downloadFileWithFolderChooser());
        this.view.getMenuDelete().addActionListener(e -> deleteFile());
    }

    private void connectToServer(boolean forceReconfig) {
        if (!resolveServerAddress(forceReconfig)) {
            view.appendLog("Đã hủy nhập IP. Bấm 'Làm mới danh sách' để nhập lại IP Server.");
            return;
        }

        new Thread(() -> {
            try {
                view.appendLog("Đang thử kết nối tới Server " + serverIp + ":" + serverPort + "...");
                boolean success = model.connectAndLogin(serverIp, serverPort, username, password);
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        view.appendLog("Kết nối thành công tới FTP Server (" + serverIp + ":" + serverPort + ")!");
                        refreshFileList();
                    } else {
                        view.showError("Xác thực FTP thất bại!\nTài khoản '" + username + "' chưa được cấp quyền trên Server.");
                        view.appendLog("Lỗi: Xác thực tài khoản FTP thất bại.");
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    view.showError("Không kết nối được FTP Server tại " + serverIp + ":" + serverPort + "!");
                    view.appendLog("Lỗi kết nối Socket tới " + serverIp + ":" + serverPort + " - " + ex.getMessage());
                });
            }
        }).start();
    }

    private boolean resolveServerAddress(boolean forcePrompt) {
        Properties props = new Properties();
        String savedIp = "";
        String savedPort = DEFAULT_PORT;

        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
                savedIp = props.getProperty("server_ip", "");
                savedPort = props.getProperty("server_port", DEFAULT_PORT);
            } catch (IOException ignored) {}
        }

        if (!forcePrompt && !savedIp.isEmpty()) {
            serverIp = savedIp;
            try {
                serverPort = Integer.parseInt(savedPort.trim());
            } catch (NumberFormatException e) {
                serverPort = Integer.parseInt(DEFAULT_PORT);
            }
            return true;
        }

        String defaultInput = savedIp.isEmpty() ? "127.0.0.1:5000" : savedIp + ":" + savedPort;
        String input = JOptionPane.showInputDialog(view,
                "Nhập địa chỉ IP và Cổng của FTP Server (Định dạng IP:Port):",
                defaultInput);

        if (input == null || input.trim().isEmpty()) return false;

        input = input.trim();
        if (input.contains(":")) {
            String[] parts = input.split(":", 2);
            serverIp = parts[0].trim();
            try { serverPort = Integer.parseInt(parts[1].trim()); } catch (Exception e) { serverPort = 5000; }
        } else {
            serverIp = input;
            serverPort = 5000;
        }

        props.setProperty("server_ip", serverIp);
        props.setProperty("server_port", String.valueOf(serverPort));
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "Cau hinh dia chi FTP Server");
        } catch (IOException ignored) {}

        return true;
    }

    private void refreshFileList() {
        if (!model.isAuthenticated()) return;

        int selectedTab = view.getTabbedPane().getSelectedIndex();
        boolean isSharedTab = (selectedTab == 1);

        new Thread(() -> {
            try {
                List<String> files = isSharedTab ? model.fetchSharedFileList() : model.fetchFileList();

                SwingUtilities.invokeLater(() -> {
                    // Nếu đã đọc rồi thì giữ nguyên (0), chưa đọc mới cập nhật số lượng
                    if (isNotificationRead) {
                        view.setNotificationCount(0);
                    } else {
                        try {
                            List<String> sharedFiles = model.fetchSharedFileList();
                            view.setNotificationCount(sharedFiles.size());
                        } catch (Exception ignored) {}
                    }

                    view.updateFileList(files, isSharedTab);
                    view.appendLog("Đã cập nhật danh sách tệp từ Server.");
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> view.showError("Lỗi khi lấy danh sách tệp từ Server."));
            }
        }).start();
    }

    private void handleShowNotificationDialog() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối tới Server!");
            return;
        }

        // Đánh dấu đã đọc & cập nhật ngay lập tức về (0)
        isNotificationRead = true;
        view.resetNotificationCount();

        new Thread(() -> {
            try {
                List<String> sharedFiles = model.fetchSharedFileList();
                SwingUtilities.invokeLater(() -> {
                    if (sharedFiles.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                view,
                                "Hiện tại bạn chưa nhận được tệp hoặc thư mục chia sẻ nào.",
                                "Thông báo chia sẻ",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        FTPClientView.SharedNotificationDialog dialog =
                                new FTPClientView.SharedNotificationDialog(view, sharedFiles);
                        dialog.setVisible(true);
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> view.showError("Lỗi khi tải thông báo từ Server."));
            }
        }).start();
    }

    private void uploadFiles() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối hoặc chưa được xác thực với FTP Server!");
            return;
        }

        FileDialog fileDialog = new FileDialog(view, "Chọn tệp tải lên Server", FileDialog.LOAD);
        fileDialog.setMultipleMode(true);
        fileDialog.setVisible(true);

        File[] selectedFiles = fileDialog.getFiles();
        if (selectedFiles != null && selectedFiles.length > 0) {
            new Thread(() -> {
                for (File file : selectedFiles) {
                    TransferProgressDialog progressDialog = new TransferProgressDialog(view, "Đang tải lên Server", file.getName());
                    SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

                    try {
                        SwingUtilities.invokeLater(() -> view.appendLog("Đang tải lên: " + file.getName()));
                        boolean ok = model.uploadFile(file, progressDialog::updateProgress, progressDialog);

                        SwingUtilities.invokeLater(progressDialog::dispose);
                        if (ok) {
                            SwingUtilities.invokeLater(() -> view.appendLog("Tải lên thành công: " + file.getName()));
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(progressDialog::dispose);
                        SwingUtilities.invokeLater(() -> view.appendLog("Lỗi/Hủy khi tải lên: " + ex.getMessage()));
                    }
                }
                refreshFileList();
            }).start();
        }
    }

    private void downloadFileWithFolderChooser() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối hoặc chưa được xác thực với FTP Server!");
            return;
        }

        String fileName = view.getSelectedFile();
        if (fileName == null) {
            view.showError("Vui lòng chọn một tệp trong bảng để tải xuống!");
            return;
        }

        FileDialog fileDialog = new FileDialog(view, "Chọn vị trí lưu tệp: " + fileName, FileDialog.SAVE);
        fileDialog.setFile(fileName);
        fileDialog.setVisible(true);

        String directory = fileDialog.getDirectory();
        String selectedFileName = fileDialog.getFile();

        if (directory != null && selectedFileName != null) {
            File destinationFolder = new File(directory);

            new Thread(() -> {
                TransferProgressDialog progressDialog = new TransferProgressDialog(view, "Đang tải xuống Máy tính", fileName);
                SwingUtilities.invokeLater(() -> progressDialog.setVisible(true));

                try {
                    SwingUtilities.invokeLater(() -> view.appendLog("Đang tải tệp về: " + directory + selectedFileName));
                    model.downloadFile(fileName, destinationFolder, progressDialog::updateProgress, progressDialog);

                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        view.appendLog("Tải về thành công! Đã lưu tại: " + directory + selectedFileName);
                        JOptionPane.showMessageDialog(view, "Tải tệp thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.dispose();
                        view.showError("Tải tệp thất bại/đã hủy: " + ex.getMessage());
                    });
                }
            }).start();
        }
    }

    private void deleteFile() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối hoặc chưa được xác thực với FTP Server!");
            return;
        }

        String selected = view.getSelectedFile();
        if (selected == null) {
            view.showError("Vui lòng chọn tệp cần xóa.");
            return;
        }

        new Thread(() -> {
            model.deleteFile(selected);
            SwingUtilities.invokeLater(() -> view.appendLog("Đã xóa tệp: " + selected));
            refreshFileList();
        }).start();
    }

    private void handleLogout() {
        new Thread(() -> {
            this.model.disconnect();
            SwingUtilities.invokeLater(() -> {
                this.view.dispose();
                AuthModel authModel = new AuthModel();
                AppLoginView loginView = new AppLoginView();
                new AppLoginController(authModel, loginView);
                loginView.setVisible(true);
            });
        }).start();
    }

    private void handleCreateDirectory() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối hoặc chưa được xác thực!");
            return;
        }

        String folderName = JOptionPane.showInputDialog(
                view,
                "Nhập tên thư mục mới cần tạo:",
                "Tạo Thư Mục Mới",
                JOptionPane.QUESTION_MESSAGE
        );

        if (folderName != null && !folderName.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    boolean ok = model.createDirectory(folderName.trim());
                    SwingUtilities.invokeLater(() -> {
                        if (ok) {
                            view.appendLog("Đã tạo thư mục thành công: " + folderName);
                            refreshFileList();
                        } else {
                            view.showError("Tạo thư mục thất bại! Thư mục đã tồn tại hoặc tên không hợp lệ.");
                        }
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> view.showError("Lỗi khi kết nối Server tạo thư mục."));
                }
            }).start();
        }
    }

    private void handleShareFile() {
        if (!model.isAuthenticated()) {
            view.showError("Bạn chưa kết nối hoặc chưa được xác thực!");
            return;
        }

        String selectedFile = view.getSelectedFile();
        if (selectedFile == null) {
            view.showError("Vui lòng chọn một tệp hoặc thư mục trong bảng để chia sẻ!");
            return;
        }

        String targetUser = JOptionPane.showInputDialog(
                view,
                "Nhập tên tài khoản (Username) người nhận tệp [" + selectedFile + "]:",
                "Chia sẻ Tệp / Thư mục",
                JOptionPane.QUESTION_MESSAGE
        );

        if (targetUser != null && !targetUser.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    boolean ok = model.shareFile(selectedFile, targetUser.trim());
                    SwingUtilities.invokeLater(() -> {
                        if (ok) {
                            view.appendLog("Đã chia sẻ thành công [" + selectedFile + "] tới tài khoản: " + targetUser);
                            JOptionPane.showMessageDialog(view, "Đã gửi tệp thành công tới " + targetUser + "!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            view.showError("Chia sẻ thất bại! Không tìm thấy người dùng '" + targetUser + "'.");
                        }
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> view.showError("Lỗi kết nối khi thực hiện chia sẻ tệp."));
                }
            }).start();
        }
    }
}