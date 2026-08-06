package client.view;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FTPClientView extends JFrame {
    private JLabel lblWelcome;
    private JTable tableFiles;
    private DefaultTableModel tableModel;
    private JScrollPane scrollTable;
    private JTextArea logArea;
    private JButton btnUpload, btnDownload, btnDelete, btnRefresh, btnExit;

    private JButton btnMkdir, btnShare;
    private JButton btnNotification;
    private JTabbedPane tabbedPane;

    private JPanel panelPersonal, panelShared, panelPublic;

    private JPopupMenu popupMenu;
    private JMenuItem menuShare, menuMkdir, menuDownload, menuDelete;

    public FTPClientView(String username) {
        setTitle("Ứng dụng Quản lý File FTP - Phía Client");
        setSize(950, 680);
        setMinimumSize(new Dimension(850, 500));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // --- 1. HEADER ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(15, 15, 0, 15));

        lblWelcome = new JLabel("Xin chào, " + username);
        lblWelcome.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(lblWelcome, BorderLayout.WEST);

        // Nút thông báo chia sẻ đồng bộ kích thước 160x36
        btnNotification = createActionButton("Thông báo chia sẻ (0)");
        btnNotification.setCursor(new Cursor(Cursor.HAND_CURSOR));
        headerPanel.add(btnNotification, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // --- 2. TRUNG TÂM (BẢNG FILE EXPLORER + NHẬT KÝ) ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(new EmptyBorder(0, 15, 15, 0));

        String[] defaultColumns = {"Name", "Date Modified", "Type", "Size"};
        tableModel = new DefaultTableModel(defaultColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableFiles = new JTable(tableModel);
        tableFiles.setRowHeight(32);
        tableFiles.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tableFiles.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));

        tableFiles.setShowGrid(false);
        tableFiles.setShowHorizontalLines(false);
        tableFiles.setShowVerticalLines(false);
        tableFiles.setIntercellSpacing(new Dimension(0, 0));
        tableFiles.setFillsViewportHeight(true);

        scrollTable = new JScrollPane(tableFiles);
        scrollTable.setBorder(BorderFactory.createEmptyBorder());
        scrollTable.getViewport().setBackground(tableFiles.getBackground());

        // Chuột phải (Popup Menu)
        initPopupMenu();
        tableFiles.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { handlePopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { handlePopup(e); }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = tableFiles.rowAtPoint(e.getPoint());
                    if (row != -1 && !tableFiles.isRowSelected(row)) {
                        tableFiles.setRowSelectionInterval(row, row);
                    }
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        panelPersonal = new JPanel(new BorderLayout());
        panelShared = new JPanel(new BorderLayout());
        panelPublic = new JPanel(new BorderLayout());

        panelPersonal.add(scrollTable, BorderLayout.CENTER);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabbedPane.addTab("Thư mục Cá nhân", panelPersonal);
        tabbedPane.addTab("Được chia sẻ với tôi", panelShared);
        tabbedPane.addTab("Thư mục Chung", panelPublic);

        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        // Nhật ký hoạt động
        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(new CompoundBorder(
                new TitledBorder("Nhật ký hoạt động"),
                new EmptyBorder(5, 5, 5, 5)
        ));

        centerPanel.add(logScrollPane, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // --- 3. CỘT NÚT HÀNH ĐỘNG (BÊN PHẢI) ---
        JPanel actionPanel = new JPanel();
        actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
        actionPanel.setBorder(new EmptyBorder(25, 0, 15, 15));

        btnUpload = createActionButton("Upload");
        btnDownload = createActionButton("Download");
        btnMkdir = createActionButton("Tạo thư mục");
        btnShare = createActionButton("Chia sẻ...");
        btnDelete = createActionButton("Delete");
        btnRefresh = createActionButton("Làm mới danh sách");
        btnExit = createActionButton("Đăng xuất");

        actionPanel.add(btnUpload);
        actionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        actionPanel.add(btnDownload);
        actionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        actionPanel.add(btnMkdir);
        actionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        actionPanel.add(btnShare);
        actionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        actionPanel.add(btnDelete);
        actionPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        actionPanel.add(btnRefresh);
        actionPanel.add(Box.createVerticalGlue());
        actionPanel.add(btnExit);

        add(actionPanel, BorderLayout.EAST);
    }

    public void attachTableToTab(int index) {
        JPanel targetPanel = (JPanel) tabbedPane.getComponentAt(index);
        targetPanel.add(scrollTable, BorderLayout.CENTER);
        targetPanel.revalidate();
        targetPanel.repaint();
    }

    private void initPopupMenu() {
        popupMenu = new JPopupMenu();
        menuShare = new JMenuItem("Share");
        menuMkdir = new JMenuItem("New Folder");
        menuDownload = new JMenuItem("Download");
        menuDelete = new JMenuItem("Delete");

        popupMenu.add(menuShare);
        popupMenu.add(menuMkdir);
        popupMenu.addSeparator();
        popupMenu.add(menuDownload);
        popupMenu.add(menuDelete);
    }

    private JButton createActionButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(160, 36));
        btn.setMaximumSize(new Dimension(160, 36));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setFocusPainted(false);
        return btn;
    }

    public void setNotificationCount(int count) {
        SwingUtilities.invokeLater(() -> btnNotification.setText("Thông báo chia sẻ (" + count + ")"));
    }

    public void resetNotificationCount() {
        setNotificationCount(0);
    }

    public void updateFileList(List<String> rawFiles, boolean isSharedTab) {
        SwingUtilities.invokeLater(() -> {
            if (isSharedTab) {
                tableModel.setColumnIdentifiers(new String[]{"Name", "Date Modified", "Type", "Size", "Sender"});
            } else {
                tableModel.setColumnIdentifiers(new String[]{"Name", "Date Modified", "Type", "Size"});
            }

            tableModel.setRowCount(0);
            setupTableStyles(isSharedTab);

            Set<String> uniqueKeys = new HashSet<>();

            for (String raw : rawFiles) {
                if (raw == null || raw.trim().isEmpty()) continue;

                String trimmed = raw.trim();

                if (trimmed.startsWith("Người dùng '") || trimmed.contains("đã chia sẻ tệp")) {
                    continue;
                }

                ParsedFileInfo info = new ParsedFileInfo(raw, isSharedTab);

                if (info.name == null || info.name.trim().isEmpty()) continue;

                String uniqueKey = isSharedTab
                        ? (info.name.toLowerCase() + "::" + info.sender.toLowerCase())
                        : info.name.toLowerCase();

                if (uniqueKeys.contains(uniqueKey)) {
                    continue;
                }
                uniqueKeys.add(uniqueKey);

                if (isSharedTab) {
                    tableModel.addRow(new Object[]{info.name, info.date, info.type, info.size, info.sender});
                } else {
                    tableModel.addRow(new Object[]{info.name, info.date, info.type, info.size});
                }
            }
        });
    }

    private void setupTableStyles(boolean isSharedTab) {
        tableFiles.getColumnModel().getColumn(0).setPreferredWidth(260);
        tableFiles.getColumnModel().getColumn(1).setPreferredWidth(140);
        tableFiles.getColumnModel().getColumn(2).setPreferredWidth(100);
        tableFiles.getColumnModel().getColumn(3).setPreferredWidth(90);

        // Renderer cho cột canh Trái (Name)
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
                return this;
            }
        };

        // Renderer cho cột canh Giữa (Date Modified, Type)
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        // Renderer cho cột canh Phải (Size)
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20));
                return this;
            }
        };
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);

        tableFiles.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);
        tableFiles.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tableFiles.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tableFiles.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);

        if (isSharedTab && tableFiles.getColumnCount() > 4) {
            tableFiles.getColumnModel().getColumn(4).setPreferredWidth(110);
            tableFiles.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        }
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    public String getSelectedFile() {
        int row = tableFiles.getSelectedRow();
        if (row != -1) {
            return (String) tableModel.getValueAt(row, 0);
        }
        return null;
    }

    // --- PARSER TỆP CHUẨN HOÁ VÀ ĐỊNH DẠNG SIZE / DATE ---
    public static class ParsedFileInfo {
        public String name;
        public String date = "--";
        public String type = "--";
        public String size = "--";
        public String sender = "--";

        public ParsedFileInfo(String raw, boolean isSharedTab) {
            if (raw == null) return;
            String[] parts = raw.split("\\|", -1);

            if (parts.length >= 4) {
                this.name = cleanName(parts[0]);
                this.date = parts[1].trim().isEmpty() ? "--" : parts[1].trim();
                this.type = cleanType(parts[2], this.name);
                this.size = formatFileSize(parts[3]);

                if (isSharedTab && parts.length >= 5) {
                    this.sender = parts[4].trim().isEmpty() ? "N/A" : parts[4].trim();
                }
            } else {
                String str = parts[0].trim();
                String extractedSender = "N/A";

                if (str.startsWith("Tệp:")) {
                    str = str.substring(4).trim();
                }

                if (str.contains("(Được chia sẻ bởi")) {
                    int idx = str.indexOf("(Được chia sẻ bởi");
                    int endIdx = str.indexOf(")", idx);
                    if (endIdx > idx) {
                        extractedSender = str.substring(idx + 18, endIdx).trim();
                    }
                    str = str.substring(0, idx).trim();
                }

                this.name = str;
                this.date = (parts.length > 1 && !parts[1].trim().isEmpty()) ? parts[1].trim() : "--";
                this.type = cleanType((parts.length > 2) ? parts[2] : "", this.name);
                this.size = (parts.length > 3) ? formatFileSize(parts[3]) : "--";
                this.sender = (parts.length > 4 && !parts[4].trim().isEmpty()) ? parts[4].trim() : extractedSender;
            }
        }

        private static String formatFileSize(String rawSize) {
            if (rawSize == null || rawSize.trim().isEmpty() || rawSize.equals("--")) return "--";
            try {
                String upper = rawSize.toUpperCase().trim();
                if (upper.contains("B") || upper.contains("KB") || upper.contains("MB") || upper.contains("GB")) {
                    return rawSize.trim();
                }
                long bytes = Long.parseLong(rawSize.trim());
                if (bytes < 0) return "--";
                if (bytes < 1024) return bytes + " B";
                if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
                if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            } catch (NumberFormatException e) {
                return rawSize.trim();
            }
        }

        private String cleanName(String rawName) {
            String str = rawName.trim();
            if (str.startsWith("Tệp:")) str = str.substring(4).trim();
            if (str.contains("(Được chia sẻ bởi")) {
                str = str.substring(0, str.indexOf("(Được chia sẻ bởi")).trim();
            }
            return str;
        }

        private String cleanType(String rawType, String fileName) {
            String t = rawType.trim();
            if (t.contains("(")) {
                t = t.substring(0, t.indexOf("(")).trim();
            }
            if (t.isEmpty() || t.equalsIgnoreCase("--")) {
                if (!fileName.contains(".")) return "File folder";
                String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
                return ext + " File";
            }
            return t;
        }
    }

    // --- POP-UP THÔNG BÁO TỐI GIẢN ---
    public static class SharedNotificationDialog extends JDialog {
        public SharedNotificationDialog(Frame parent, List<String> rawNotifications) {
            super(parent, "Thông báo chia sẻ", true);
            setSize(480, 260); // Giảm chiều cao gọn gàng vì đã bỏ Header
            setLocationRelativeTo(parent);
            setLayout(new BorderLayout());

            DefaultListModel<ParsedFileInfo> listModel = new DefaultListModel<>();
            Set<String> uniqueKeys = new HashSet<>();

            for (String raw : rawNotifications) {
                if (raw == null || raw.trim().startsWith("Người dùng '")) continue;
                ParsedFileInfo info = new ParsedFileInfo(raw, true);
                String key = info.name.toLowerCase() + "::" + info.sender.toLowerCase();
                if (!uniqueKeys.contains(key)) {
                    uniqueKeys.add(key);
                    listModel.addElement(info);
                }
            }

            JList<ParsedFileInfo> list = new JList<>(listModel);
            list.setCellRenderer(new CleanMessageRenderer());
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JScrollPane scrollPane = new JScrollPane(list);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            add(scrollPane, BorderLayout.CENTER);

            JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
            footerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(230, 230, 230)));

            JButton btnClose = new JButton("Đóng");
            btnClose.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            btnClose.setPreferredSize(new Dimension(85, 28));
            btnClose.setFocusPainted(false);
            btnClose.addActionListener(e -> dispose());

            footerPanel.add(btnClose);
            add(footerPanel, BorderLayout.SOUTH);
        }

        private static class CleanMessageRenderer extends JPanel implements ListCellRenderer<ParsedFileInfo> {
            private JLabel lblSender = new JLabel();
            private JLabel lblMsg = new JLabel();

            public CleanMessageRenderer() {
                setLayout(new BorderLayout(15, 0));
                setBorder(new EmptyBorder(10, 15, 10, 15));

                lblSender.setFont(new Font("Segoe UI", Font.BOLD, 13));
                lblSender.setPreferredSize(new Dimension(110, 20));

                lblMsg.setFont(new Font("Segoe UI", Font.PLAIN, 13));

                add(lblSender, BorderLayout.WEST);
                add(lblMsg, BorderLayout.CENTER);
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends ParsedFileInfo> list, ParsedFileInfo value, int index, boolean isSelected, boolean cellHasFocus) {
                lblSender.setText(value.sender);
                lblMsg.setText("đã chia sẻ tệp \"" + value.name + "\" với bạn.");

                if (isSelected) {
                    setBackground(new Color(230, 240, 255));
                } else {
                    setBackground(index % 2 == 0 ? Color.WHITE : new Color(250, 250, 250));
                }
                return this;
            }
        }
    }

    // --- GETTERS ---
    public JButton getBtnUpload() { return btnUpload; }
    public JButton getBtnDownload() { return btnDownload; }
    public JButton getBtnMkdir() { return btnMkdir; }
    public JButton getBtnShare() { return btnShare; }
    public JButton getBtnDelete() { return btnDelete; }
    public JButton getBtnRefresh() { return btnRefresh; }
    public JButton getBtnExit() { return btnExit; }
    public JButton getBtnNotification() { return btnNotification; }
    public JTabbedPane getTabbedPane() { return tabbedPane; }

    public JMenuItem getMenuShare() { return menuShare; }
    public JMenuItem getMenuMkdir() { return menuMkdir; }
    public JMenuItem getMenuDownload() { return menuDownload; }
    public JMenuItem getMenuDelete() { return menuDelete; }
}