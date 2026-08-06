package client.controller;

import client.model.FTPClientModel;
import client.view.MainClientView;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainClientController {
    private final MainClientView view;
    private final FTPClientModel model;
    private final FileSystemView fsv = FileSystemView.getFileSystemView();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public MainClientController(MainClientView view, FTPClientModel model) {
        this.view = view;
        this.model = model;

        initLocalEvents();
        initRemoteEvents();
        initTopBarEvents();
    }

    private void initTopBarEvents() {
        view.getBtnConnect().addActionListener(e -> handleQuickConnect());

        // Bắt sự kiện nút Đăng nhập Ẩn danh
        view.getBtnAnonymous().addActionListener(e -> {
            view.getTxtUsername().setText("anonymous");
            view.getTxtPassword().setText("");
            handleQuickConnect();
        });

        view.getBtnRegister().addActionListener(e -> {
            JOptionPane.showMessageDialog(view, "Chức năng đăng ký OTP sẽ được tích hợp sau.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void handleQuickConnect() {
        String host = view.getTxtHost().getText().trim();
        String portStr = view.getTxtPort().getText().trim();
        String username = view.getTxtUsername().getText().trim();
        String password = new String(view.getTxtPassword().getPassword());

        if (host.isEmpty() || portStr.isEmpty() || username.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Vui lòng nhập đầy đủ Host, Port và Username!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view, "Port phải là số nguyên!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        view.appendLog("Status: Đang thử kết nối tới " + host + ":" + port + "...");

        new Thread(() -> {
            try {
                boolean success = model.connectAndLogin(host, port, username, password);
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        view.appendLog("Response: Kết nối và đăng nhập thành công với tài khoản '" + username + "'!");
                        view.setRemotePaneEnabled(true);
                        // Bắt đầu tải gốc của Server
                        initRemoteRootNode();
                    } else {
                        view.appendLog("Error: Xác thực thất bại hoặc tài khoản không tồn tại.");
                        JOptionPane.showMessageDialog(view, "Xác thực thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    view.appendLog("Error: Lỗi mạng - " + ex.getMessage());
                    JOptionPane.showMessageDialog(view, "Không thể kết nối máy chủ!", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // ==========================================
    // CÁC SỰ KIỆN XỬ LÝ NHÁNH LOCAL (BÊN TRÁI)
    // ==========================================
    private void initLocalEvents() {
        view.getLocalTree().addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (!(node.getUserObject() instanceof MainClientView.LocalFileNodeInfo)) return;

                MainClientView.LocalFileNodeInfo info = (MainClientView.LocalFileNodeInfo) node.getUserObject();
                if (!info.isLoaded) {
                    node.removeAllChildren();
                    File[] files = info.file.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isDirectory() && !file.isHidden()) {
                                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new MainClientView.LocalFileNodeInfo(file));
                                childNode.add(new DefaultMutableTreeNode("Đang tải..."));
                                node.add(childNode);
                            }
                        }
                    }
                    info.isLoaded = true;
                    ((DefaultTreeModel) view.getLocalTree().getModel()).reload(node);
                }
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        view.getLocalTree().addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) view.getLocalTree().getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof MainClientView.LocalFileNodeInfo)) return;

            MainClientView.LocalFileNodeInfo info = (MainClientView.LocalFileNodeInfo) node.getUserObject();
            view.getTxtLocalPath().setText(info.file.getAbsolutePath());
            loadLocalFilesToTable(info.file);
        });
    }

    private void loadLocalFilesToTable(File dir) {
        DefaultTableModel tableModel = (DefaultTableModel) view.getLocalTable().getModel();
        tableModel.setRowCount(0);

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isHidden()) {
                String name = fsv.getSystemDisplayName(file);
                if (name.isEmpty()) name = file.getName();

                String size = file.isDirectory() ? "" : formatFileSize(file.length());
                String type = fsv.getSystemTypeDescription(file);
                String lastModified = sdf.format(new Date(file.lastModified()));

                tableModel.addRow(new Object[]{name, size, type, lastModified});
            }
        }
    }

    // ==========================================
    // CÁC SỰ KIỆN XỬ LÝ NHÁNH REMOTE (BÊN PHẢI)
    // ==========================================
    private void initRemoteEvents() {
        // Sự kiện mở rộng thư mục Remote (Lazy Loading)
        view.getRemoteTree().addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (!(node.getUserObject() instanceof RemoteNodeInfo)) return;

                RemoteNodeInfo info = (RemoteNodeInfo) node.getUserObject();
                if (!info.isLoaded) {
                    loadRemoteSubDirectories(node, info);
                }
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        // Sự kiện click chọn thư mục Remote
        view.getRemoteTree().addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) view.getRemoteTree().getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof RemoteNodeInfo)) return;

            RemoteNodeInfo info = (RemoteNodeInfo) node.getUserObject();
            view.getTxtRemotePath().setText(info.ftpPath);
            loadRemoteFilesToTable(info.ftpPath);
        });
    }

    private void initRemoteRootNode() {
        RemoteNodeInfo rootInfo = new RemoteNodeInfo("/", "/");
        DefaultMutableTreeNode remoteRoot = new DefaultMutableTreeNode(rootInfo);
        remoteRoot.add(new DefaultMutableTreeNode("Đang tải..."));

        DefaultTreeModel treeModel = new DefaultTreeModel(remoteRoot);
        view.getRemoteTree().setModel(treeModel);
        view.getTxtRemotePath().setText("/");

        // Tự động mở rộng root để tải dữ liệu ban đầu
        view.getRemoteTree().expandPath(new TreePath(remoteRoot.getPath()));
    }

    private void loadRemoteSubDirectories(DefaultMutableTreeNode parentNode, RemoteNodeInfo parentInfo) {
        new Thread(() -> {
            try {
                // Di chuyển Server tới đúng thư mục
                boolean success = model.changeWorkingDirectory(parentInfo.ftpPath);

                if (!success) {
                    SwingUtilities.invokeLater(() -> {
                        parentNode.removeAllChildren();
                        ((DefaultTreeModel) view.getRemoteTree().getModel()).reload(parentNode);
                        view.appendLog("Warning: Server từ chối chuyển tới " + parentInfo.ftpPath);
                    });
                    // Nếu là thư mục gốc "/" thì vẫn thử tải list file, nếu không thì dừng
                    if (!parentInfo.ftpPath.equals("/")) return;
                }

                List<String> files = model.fetchFileList();

                SwingUtilities.invokeLater(() -> {
                    parentNode.removeAllChildren();
                    parentInfo.isLoaded = true;

                    if (files != null) {
                        for (String raw : files) {
                            if (raw.trim().startsWith("d")) { // Dấu hiệu thư mục
                                String folderName = parseFileName(raw);
                                if (folderName.isEmpty() || folderName.equals(".") || folderName.equals("..")) continue;

                                String subPath = parentInfo.ftpPath.equals("/")
                                        ? "/" + folderName
                                        : parentInfo.ftpPath + "/" + folderName;

                                RemoteNodeInfo subDirInfo = new RemoteNodeInfo(folderName, subPath);
                                DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(subDirInfo);
                                subNode.add(new DefaultMutableTreeNode("Đang tải..."));

                                parentNode.add(subNode);
                            }
                        }
                    }
                    ((DefaultTreeModel) view.getRemoteTree().getModel()).reload(parentNode);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    parentNode.removeAllChildren();
                    ((DefaultTreeModel) view.getRemoteTree().getModel()).reload(parentNode);
                    view.appendLog("Error: Lỗi khi tải thư mục Remote - " + e.getMessage());
                });
            }
        }).start();
    }

    private void loadRemoteFilesToTable(String ftpPath) {
        new Thread(() -> {
            try {
                boolean success = model.changeWorkingDirectory(ftpPath);
                // Dù CWD thành công hay thất bại (như trường hợp ở root "/"), vẫn thử lấy file
                if (success || ftpPath.equals("/")) {
                    List<String> files = model.fetchFileList();

                    SwingUtilities.invokeLater(() -> {
                        DefaultTableModel tableModel = (DefaultTableModel) view.getRemoteTable().getModel();
                        tableModel.setRowCount(0); // Xóa data cũ

                        if (files != null) {
                            for (String raw : files) {
                                String name = parseFileName(raw);
                                if (name.equals(".") || name.equals("..")) continue;

                                boolean isDir = raw.trim().startsWith("d");
                                String type = isDir ? "File folder" : "File";
                                String size = isDir ? "" : parseFileSize(raw);
                                String date = parseFileDate(raw);

                                tableModel.addRow(new Object[]{name, size, type, date});
                            }
                        }
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> view.appendLog("Error: Lỗi khi tải file vào bảng - " + e.getMessage()));
            }
        }).start();
    }

    // ---------------------------------------------------------
    // CÁC HÀM PARSE DỮ LIỆU FTP (HỖ TRỢ CẢ UNIX VÀ WINDOWS DOS)
    // ---------------------------------------------------------
    private String[] parseParts(String rawStr) {
        return rawStr.split("\\|", 4);
    }

    private boolean isDirectory(String rawStr) {
        String[] p = parseParts(rawStr);
        return p.length == 4 && p[0].trim().equals("d");
    }

    private String parseFileName(String rawStr) {
        String[] p = parseParts(rawStr);
        return p.length == 4 ? p[3].trim() : rawStr;
    }

    private String parseFileSize(String rawStr) {
        String[] p = parseParts(rawStr);
        if (p.length == 4 && p[0].trim().equals("-")) {
            try { return formatFileSize(Long.parseLong(p[1].trim())); } catch (Exception ignored) {}
        }
        return "";
    }

    private String parseFileDate(String rawStr) {
        String[] p = parseParts(rawStr);
        return p.length == 4 ? p[2].trim() : "";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }


    // --- LỚP HỖ TRỢ LƯU TRỮ THÔNG TIN NODE REMOTE ---
    public static class RemoteNodeInfo {
        public String name;
        public String ftpPath;
        public boolean isLoaded = false;

        public RemoteNodeInfo(String name, String ftpPath) {
            this.name = name;
            this.ftpPath = ftpPath;
        }

        @Override
        public String toString() {
            return name.equals("/") ? "/" : name;
        }
    }

    // Hàm chạy chính
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            FTPClientModel model = new FTPClientModel();
            MainClientView view = new MainClientView();
            new MainClientController(view, model);
            view.setVisible(true);
        });
    }
}