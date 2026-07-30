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
import java.util.List;

public class FTPClientView extends JFrame {
    private JLabel lblWelcome;
    private JTable tableFiles;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JButton btnUpload, btnDownload, btnDelete, btnRefresh, btnExit;

    private JButton btnMkdir, btnShare;
    private JPopupMenu popupMenu;
    private JMenuItem menuShare, menuMkdir, menuDownload, menuDelete;

    public FTPClientView(String username) {
        setTitle("Ứng dụng Quản lý File FTP - Phía Client");
        setSize(920, 680);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // --- 1. HEADER ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(15, 15, 0, 15));

        lblWelcome = new JLabel("Xin chào, " + username);
        lblWelcome.setFont(new Font("Arial", Font.BOLD, 18));
        headerPanel.add(lblWelcome, BorderLayout.WEST);

        JButton btnNotif = new JButton("Thông báo chia sẻ (0)");
        btnNotif.setFocusPainted(false);
        btnNotif.setPreferredSize(new Dimension(170, 32));
        headerPanel.add(btnNotif, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // --- 2. TRUNG TÂM (DANH SÁCH FILE + NHẬT KÝ) ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(new EmptyBorder(0, 15, 15, 0));

        String[] columnNames = {"Tên File / Thư mục"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableFiles = new JTable(tableModel);
        tableFiles.setRowHeight(28);
        tableFiles.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(JLabel.LEFT);
        tableFiles.getColumnModel().getColumn(0).setCellRenderer(leftRenderer);

        // --- KHỞI TẠO POPUP MENU CHUỘT PHẢI ---
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

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabbedPane.addTab("Thư mục Cá nhân", new JScrollPane(tableFiles));
        tabbedPane.addTab("Được chia sẻ với tôi", new JPanel());
        tabbedPane.addTab("Thư mục Chung", new JPanel());

        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        // Log Area
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
        btnMkdir = createActionButton("Tạo thư mục");     // <-- NÚT MỚI
        btnShare = createActionButton("Chia sẻ...");      // <-- NÚT MỚI
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

    private void initPopupMenu() {
        popupMenu = new JPopupMenu();
        menuShare = new JMenuItem("🔗 Chia sẻ tệp/thư mục này...");
        menuMkdir = new JMenuItem("📁 Tạo thư mục mới");
        menuDownload = new JMenuItem("⬇ Tải xuống");
        menuDelete = new JMenuItem("❌ Xóa");

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

    public void updateFileList(List<String> files) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (String file : files) {
                tableModel.addRow(new Object[]{file});
            }
        });
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

    // --- GETTERS DÙNG TRONG CONTROLLER ---
    public JButton getBtnUpload() { return btnUpload; }
    public JButton getBtnDownload() { return btnDownload; }
    public JButton getBtnMkdir() { return btnMkdir; }
    public JButton getBtnShare() { return btnShare; }
    public JButton getBtnDelete() { return btnDelete; }
    public JButton getBtnRefresh() { return btnRefresh; }
    public JButton getBtnExit() { return btnExit; }

    public JMenuItem getMenuShare() { return menuShare; }
    public JMenuItem getMenuMkdir() { return menuMkdir; }
    public JMenuItem getMenuDownload() { return menuDownload; }
    public JMenuItem getMenuDelete() { return menuDelete; }
}