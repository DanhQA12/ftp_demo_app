package client.controller;

import client.model.FTPClientModel;
import client.view.MainClientView;
import client.view.RegisterView;
import client.view.TransferProgressDialog;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainClientController {
    private final MainClientView view;
    private final FTPClientModel model;
    private final FileSystemView fsv = FileSystemView.getFileSystemView();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private String clipboardSourcePath = null;
    private String clipboardFileName = null;
    private boolean isCutAction = false;

    public MainClientController(MainClientView view, FTPClientModel model) {
        this.view = view;
        this.model = model;

        initLocalEvents();
        initRemoteEvents();
        initTopBarEvents();
        initContextMenuEvents();
        initDragAndDrop();
    }

    private void initTopBarEvents() {
        view.getBtnConnect().addActionListener(e -> handleQuickConnect());
        view.getBtnAnonymous().addActionListener(e -> {
            view.getTxtUsername().setText("anonymous");
            view.getTxtPassword().setText("");
            handleQuickConnect();
        });
        view.getBtnRegister().addActionListener(e -> new RegisterView(view).setVisible(true));
        view.getBtnQuit().addActionListener(e -> {
            if (model.isAuthenticated()) {
                new Thread(() -> {
                    model.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        view.appendLog("Status: Đã ngắt kết nối khỏi máy chủ.");
                        view.setRemotePaneEnabled(false); // Khóa lại khung bên phải giống như lúc chưa kết nối
                        updateQuotaUI();
                    });
                }).start();
            } else {
                JOptionPane.showMessageDialog(view, "Bạn chưa kết nối tới máy chủ nào!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
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
        try { port = Integer.parseInt(portStr); }
        catch (NumberFormatException ex) {
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
                        initRemoteRootNode();
                        updateQuotaUI();

                        // --- KIỂM TRA THÔNG BÁO ---
                        new Thread(() -> {
                            List<String> notifs;

                            // THÊM KHÓA ĐỒNG BỘ Ở ĐÂY để nó phải "xếp hàng" chờ
                            // luồng QUOTA và LIST chạy xong mới được gửi lệnh lấy thông báo
                            synchronized (model) {
                                notifs = model.fetchNotifications();
                            }

                            if (notifs != null && !notifs.isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    StringBuilder sb = new StringBuilder();
                                    for (String n : notifs) sb.append("- ").append(n).append("\n");
                                    JOptionPane.showMessageDialog(view, "Bạn có thông báo mới:\n" + sb.toString(),
                                            "Thông báo Hệ thống", JOptionPane.INFORMATION_MESSAGE);
                                });
                            }
                        }).start();
                    }
                    else {
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
    // TÍNH NĂNG DRAG & DROP (KÉO THẢ GIỮA 2 BẢNG)
    // ==========================================
    private void initDragAndDrop() {
        view.getRemoteTable().setDragEnabled(true);
        view.getRemoteTable().setDropMode(DropMode.ON_OR_INSERT_ROWS);
        view.getRemoteTable().setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) { return COPY_OR_MOVE; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<String> selectedFiles = getSelectedRemoteFiles();
                if (selectedFiles.isEmpty()) return null;
                return new StringSelection("REMOTE_MOVE::" + String.join("::", selectedFiles));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!model.isAuthenticated()) return false;

                // Trường hợp 1: Nhận file kéo từ máy tính (Upload)
                if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true;

                // Trường hợp 2: Kéo thả di chuyển ngay trong Remote Table
                if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                    int row = dl.getRow();

                    // CHẶN LỖI: Tránh click vào khoảng trắng (nơi không có dòng) gây Exception tạo xẹt chéo
                    if (row < 0 || row >= view.getRemoteTable().getRowCount()) return false;

                    String type = (String) view.getRemoteTable().getValueAt(row, 2);
                    return "File folder".equalsIgnoreCase(type) || type.toLowerCase().contains("thư mục") || "d".equalsIgnoreCase(type);
                }
                return false;
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    // Xử lý kéo thả Upload từ Local
                    if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        handleUploadFilesList(files);
                        return true;
                    }

                    // Xử lý kéo thả Di chuyển (Move) trong Remote
                    if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                        int row = dl.getRow();
                        String targetFolderName = (String) view.getRemoteTable().getValueAt(row, 0);
                        if ("..".equals(targetFolderName)) return false;

                        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if (!data.startsWith("REMOTE_MOVE::")) return false;

                        String[] fileNames = data.replace("REMOTE_MOVE::", "").split("::");
                        String currentDir = view.getTxtRemotePath().getText();
                        String targetDirPath = currentDir.equals("/") ? "/" + targetFolderName : currentDir + "/" + targetFolderName;

                        new Thread(() -> {
                            try {
                                synchronized (model) {
                                    for (String fn : fileNames) {
                                        if (fn.trim().isEmpty()) continue;
                                        String oldFullPath = currentDir.equals("/") ? "/" + fn : currentDir + "/" + fn;
                                        String newFullPath = targetDirPath + "/" + fn;
                                        boolean ok = model.renameOrMoveFile(oldFullPath, newFullPath);
                                        if (ok) view.appendLog("Status: Đã di chuyển '" + fn + "' vào '" + targetFolderName + "'");
                                        else view.appendLog("Error: Server từ chối lệnh di chuyển đối với '" + fn + "'");
                                    }
                                }
                                SwingUtilities.invokeLater(() -> loadRemoteFilesToTable(currentDir));
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> view.appendLog("Lỗi di chuyển: " + ex.getMessage()));
                            }
                        }).start();
                        return true;
                    }
                    return false;
                } catch (Exception e) { return false; }
            }
        });
        // localTable: chỉ cần làm ĐÍCH (nhận file kéo từ Remote để Download)
        view.getLocalTable().setTransferHandler(new TransferHandler() {
            // --- Làm NGUỒN: kéo file Local ra để thả vào Remote (Upload) ---
            @Override
            public int getSourceActions(JComponent c) { return COPY; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<File> selected = getSelectedLocalFiles();
                if (selected.isEmpty()) return null;
                return new Transferable() {
                    @Override
                    public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
                    }
                    @Override
                    public boolean isDataFlavorSupported(DataFlavor flavor) {
                        return DataFlavor.javaFileListFlavor.equals(flavor);
                    }
                    @Override
                    public Object getTransferData(DataFlavor flavor) {
                        return selected;
                    }
                };
            }

            // --- Làm ĐÍCH: nhận file kéo từ Remote thả vào (Download) ---
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor) && model.isAuthenticated();
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    String[] fileNames = data.split("::");
                    List<String> listToDownload = new ArrayList<>();
                    for (String f : fileNames) {
                        if (!f.trim().isEmpty() && !"..".equals(f.trim())) listToDownload.add(f.trim());
                    }
                    if (!listToDownload.isEmpty()) handleDownloadFilesList(listToDownload, false);
                    return true;
                } catch (Exception e) { return false; }
            }
        });
    }

    // ==========================================
    // SỰ KIỆN MENU CHUỘT PHẢI (CONTEXT MENU)
    // ==========================================
    private void initContextMenuEvents() {
        // --- LOCAL SITE ---
        view.getMnuLocUpload().addActionListener(e -> handleUpload());
        view.getMnuLocRefresh().addActionListener(e -> loadLocalFilesToTable(new File(view.getTxtLocalPath().getText())));

        view.getMnuLocOpen().addActionListener(e -> {
            List<File> files = getSelectedLocalFiles();
            for(File f : files) {
                try { Desktop.getDesktop().open(f); }
                catch (IOException ex) { JOptionPane.showMessageDialog(view, "Không thể mở file.", "Lỗi", JOptionPane.ERROR_MESSAGE); }
            }
        });

        view.getMnuLocMkdir().addActionListener(e -> {
            String dirName = JOptionPane.showInputDialog(view, "Nhập tên thư mục mới:", "Tạo Folder", JOptionPane.QUESTION_MESSAGE);
            if (dirName != null && !dirName.trim().isEmpty()) {
                File newDir = new File(view.getTxtLocalPath().getText(), dirName.trim());
                if (newDir.mkdir()) loadLocalFilesToTable(new File(view.getTxtLocalPath().getText()));
                else JOptionPane.showMessageDialog(view, "Tạo thư mục thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        view.getMnuLocMkdirEnter().addActionListener(e -> {
            String dirName = JOptionPane.showInputDialog(view, "Nhập tên thư mục mới:", "Tạo Folder và vào trong", JOptionPane.QUESTION_MESSAGE);
            if (dirName != null && !dirName.trim().isEmpty()) {
                File newDir = new File(view.getTxtLocalPath().getText(), dirName.trim());
                if (newDir.mkdir()) {
                    view.getTxtLocalPath().setText(newDir.getAbsolutePath());
                    loadLocalFilesToTable(newDir);
                }
            }
        });

        view.getMnuLocRename().addActionListener(e -> {
            List<File> files = getSelectedLocalFiles();
            if (files.isEmpty()) return;
            String oldName = files.get(0).getName();
            String newName = JOptionPane.showInputDialog(view, "Đổi tên file:", oldName);
            if (newName != null && !newName.trim().isEmpty()) {
                File oldFile = new File(view.getTxtLocalPath().getText(), oldName);
                File newFile = new File(view.getTxtLocalPath().getText(), newName.trim());
                if (oldFile.renameTo(newFile)) loadLocalFilesToTable(new File(view.getTxtLocalPath().getText()));
                else JOptionPane.showMessageDialog(view, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        view.getMnuLocDelete().addActionListener(e -> {
            List<File> files = getSelectedLocalFiles();
            if (files.isEmpty()) return;
            int ans = JOptionPane.showConfirmDialog(view, "Bạn có chắc chắn muốn xóa vĩnh viễn " + files.size() + " mục đã chọn?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) {
                for(File f : files) {
                    if (f.delete()) view.appendLog("Status: Đã xóa Local file - " + f.getName());
                }
                loadLocalFilesToTable(new File(view.getTxtLocalPath().getText()));
            }
        });

        view.getMnuLocEdit().addActionListener(e -> showDevMsg());

        // --- REMOTE SITE ---
        view.getMnuRemDownload().addActionListener(e -> handleDownload(false));
        view.getMnuRemRefresh().addActionListener(e -> loadRemoteFilesToTable(view.getTxtRemotePath().getText()));

        view.getMnuRemDelete().addActionListener(e -> {
            List<String> files = getSelectedRemoteFiles();
            if (files.isEmpty()) return;

            String currentRemoteDir = view.getTxtRemotePath().getText();

            if ("SHARED".equals(currentRemoteDir)) {
                int ans = JOptionPane.showConfirmDialog(view,
                        "Bạn có muốn gỡ bỏ " + files.size() + " file khỏi danh sách chia sẻ không?",
                        "Xác nhận gỡ chia sẻ", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (ans == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        for(String fn : files) {
                            if(model.unshareFile(fn)) view.appendLog("Status: Đã gỡ file chia sẻ - " + fn);
                        }
                        SwingUtilities.invokeLater(() -> loadRemoteFilesToTable(currentRemoteDir));
                    }).start();
                }
                return;
            }

            int ans = JOptionPane.showConfirmDialog(view, "Bạn có chắc chắn muốn xóa " + files.size() + " mục khỏi FTP Server không?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) {
                new Thread(() -> {
                    for(String fn : files) {
                        model.deleteFile(fn);
                        SwingUtilities.invokeLater(() -> view.appendLog("Status: Lệnh xóa file đã được gửi - " + fn));
                    }
                    SwingUtilities.invokeLater(() -> loadRemoteFilesToTable(currentRemoteDir));
                    updateQuotaUI();
                }).start();
            }
        });

        view.getMnuRemMkdir().addActionListener(e -> {
            String dirName = JOptionPane.showInputDialog(view, "Nhập tên Folder mới:", "Tạo Folder", JOptionPane.QUESTION_MESSAGE);
            if (dirName != null && !dirName.trim().isEmpty()) {
                new Thread(() -> {
                    try {
                        synchronized (model) {
                            model.changeWorkingDirectory(view.getTxtRemotePath().getText());
                            boolean ok = model.createDirectory(dirName.trim());
                            SwingUtilities.invokeLater(() -> {
                                if (ok) {
                                    view.appendLog("Thành công: Đã tạo Folder " + dirName);
                                    loadRemoteFilesToTable(view.getTxtRemotePath().getText());
                                } else JOptionPane.showMessageDialog(view, "Không thể tạo Folder!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> view.appendLog("Lỗi tạo Folder: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        // 1. MnuRemMkdirEnter: Tạo Folder và vào trong luôn
        view.getMnuRemMkdirEnter().addActionListener(e -> {
            String dirName = JOptionPane.showInputDialog(view, "Nhập tên Folder mới:", "Tạo Folder và vào trong", JOptionPane.QUESTION_MESSAGE);
            if (dirName != null && !dirName.trim().isEmpty()) {
                String currentDir = view.getTxtRemotePath().getText();
                new Thread(() -> {
                    try {
                        synchronized (model) {
                            model.changeWorkingDirectory(currentDir);
                            boolean ok = model.createDirectory(dirName.trim());
                            if (ok) {
                                String newPath = currentDir.equals("/") ? "/" + dirName.trim() : currentDir + "/" + dirName.trim();
                                SwingUtilities.invokeLater(() -> {
                                    view.appendLog("Thành công: Đã tạo và chuyển vào Folder " + dirName.trim());
                                    loadRemoteFilesToTable(newPath);
                                });
                            } else {
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(view, "Không thể tạo Folder!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                            }
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> view.appendLog("Lỗi tạo Folder: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        // 2. MnuRemCreateFile: Tạo file rỗng trực tiếp trên Server
        view.getMnuRemCreateFile().addActionListener(e -> {
            String fileName = JOptionPane.showInputDialog(view, "Nhập tên file mới (vd: note.txt):", "Tạo File", JOptionPane.QUESTION_MESSAGE);
            if (fileName != null && !fileName.trim().isEmpty()) {
                String currentDir = view.getTxtRemotePath().getText();
                new Thread(() -> {
                    try {
                        File tempFile = File.createTempFile("ftp_empty", ".tmp");
                        boolean serverAccepted = false;
                        synchronized (model) {
                            model.changeWorkingDirectory(currentDir);
                            boolean ok = model.uploadFile(tempFile, null, null);
                            if (ok) {
                                String tempName = tempFile.getName();
                                serverAccepted = model.renameOrMoveFile(
                                        currentDir.equals("/") ? "/" + tempName : currentDir + "/" + tempName,
                                        currentDir.equals("/") ? "/" + fileName.trim() : currentDir + "/" + fileName.trim()
                                );
                            }
                        }
                        tempFile.delete();

                        // CHỈ HIỂN THỊ LOG THÀNH CÔNG KHI SERVER THỰC SỰ CHẤP NHẬN
                        final boolean success = serverAccepted;
                        SwingUtilities.invokeLater(() -> {
                            if (success) {
                                view.appendLog("Thành công: Đã tạo file " + fileName.trim());
                                loadRemoteFilesToTable(currentDir);
                            } else {
                                view.appendLog("Error: Server từ chối tạo file do thư mục là Read-Only.");
                                JOptionPane.showMessageDialog(view, "Thất bại: Thư mục này đang ở trạng thái Read-Only!", "Từ chối", JOptionPane.ERROR_MESSAGE);
                                loadRemoteFilesToTable(currentDir);
                            }
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            view.appendLog("Error: Tạo file thất bại - Thư mục là Read-Only.");
                            JOptionPane.showMessageDialog(view, "Tạo file thất bại: Thư mục này đang ở trạng thái Read-Only!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                            loadRemoteFilesToTable(currentDir);
                        });
                    }
                }).start();
            }
        });

        // 3. MnuRemRename: Đổi tên file/thư mục
        view.getMnuRemRename().addActionListener(e -> {
            List<String> files = getSelectedRemoteFiles();
            if (files.isEmpty()) return;
            String oldName = files.get(0);
            String newName = JOptionPane.showInputDialog(view, "Nhập tên mới:", oldName);
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(oldName)) {
                String currentDir = view.getTxtRemotePath().getText();
                String oldPath = currentDir.equals("/") ? "/" + oldName : currentDir + "/" + oldName;
                String newPath = currentDir.equals("/") ? "/" + newName.trim() : currentDir + "/" + newName.trim();
                new Thread(() -> {
                    try {
                        synchronized (model) {
                            boolean ok = model.renameOrMoveFile(oldPath, newPath);
                            SwingUtilities.invokeLater(() -> {
                                if (ok) {
                                    view.appendLog("Đã đổi tên thành công: " + newName.trim());
                                    loadRemoteFilesToTable(currentDir);
                                } else {
                                    JOptionPane.showMessageDialog(view, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> view.appendLog("Lỗi đổi tên: " + ex.getMessage()));
                    }
                }).start();
            }
        });

        // 4. MnuRemCopyUrl: Copy đường dẫn tuyệt đối vào Clipboard
        view.getMnuRemCopyUrl().addActionListener(e -> {
            List<String> files = getSelectedRemoteFiles();
            if (files.isEmpty()) return;
            String fileName = files.get(0);
            String currentDir = view.getTxtRemotePath().getText();
            String fullPath = (currentDir.equals("/") ? "" : currentDir) + "/" + fileName;

            String ftpUrl = "ftp://" + view.getTxtHost().getText().trim() + ":" + view.getTxtPort().getText().trim() + fullPath;
            StringSelection selection = new StringSelection(ftpUrl);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            view.appendLog("Status: Đã copy URL vào clipboard: " + ftpUrl);
            JOptionPane.showMessageDialog(view, "Đã sao chép URL:\n" + ftpUrl, "Thành công", JOptionPane.INFORMATION_MESSAGE);
        });

        // 5. MnuRemViewEdit: Tải file về máy tạm thời rồi mở lên bằng Desktop App để xem/sửa
        view.getMnuRemViewEdit().addActionListener(e -> {
            List<String> files = getSelectedRemoteFiles();
            if (files.isEmpty()) return;
            String fileName = files.get(0);
            String currentDir = view.getTxtRemotePath().getText();

            new Thread(() -> {
                try {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"), "ftp_view_edit");
                    if (!tempDir.exists()) tempDir.mkdirs();

                    synchronized (model) {
                        model.changeWorkingDirectory(currentDir);
                        model.downloadFile(fileName, tempDir, null, null, 0);
                    }

                    File downloadedFile = new File(tempDir, fileName);
                    if (downloadedFile.exists()) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                Desktop.getDesktop().open(downloadedFile);
                                view.appendLog("Status: Đã mở file để View/Edit: " + fileName);
                            } catch (IOException ex) {
                                JOptionPane.showMessageDialog(view, "Không thể mở ứng dụng mặc định đọc file này.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> view.appendLog("Lỗi View/Edit: " + ex.getMessage()));
                }
            }).start();
        });

        view.getMnuRemShare().addActionListener(e -> {
            // Khách ẩn danh không được dùng tính năng chia sẻ
            String username = view.getTxtUsername().getText().trim();
            if ("anonymous".equalsIgnoreCase(username)) {
                JOptionPane.showMessageDialog(view, "Khách ẩn danh không được phép chia sẻ file!", "Từ chối", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<String> files = getSelectedRemoteFiles();
            if (files.isEmpty()) return;
            String fileName = files.get(0);

            JDialog shareDialog = new JDialog(view, "Chia sẻ: " + fileName, true);
            shareDialog.setSize(350, 220);
            shareDialog.setLocationRelativeTo(view);
            shareDialog.setLayout(new BorderLayout(10, 10));

            JPanel p = new JPanel(new GridLayout(4, 1, 5, 5));
            p.setBorder(BorderFactory.createEmptyBorder(15, 20, 5, 20));

            p.add(new JLabel("Nhập Username người nhận:"));
            JTextField txtTargetUser = new JTextField();
            p.add(txtTargetUser);

            JPanel permPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            JRadioButton rbRead = new JRadioButton("Chỉ xem");
            JRadioButton rbFull = new JRadioButton("Toàn quyền", true);
            ButtonGroup bg = new ButtonGroup();
            bg.add(rbRead); bg.add(rbFull);
            permPanel.add(new JLabel("Quyền:  "));
            permPanel.add(rbRead);
            permPanel.add(rbFull);
            p.add(permPanel);

            shareDialog.add(p, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnOk = new JButton("Chia sẻ");
            JButton btnCancel = new JButton("Hủy");

            btnOk.addActionListener(ev -> {
                String targetUser = txtTargetUser.getText().trim();
                if (targetUser.isEmpty()) {
                    JOptionPane.showMessageDialog(shareDialog, "Vui lòng nhập Username!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String permType = rbFull.isSelected() ? "FULL_CONTROL" : "READ_ONLY";

                new Thread(() -> {
                    try {
                        synchronized (model) {
                            model.changeWorkingDirectory(view.getTxtRemotePath().getText());
                            boolean ok = model.shareFile(fileName, targetUser, permType);
                            SwingUtilities.invokeLater(() -> {
                                if (ok) {
                                    view.appendLog("Thành công: Đã chia sẻ " + fileName + " cho " + targetUser);
                                    JOptionPane.showMessageDialog(shareDialog, "Chia sẻ thành công!");
                                    shareDialog.dispose();
                                } else {
                                    JOptionPane.showMessageDialog(shareDialog, "Chia sẻ thất bại! Kiểm tra lại Username.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                }
                            });
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> view.appendLog("Lỗi chia sẻ: " + ex.getMessage()));
                    }
                }).start();
            });

            btnCancel.addActionListener(ev -> shareDialog.dispose());
            btnPanel.add(btnOk); btnPanel.add(btnCancel);
            shareDialog.add(btnPanel, BorderLayout.SOUTH);

            shareDialog.setVisible(true);
        });

        view.getMnuRemPerms().addActionListener(e -> {
            // THÊM ĐOẠN CHẶN NÀY:
            String username = view.getTxtUsername().getText().trim();
            if ("anonymous".equalsIgnoreCase(username)) {
                JOptionPane.showMessageDialog(view, "TK ẩn danh không có quyền thay đổi Permissions!", "Từ chối", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int row = view.getRemoteTable().getSelectedRow();
            if (row == -1) return;
            String fileName = (String) view.getRemoteTable().getValueAt(row, 0);
            if ("..".equals(fileName)) return;

            showPermissionsDialog(fileName, row);
        });

        // remoteTable: Hỗ trợ kéo thả nội bộ (Di chuyển file vào thư mục khác)
        view.getRemoteTable().setDragEnabled(true);
        view.getRemoteTable().setDropMode(DropMode.ON);
        view.getRemoteTable().setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) { return MOVE; }

            @Override
            protected Transferable createTransferable(JComponent c) {
                List<String> selectedFiles = getSelectedRemoteFiles();
                if (selectedFiles.isEmpty()) return null;
                return new StringSelection("REMOTE_MOVE::" + String.join("::", selectedFiles));
            }

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDataFlavorSupported(DataFlavor.stringFlavor) || !model.isAuthenticated()) return false;
                JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                int row = dl.getRow();
                if (row < 0) return false;

                String type = (String) view.getRemoteTable().getValueAt(row, 2);
                boolean isDir = "File folder".equalsIgnoreCase(type) || type.toLowerCase().contains("thư mục") || "d".equalsIgnoreCase(type);
                return isDir;
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
                    int row = dl.getRow();
                    String targetFolderName = (String) view.getRemoteTable().getValueAt(row, 0);
                    if ("..".equals(targetFolderName)) return false;

                    String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    if (!data.startsWith("REMOTE_MOVE::")) return false;

                    String[] fileNames = data.replace("REMOTE_MOVE::", "").split("::");
                    String currentDir = view.getTxtRemotePath().getText();
                    String targetDirPath = currentDir.equals("/") ? "/" + targetFolderName : currentDir + "/" + targetFolderName;

                    new Thread(() -> {
                        try {
                            synchronized (model) {
                                for (String fn : fileNames) {
                                    if (fn.trim().isEmpty()) continue;
                                    String oldFullPath = currentDir.equals("/") ? "/" + fn : currentDir + "/" + fn;
                                    String newFullPath = targetDirPath + "/" + fn;
                                    model.renameOrMoveFile(oldFullPath, newFullPath);
                                    view.appendLog("Status: Đã di chuyển '" + fn + "' vào '" + targetFolderName + "'");
                                }
                            }
                            SwingUtilities.invokeLater(() -> loadRemoteFilesToTable(currentDir));
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> view.appendLog("Lỗi di chuyển: " + ex.getMessage()));
                        }
                    }).start();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        // --- Xử lý Cut, Copy, Paste trên Remote ---
        view.getMnuRemCopy().addActionListener(e -> {
            List<String> selected = getSelectedRemoteFiles();
            if (selected.isEmpty()) return;
            clipboardFileName = selected.get(0);
            String currentDir = view.getTxtRemotePath().getText();
            clipboardSourcePath = currentDir.equals("/") ? "/" + clipboardFileName : currentDir + "/" + clipboardFileName;
            isCutAction = false;
            view.appendLog("Status: Đã Copy file/thư mục: " + clipboardFileName);
        });

        view.getMnuRemCut().addActionListener(e -> {
            List<String> selected = getSelectedRemoteFiles();
            if (selected.isEmpty()) return;
            clipboardFileName = selected.get(0);
            String currentDir = view.getTxtRemotePath().getText();
            clipboardSourcePath = currentDir.equals("/") ? "/" + clipboardFileName : currentDir + "/" + clipboardFileName;
            isCutAction = true;
            view.appendLog("Status: Đã Cut file/thư mục: " + clipboardFileName);
        });

        view.getMnuRemPaste().addActionListener(e -> {
            if (clipboardSourcePath == null || clipboardFileName == null) {
                JOptionPane.showMessageDialog(view, "Clipboard trống!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String currentDir = view.getTxtRemotePath().getText();

            // Tự động thêm (1), (2) nếu đang Copy (Cut thì giữ nguyên tên gốc)
            String finalTargetName = isCutAction ? clipboardFileName : getUniqueFileName(clipboardFileName);
            String targetPath = currentDir.equals("/") ? "/" + finalTargetName : currentDir + "/" + finalTargetName;

            new Thread(() -> {
                try {
                    synchronized (model) {
                        if (isCutAction) {
                            boolean ok = model.renameOrMoveFile(clipboardSourcePath, targetPath);
                            if (ok) {
                                view.appendLog("Status: Di chuyển thành công tới " + targetPath);
                                clipboardSourcePath = null;
                                clipboardFileName = null;
                            } else {
                                view.appendLog("Error: Server từ chối di chuyển file.");
                            }
                        } else {
                            // Xử lý Copy bản sao
                            File tempDir = new File(System.getProperty("java.io.tmpdir"), "ftp_copy_paste");
                            if (!tempDir.exists()) tempDir.mkdirs();

                            int lastSlash = clipboardSourcePath.lastIndexOf('/');
                            String sourceDir = lastSlash > 0 ? clipboardSourcePath.substring(0, lastSlash) : "/";
                            String srcName = clipboardSourcePath.substring(lastSlash + 1);

                            model.changeWorkingDirectory(sourceDir);
                            model.downloadFile(srcName, tempDir, null, null, 0);

                            File downloadedTemp = new File(tempDir, srcName);
                            if (downloadedTemp.exists()) {
                                // Đổi tên file tạm thành tên đã được đánh số (VD: file(1).txt)
                                File fileToUpload = new File(tempDir, finalTargetName);
                                downloadedTemp.renameTo(fileToUpload);

                                model.changeWorkingDirectory(currentDir);
                                model.uploadFile(fileToUpload, null, null);
                                fileToUpload.delete();
                                view.appendLog("Status: Copy & Paste thành công bản sao: " + finalTargetName);
                            }
                        }
                    }
                    SwingUtilities.invokeLater(() -> loadRemoteFilesToTable(currentDir));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> view.appendLog("Lỗi Paste: " + ex.getMessage()));
                }
            }).start();
        });
    }

    private List<File> getSelectedLocalFiles() {
        int[] rows = view.getLocalTable().getSelectedRows();
        List<File> files = new ArrayList<>();
        String currentPath = view.getTxtLocalPath().getText();
        for (int r : rows) {
            String name = (String) view.getLocalTable().getValueAt(r, 0);
            if (!"..".equals(name)) files.add(new File(currentPath, name));
        }
        return files;
    }

    private List<String> getSelectedRemoteFiles() {
        int[] rows = view.getRemoteTable().getSelectedRows();
        List<String> files = new ArrayList<>();
        for (int r : rows) {
            String name = (String) view.getRemoteTable().getValueAt(r, 0);
            if (!"..".equals(name)) files.add(name);
        }
        return files;
    }

    private void showDevMsg() {
        JOptionPane.showMessageDialog(view, "Chức năng này đang được phát triển ở phiên bản API sau.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }

    // --- UPLOAD / DOWNLOAD ---

    // GỌI KHI BẤM NÚT UPLOAD TỪ MENU
    private void handleUpload() {
        List<File> uploadFiles = getSelectedLocalFiles();
        if (uploadFiles.isEmpty() || !model.isAuthenticated()) return;
        handleUploadFilesList(uploadFiles);
    }

    // GỌI CHUNG CHO MENU UPLOAD VÀ KHI KÉO THẢ TỪ LOCAL QUA REMOTE
    private void handleUploadFilesList(List<File> uploadFiles) {
        String username = view.getTxtUsername().getText().trim();
        if ("anonymous".equalsIgnoreCase(username)) {
            JOptionPane.showMessageDialog(view, "Tài khoản Anonymous không được phép tải lên!", "Từ chối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Tạo Popup chỉ cho chọn User hoặc Public
        String personalPath = "/users/" + username;
        String[] options = {"Thư mục Cá nhân (" + personalPath + ")", "Thư mục Chung (/public)"};
        JComboBox<String> cbDirs = new JComboBox<>(options);
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        panel.add(new JLabel("Chọn thư mục trên Server để lưu file:"));
        panel.add(cbDirs);

        if (JOptionPane.showConfirmDialog(view, panel, "Cấu hình Upload", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        String targetRemoteDir = cbDirs.getSelectedIndex() == 0 ? personalPath : "/public";

        new Thread(() -> {
            for (File uploadFile : uploadFiles) {
                try {
                    uploadRecursive(uploadFile, targetRemoteDir); // Hàm đệ quy gọi từng file
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> view.appendLog("Lỗi tải lên '" + uploadFile.getName() + "': " + ex.getMessage()));
                }
            }
            SwingUtilities.invokeLater(() -> {
                view.appendLog("Hoàn tất tải lên!");
                updateQuotaUI();
                if (view.getTxtRemotePath().getText().equals(targetRemoteDir)) loadRemoteFilesToTable(targetRemoteDir);
            });
        }).start();
    }

    // GỌI KHI BẤM NÚT DOWNLOAD TỪ MENU
    private void handleDownload(boolean openAfterDownload) {
        List<String> remoteFiles = getSelectedRemoteFiles();
        if (remoteFiles.isEmpty() || !model.isAuthenticated()) return;
        handleDownloadFilesList(remoteFiles, openAfterDownload);
    }

    // GỌI CHUNG CHO MENU DOWNLOAD VÀ KHI KÉO THẢ TỪ REMOTE QUA LOCAL
    private void handleDownloadFilesList(List<String> remoteFiles, boolean openAfterDownload) {
        String currentRemoteDir = view.getTxtRemotePath().getText();
        String username = view.getTxtUsername().getText().trim();

        // Anonymous chỉ được down từ public
        if ("anonymous".equalsIgnoreCase(username) && !currentRemoteDir.contains("/public")) {
            JOptionPane.showMessageDialog(view, "Anonymous chỉ có thể tải file từ mục /public!", "Từ chối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser(view.getTxtLocalPath().getText());
        chooser.setDialogTitle("Chọn thư mục lưu");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) return;

        File destFolder = chooser.getSelectedFile();

        new Thread(() -> {
            for (String fileName : remoteFiles) {
                try {
                    boolean isDir = false; long size = 0;
                    for(int i=0; i<view.getRemoteTable().getRowCount(); i++) {
                        if (fileName.equals(view.getRemoteTable().getValueAt(i, 0))) {
                            String type = (String) view.getRemoteTable().getValueAt(i, 2);
                            isDir = "File folder".equalsIgnoreCase(type) || type.toLowerCase().contains("thư mục");
                            size = isDir ? 0 : parseSizeFromFormattedString((String) view.getRemoteTable().getValueAt(i, 1));
                            break;
                        }
                    }
                    downloadRecursive(fileName, isDir, currentRemoteDir, destFolder, size);

                    if (openAfterDownload && !isDir) {
                        try { Desktop.getDesktop().open(new File(destFolder, fileName)); }
                        catch (IOException ex) { view.appendLog("Không thể mở tự động: " + ex.getMessage()); }
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> view.appendLog("Lỗi tải xuống '" + fileName + "': " + ex.getMessage()));
                }
            }
            SwingUtilities.invokeLater(() -> {
                view.appendLog("Hoàn tất tải xuống!");
                if (view.getTxtLocalPath().getText().equals(destFolder.getAbsolutePath())) loadLocalFilesToTable(destFolder);
            });
        }).start();
    }

    private void uploadRecursive(File localFile, String targetRemoteDir) throws Exception {
        if (localFile.isDirectory()) {
            String newRemoteDir = targetRemoteDir.equals("/") ? "/" + localFile.getName() : targetRemoteDir + "/" + localFile.getName();
            synchronized (model) {
                model.changeWorkingDirectory(targetRemoteDir);
                model.createDirectory(localFile.getName());
            }
            File[] files = localFile.listFiles();
            if (files != null) {
                for (File f : files) { uploadRecursive(f, newRemoteDir); }
            }
        } else {
            TransferProgressDialog dialog = new TransferProgressDialog(view, "Đang tải lên Server", localFile.getName());
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
            try {
                synchronized (model) {
                    model.changeWorkingDirectory(targetRemoteDir);
                    boolean ok = model.uploadFile(localFile, dialog::updateProgress, dialog);
                    if (!ok && !dialog.isCancelled()) throw new Exception("Server từ chối nhận file: " + localFile.getName());
                }
            } finally {
                SwingUtilities.invokeLater(dialog::dispose);
            }
            if (dialog.isCancelled()) throw new Exception("Người dùng đã hủy tải lên.");
        }
    }

    private void downloadRecursive(String itemName, boolean isDir, String currentRemoteDir, File localDestFolder, long knownSize) throws Exception {
        if (isDir) {
            File newLocalFolder = new File(localDestFolder, itemName);
            if (!newLocalFolder.exists()) newLocalFolder.mkdirs();

            String newRemoteDir = currentRemoteDir.equals("/") ? "/" + itemName : currentRemoteDir + "/" + itemName;
            List<String> children;
            synchronized (model) {
                model.changeWorkingDirectory(newRemoteDir);
                children = model.fetchFileList();
            }

            if (children != null) {
                for (String raw : children) {
                    String childName = parseFileName(raw);
                    if (childName.equals(".") || childName.equals("..")) continue;
                    boolean childIsDir = isDirectory(raw);
                    long childSize = childIsDir ? 0 : parseFileSizeBytes(raw);
                    downloadRecursive(childName, childIsDir, newRemoteDir, newLocalFolder, childSize);
                }
            }
        } else {
            TransferProgressDialog dialog = new TransferProgressDialog(view, "Đang tải xuống Máy tính", itemName);
            SwingUtilities.invokeLater(() -> dialog.setVisible(true));
            try {
                synchronized (model) {
                    model.changeWorkingDirectory(currentRemoteDir);
                    model.downloadFile(itemName, localDestFolder, dialog::updateProgress, dialog, knownSize);
                }
            } finally {
                SwingUtilities.invokeLater(dialog::dispose);
            }
            if (dialog.isCancelled()) throw new Exception("Người dùng đã hủy tải xuống.");
        }
    }

    // --- CẬP NHẬT: ĐỌC LẠI PERMISSIONS HIỆN TẠI VÀ CHỈNH LẠI MÃ SỐ ---
    private void showPermissionsDialog(String fileName, int row) {
        JDialog permDialog = new JDialog(view, "Cấu hình quyền (Permissions) - " + fileName, true);
        permDialog.setSize(350, 220);
        permDialog.setLocationRelativeTo(view);
        permDialog.setLayout(new BorderLayout(10, 10));

        JPanel p = new JPanel(new GridLayout(3, 1, 5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        p.add(new JLabel("Chọn mức quyền cho file này:"));

        JRadioButton rbRead = new JRadioButton("Read Only (Chỉ xem / Tải xuống)");
        JRadioButton rbFull = new JRadioButton("Full Control (Toàn quyền)");

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbRead); bg.add(rbFull);

        // Lấy giá trị quyền hiện tại từ cột thứ 5 (index 4) của dòng đang chọn trên bảng Remote
        String currentPerm = (String) view.getRemoteTable().getValueAt(row, 4);
        if ("READ_ONLY".equalsIgnoreCase(currentPerm)) {
            rbRead.setSelected(true);
        } else {
            rbFull.setSelected(true); // Mặc định nếu là FULL_CONTROL
        }

        p.add(rbRead); p.add(rbFull);
        permDialog.add(p, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("Lưu thay đổi");
        JButton btnCancel = new JButton("Hủy");

        btnOk.addActionListener(e -> {
            String permType = rbFull.isSelected() ? "FULL_CONTROL" : "READ_ONLY";
            String currentDir = view.getTxtRemotePath().getText();

            new Thread(() -> {
                try {
                    synchronized (model) {
                        model.changeWorkingDirectory(currentDir);
                        boolean ok = model.setFilePermissions(fileName, permType);
                        SwingUtilities.invokeLater(() -> {
                            if (ok) {
                                view.appendLog("Thành công: Đã cập nhật quyền thành " + permType + " cho " + fileName);
                                JOptionPane.showMessageDialog(permDialog, "Đã lưu quyền thành công!");
                                permDialog.dispose();
                                loadRemoteFilesToTable(currentDir); // Refresh lại bảng
                            } else {
                                JOptionPane.showMessageDialog(permDialog, "Cập nhật quyền thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        });
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(permDialog, "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        });

        btnCancel.addActionListener(e -> permDialog.dispose());
        btnPanel.add(btnOk); btnPanel.add(btnCancel);
        permDialog.add(btnPanel, BorderLayout.SOUTH);
        permDialog.setVisible(true);
    }

    // ==========================================
    // XỬ LÝ GIAO DIỆN CÂY VÀ BẢNG
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

        view.getLocalTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = view.getLocalTable().getSelectedRow();
                    if (row == -1) return;
                    String name = (String) view.getLocalTable().getValueAt(row, 0);
                    if ("..".equals(name)) {
                        File parent = new File(view.getTxtLocalPath().getText()).getParentFile();
                        if (parent != null) {
                            view.getTxtLocalPath().setText(parent.getAbsolutePath());
                            loadLocalFilesToTable(parent);
                        }
                        return;
                    }
                    String type = (String) view.getLocalTable().getValueAt(row, 2);
                    if ("File folder".equalsIgnoreCase(type) || type.toLowerCase().contains("thư mục")) {
                        File newDir = new File(view.getTxtLocalPath().getText(), name);
                        if (newDir.exists() && newDir.isDirectory()) {
                            view.getTxtLocalPath().setText(newDir.getAbsolutePath());
                            loadLocalFilesToTable(newDir);
                        }
                    } else {
                        try {
                            Desktop.getDesktop().open(new File(view.getTxtLocalPath().getText(), name));
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(view, "Không thể mở file này.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            }
        });
    }

    private void loadLocalFilesToTable(File dir) {
        DefaultTableModel tableModel = (DefaultTableModel) view.getLocalTable().getModel();
        tableModel.setRowCount(0);
        if (dir.getParentFile() != null) tableModel.addRow(new Object[]{"..", "", "File folder", ""});
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

    private void initRemoteEvents() {
        view.getRemoteTree().addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (!(node.getUserObject() instanceof RemoteNodeInfo)) return;
                RemoteNodeInfo info = (RemoteNodeInfo) node.getUserObject();
                if (!info.isLoaded) {
                    info.isLoaded = true;
                    loadRemoteSubDirectories(node, info);
                }
            }
            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        view.getRemoteTree().addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) view.getRemoteTree().getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof RemoteNodeInfo)) return;
            RemoteNodeInfo info = (RemoteNodeInfo) node.getUserObject();
            view.getTxtRemotePath().setText(info.ftpPath);
            loadRemoteFilesToTable(info.ftpPath);
        });

        view.getRemoteTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = view.getRemoteTable().getSelectedRow();
                    if (row == -1) return;

                    String name = (String) view.getRemoteTable().getValueAt(row, 0);

                    // Quay lại thư mục cha
                    if ("..".equals(name)) {
                        String currentPath = view.getTxtRemotePath().getText();
                        String username = view.getTxtUsername().getText().trim();

                        if (!currentPath.equals("/") && !currentPath.equals("SHARED")) {

                            String newPath;

                            // User thường + Anonymous: /users/<username> -> /
                            if (!"admin".equalsIgnoreCase(username)
                                    && currentPath.equals("/users/" + username)) {

                                newPath = "/";

                                // Admin: /users -> /
                            } else if ("admin".equalsIgnoreCase(username)
                                    && currentPath.equals("/users")) {

                                newPath = "/";

                            } else {
                                int lastSlash = currentPath.lastIndexOf('/');
                                newPath = (lastSlash > 0)
                                        ? currentPath.substring(0, lastSlash)
                                        : "/";
                            }

                            loadRemoteFilesToTable(newPath);
                        }
                        return;
                    }

                    String type = (String) view.getRemoteTable().getValueAt(row, 2);

                    if ("File folder".equalsIgnoreCase(type)
                            || type.toLowerCase().contains("thư mục")
                            || "d".equalsIgnoreCase(type)) {

                        String currentPath = view.getTxtRemotePath().getText();
                        String newPath;

                        if ("/".equals(currentPath)) {

                            String username = view.getTxtUsername().getText().trim();

                            switch (name) {
                                case "user":
                                    if ("admin".equalsIgnoreCase(username)) {
                                        newPath = "/users";
                                    } else if ("anonymous".equalsIgnoreCase(username)) {
                                        newPath = "/public";
                                    } else {
                                        newPath = "/users/" + username;
                                    }
                                    break;

                                case "public":
                                    newPath = "/public";
                                    break;

                                case "shared":
                                    newPath = "SHARED";
                                    break;

                                default:
                                    newPath = "/" + name;
                                    break;
                            }

                        } else {
                            newPath = currentPath + "/" + name;
                        }

                        loadRemoteFilesToTable(newPath);

                    } else {
                        List<String> toDownload = new ArrayList<>();
                        toDownload.add(name);
                        handleDownloadFilesList(toDownload, true);
                    }
                }
            }
        });
    }

    private void initRemoteRootNode() {
        String username = view.getTxtUsername().getText().trim();
        RemoteNodeInfo rootInfo = new RemoteNodeInfo("/", "/");
        rootInfo.isLoaded = true;
        DefaultMutableTreeNode remoteRoot = new DefaultMutableTreeNode(rootInfo);

        DefaultMutableTreeNode defaultSelectedNode;
        String initialPath;

        if ("anonymous".equalsIgnoreCase(username)) {
            // --- ẨN DANH: Chỉ tạo node public, không có node user/shared ---
            DefaultMutableTreeNode publicNode = new DefaultMutableTreeNode(new RemoteNodeInfo("public", "/public"));
            publicNode.add(new DefaultMutableTreeNode("Đang tải..."));
            remoteRoot.add(publicNode);

            defaultSelectedNode = publicNode;
            initialPath = "/public";
        } else {
            // --- ADMIN VÀ USER THƯỜNG ---
            String personalPath = "admin".equalsIgnoreCase(username) ? "/users" : "/users/" + username;

            DefaultMutableTreeNode personalNode = new DefaultMutableTreeNode(new RemoteNodeInfo("user", personalPath));
            personalNode.add(new DefaultMutableTreeNode("Đang tải..."));

            DefaultMutableTreeNode publicNode = new DefaultMutableTreeNode(new RemoteNodeInfo("public", "/public"));
            publicNode.add(new DefaultMutableTreeNode("Đang tải..."));

            remoteRoot.add(personalNode);
            remoteRoot.add(publicNode);

            DefaultMutableTreeNode sharedNode = new DefaultMutableTreeNode(new RemoteNodeInfo("shared", "SHARED"));
            remoteRoot.add(sharedNode);

            defaultSelectedNode = personalNode;
            initialPath = personalPath;
        }

        DefaultTreeModel treeModel = new DefaultTreeModel(remoteRoot);
        view.getRemoteTree().setModel(treeModel);
        view.getRemoteTree().setRootVisible(true);
        view.getTxtRemotePath().setText("/");

        view.getRemoteTree().expandPath(new TreePath(remoteRoot.getPath()));
        view.getRemoteTree().setSelectionPath(new TreePath(defaultSelectedNode.getPath()));

        loadRemoteFilesToTable(initialPath);
    }

    private void loadRemoteSubDirectories(DefaultMutableTreeNode parentNode, RemoteNodeInfo parentInfo) {
        if ("SHARED".equals(parentInfo.ftpPath) || "/".equals(parentInfo.ftpPath)) {
            SwingUtilities.invokeLater(() -> {
                parentNode.removeAllChildren();
                ((DefaultTreeModel) view.getRemoteTree().getModel()).reload(parentNode);
            });
            return;
        }

        new Thread(() -> {
            try {
                List<String> files;
                synchronized (model) {
                    boolean success = model.changeWorkingDirectory(parentInfo.ftpPath);
                    if (!success) {
                        SwingUtilities.invokeLater(() -> {
                            parentInfo.isLoaded = false;
                            parentNode.removeAllChildren();
                            ((DefaultTreeModel) view.getRemoteTree().getModel()).reload(parentNode);
                            view.appendLog("Warning: Server từ chối chuyển tới " + parentInfo.ftpPath);
                        });
                        return;
                    }
                    files = model.fetchFileList();
                }

                SwingUtilities.invokeLater(() -> {
                    parentNode.removeAllChildren();
                    if (files != null) {
                        for (String raw : files) {
                            if (isDirectory(raw)) {
                                String folderName = parseFileName(raw);
                                if (folderName.isEmpty() || folderName.equals(".") || folderName.equals("..")) continue;
                                String subPath = parentInfo.ftpPath.equals("/") ? "/" + folderName : parentInfo.ftpPath + "/" + folderName;
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
                    parentInfo.isLoaded = false;
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
                List<String> tempFiles = new ArrayList<>();
                boolean isShared = "SHARED".equals(ftpPath);
                boolean isRoot = "/".equals(ftpPath);
                String currentLoginUser = view.getTxtUsername().getText().trim();

                if (isRoot) {
                    String dateStr = sdf.format(new Date());
                    tempFiles.add("d|0|" + dateStr + "|user|system");
                    tempFiles.add("d|0|" + dateStr + "|public|system");
                    if (!"anonymous".equalsIgnoreCase(currentLoginUser)) tempFiles.add("d|0|" + dateStr + "|shared|system");
                } else {
                    synchronized (model) {
                        if (isShared) {
                            tempFiles = model.fetchSharedFileList();
                        } else {
                            if (!model.changeWorkingDirectory(ftpPath)) {
                                SwingUtilities.invokeLater(() ->
                                        view.appendLog("Warning: Không thể chuyển tới " + ftpPath + " (không tồn tại hoặc không có quyền)."));
                                return; // <-- KHÔNG đụng tới path bar khi thất bại
                            }
                            tempFiles = model.fetchFileList();
                        }
                    }
                }

                final List<String> finalFiles = tempFiles;
                SwingUtilities.invokeLater(() -> {
                    view.getTxtRemotePath().setText(ftpPath); // CHỈ set khi đã chắc chắn thành công
                    DefaultTableModel tableModel = (DefaultTableModel) view.getRemoteTable().getModel();
                    tableModel.setRowCount(0);
                    if (!isRoot && !isShared) tableModel.addRow(new Object[]{"..", "", "File folder", "", "", ""});

                    if (finalFiles != null) {
                        for (String raw : finalFiles) {
                            if (isShared) {
                                String[] parts = raw.split("\\|", -1);
                                if (parts.length >= 1) {
                                    String name = parts[0].trim();
                                    String date = parts.length > 1 ? parts[1].trim() : "--";
                                    String type = parts.length > 2 ? parts[2].trim() : "File";
                                    String size = parts.length > 3 ? formatFileSizeString(parts[3].trim()) : "--";
                                    String owner = parts.length > 4 ? parts[4].trim() : "Unknown";

                                    // Đọc quyền chia sẻ trực tiếp từ Database
                                    String permStr = parts.length > 5 ? parts[5].trim() : "READ_ONLY";

                                    // Bỏ tự động phân giải số 0777/0755, lấy trực tiếp chữ
                                    String perms = permStr;

                                    tableModel.addRow(new Object[]{name, size, type, date, perms, owner});
                                }
                            } else {
                                String name = parseFileName(raw);
                                if (name.equals(".") || name.equals("..")) continue;
                                boolean isDir = isDirectory(raw);
                                String type = isDir ? "File folder" : "File";
                                String size = isDir ? "" : parseFileSize(raw);
                                String date = parseFileDate(raw);

                                // --- HIỂN THỊ TRỰC TIẾP CHỮ TRONG DATABASE ---
                                String perms = "FULL_CONTROL"; // Mặc định
                                String owner = currentLoginUser;

                                if (raw.contains("|")) {
                                    String[] p = raw.split("\\|");
                                    if (p.length >= 5) owner = p[4].trim();
                                    if (p.length >= 6) perms = p[5].trim(); // Nhận thẳng READ_ONLY hoặc FULL_CONTROL từ Server
                                }

                                if (isRoot) {
                                    perms = "user".equals(name) ? "FULL_CONTROL" : "READ_ONLY";
                                }
                                // Đã sửa: Xóa/Comment đoạn ép quyền READ_ONLY cho /public để lấy trực tiếp quyền từ Server
                                // else if (ftpPath.contains("/public")) {
                                //     perms = "READ_ONLY"; // Thư mục public mặc định là Read Only
                                // }

                                tableModel.addRow(new Object[]{name, size, type, date, perms, owner});
                            }
                        }
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> view.appendLog("Error: Lỗi tải file - " + e.getMessage()));
            }
        }).start();
    }
    // --- PARSER ---
    private boolean isDirectory(String rawStr) {
        if (rawStr.contains("|")) return rawStr.startsWith("d|");
        return rawStr.trim().startsWith("d") || rawStr.contains("<DIR>");
    }

    private String parseFileName(String rawStr) {
        if (rawStr.contains("|")) {
            String[] p = rawStr.split("\\|");
            return p.length >= 4 ? p[3].trim() : rawStr;
        }
        if (rawStr.matches("^[\\d\\-]+\\s+[\\d\\:\\w]+\\s+.*")) {
            String[] p = rawStr.trim().split("\\s+", 4);
            return p.length == 4 ? p[3] : rawStr;
        }
        String[] p = rawStr.split("\\s+", 9);
        return p.length >= 9 ? p[8] : rawStr;
    }

    private String parseFileSize(String rawStr) {
        if (isDirectory(rawStr)) return "";
        if (rawStr.contains("|")) {
            String[] p = rawStr.split("\\|");
            try { return formatFileSize(Long.parseLong(p[1].trim())); } catch (Exception ignored) {}
            return "";
        }
        if (rawStr.matches("^[\\d\\-]+\\s+[\\d\\:\\w]+\\s+.*")) {
            String[] p = rawStr.trim().split("\\s+", 4);
            if (p.length >= 3) {
                try { return formatFileSize(Long.parseLong(p[2])); } catch (Exception ignored) {}
            }
            return "";
        }
        String[] p = rawStr.split("\\s+", 9);
        if (p.length >= 5) {
            try { return formatFileSize(Long.parseLong(p[4])); } catch (Exception ignored) {}
        }
        return "";
    }

    private long parseFileSizeBytes(String rawStr) {
        if (isDirectory(rawStr)) return 0;
        if (rawStr.contains("|")) {
            String[] p = rawStr.split("\\|");
            try { return Long.parseLong(p[1].trim()); } catch (Exception ignored) {}
            return 0;
        }
        if (rawStr.matches("^[\\d\\-]+\\s+[\\d\\:\\w]+\\s+.*")) {
            String[] p = rawStr.trim().split("\\s+", 4);
            if (p.length >= 3) {
                try { return Long.parseLong(p[2]); } catch (Exception ignored) {}
            }
            return 0;
        }
        String[] p = rawStr.split("\\s+", 9);
        if (p.length >= 5) {
            try { return Long.parseLong(p[4]); } catch (Exception ignored) {}
        }
        return 0;
    }

    private String parseFileDate(String rawStr) {
        if (rawStr.contains("|")) {
            String[] p = rawStr.split("\\|");
            return p.length >= 3 ? p[2].trim() : "";
        }
        if (rawStr.matches("^[\\d\\-]+\\s+[\\d\\:\\w]+\\s+.*")) {
            String[] p = rawStr.trim().split("\\s+", 4);
            return p.length >= 2 ? p[0] + " " + p[1] : "";
        }
        String[] p = rawStr.split("\\s+", 9);
        if (p.length >= 8) {
            return p[5] + " " + p[6] + " " + p[7];
        }
        return "";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String formatFileSizeString(String rawSize) {
        try { return formatFileSize(Long.parseLong(rawSize.trim())); }
        catch (Exception e) { return rawSize; }
    }

    private long parseSizeFromFormattedString(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty() || sizeStr.equals("--")) return 100 * 1024 * 1024;
        try {
            sizeStr = sizeStr.trim();
            if (sizeStr.endsWith(" B")) return Long.parseLong(sizeStr.replace(" B", "").trim());
            if (sizeStr.endsWith(" KB")) return (long) (Double.parseDouble(sizeStr.replace(" KB", "").trim()) * 1024);
            if (sizeStr.endsWith(" MB")) return (long) (Double.parseDouble(sizeStr.replace(" MB", "").trim()) * 1024 * 1024);
            if (sizeStr.endsWith(" GB")) return (long) (Double.parseDouble(sizeStr.replace(" GB", "").trim()) * 1024 * 1024 * 1024);
            return Long.parseLong(sizeStr);
        } catch (Exception e) { return 100 * 1024 * 1024; }
    }

    private String getUniqueFileName(String baseName) {
        DefaultTableModel tableModel = (DefaultTableModel) view.getRemoteTable().getModel();
        List<String> existingNames = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            existingNames.add((String) tableModel.getValueAt(i, 0));
        }

        if (!existingNames.contains(baseName)) return baseName;

        String nameWithoutExt = baseName;
        String ext = "";
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = baseName.substring(0, dotIndex);
            ext = baseName.substring(dotIndex);
        }

        int counter = 1;
        String newName;
        do {
            newName = nameWithoutExt + "(" + counter + ")" + ext;
            counter++;
        } while (existingNames.contains(newName));

        return newName;
    }

    public void updateQuotaUI() {
        if (!model.isAuthenticated()) {
            SwingUtilities.invokeLater(() -> {
                view.getPbQuota().setValue(0);
                view.getPbQuota().setString("0 MB / 0 MB");
                view.getPbQuota().setForeground(UIManager.getColor("ProgressBar.foreground"));
            });
            return;
        }
        new Thread(() -> {
            long[] quota;

            // --- THÊM KHÓA ĐỒNG BỘ (SYNCHRONIZED) ---
            // Đảm bảo không bị nhiễu sóng với luồng tải danh sách file
            synchronized (model) {
                quota = model.getQuota();
            }

            long used = quota[0];
            long max = quota[1];

            SwingUtilities.invokeLater(() -> {
                double usedMB = used / (1024.0 * 1024.0);

                if (max == -1) {
                    // XỬ LÝ CHO ADMIN: Vô hạn
                    view.getPbQuota().setValue(100);
                    view.getPbQuota().setString(String.format("%.1f MB / ∞", usedMB));
                    view.getPbQuota().setForeground(new Color(30, 144, 255));
                } else if (max > 0) {
                    // XỬ LÝ CHO USER THƯỜNG
                    int percent = (int) ((used * 100) / max);
                    view.getPbQuota().setValue(percent);

                    double maxMB = max / (1024.0 * 1024.0);
                    view.getPbQuota().setString(String.format("%.1f MB / %.1f MB", usedMB, maxMB));

                    if (percent >= 90) {
                        view.getPbQuota().setForeground(Color.RED);
                    } else {
                        view.getPbQuota().setForeground(new Color(50, 205, 50));
                    }
                }
            });
        }).start();
    }

    public static class RemoteNodeInfo {
        public String name;
        public String ftpPath;
        public boolean isLoaded = false;
        public RemoteNodeInfo(String name, String ftpPath) { this.name = name; this.ftpPath = ftpPath; }
        @Override public String toString() { return name; }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            FTPClientModel model = new FTPClientModel();
            MainClientView view = new MainClientView();
            new MainClientController(view, model);
            view.setVisible(true);
        });
    }
}