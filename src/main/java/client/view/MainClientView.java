package client.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;

public class MainClientView extends JFrame {
    // --- TOP BAR (Quickconnect) ---
    private JTextField txtHost, txtUsername, txtPort;
    private JPasswordField txtPassword;
    private JButton btnConnect, btnAnonymous, btnRegister;

    // --- LOCAL SITE (Bên trái) ---
    private JTextField txtLocalPath;
    private JTree localTree;
    private JTable localTable;
    private JPanel localPanel;

    // --- REMOTE SITE (Bên phải) ---
    private JTextField txtRemotePath;
    private JTree remoteTree;
    private JTable remoteTable;
    private JPanel remotePanel;

    // --- LOG & STATUS ---
    private JTextArea txtLog;
    private JLabel lblStatus;

    public MainClientView() {
        setTitle("FTP Client - Dual Pane Layout");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();

        // Mặc định làm mờ bên Remote khi chưa kết nối
        setRemotePaneEnabled(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // 1. THANH CÔNG CỤ TRÊN CÙNG (Quickconnect)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        // Đã xóa giá trị admin mặc định đi theo yêu cầu
        txtHost = new JTextField("127.0.0.1", 15);
        txtUsername = new JTextField("", 10);
        txtPassword = new JPasswordField("", 10);
        txtPort = new JTextField("5000", 5);
        btnConnect = new JButton("Quickconnect");
        btnAnonymous = new JButton("Ẩn danh");
        btnRegister = new JButton("Đăng ký");

        topBar.add(new JLabel("Host:"));
        topBar.add(txtHost);
        topBar.add(new JLabel("Username:"));
        topBar.add(txtUsername);
        topBar.add(new JLabel("Password:"));
        topBar.add(txtPassword);
        topBar.add(new JLabel("Port:"));
        topBar.add(txtPort);
        topBar.add(btnConnect);
        topBar.add(btnAnonymous);
        topBar.add(btnRegister);

        add(topBar, BorderLayout.NORTH);

        // 2. KHU VỰC TRUNG TÂM (Local vs Remote)
        // 2.1 LOCAL SITE (Trái)
        localPanel = new JPanel(new BorderLayout(5, 5));
        localPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel localPathPanel = new JPanel(new BorderLayout(5, 0));
        localPathPanel.add(new JLabel("Local site:"), BorderLayout.WEST);
        txtLocalPath = new JTextField("C:\\");
        txtLocalPath.setEditable(false);
        localPathPanel.add(txtLocalPath, BorderLayout.CENTER);
        localPanel.add(localPathPanel, BorderLayout.NORTH);

        DefaultMutableTreeNode localRoot = new DefaultMutableTreeNode("My Computer");
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File[] roots = File.listRoots();

        for (File root : roots) {
            LocalFileNodeInfo nodeInfo = new LocalFileNodeInfo(root);
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(nodeInfo);
            rootNode.add(new DefaultMutableTreeNode("Đang tải..."));
            localRoot.add(rootNode);
        }

        localTree = new JTree(localRoot);
        localTree.setRootVisible(false);
        localTree.setShowsRootHandles(true);
        styleTree(localTree); // Sửa lỗi cục đen đen cho cây Local

        // Khởi tạo bảng với JTable
        String[] columns = {"Name", "Size", "Type", "Last modified"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        localTable = new JTable(tableModel);
        styleTable(localTable); // Xóa ô kẻ, căn phải cột Size

        JSplitPane localSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(localTree), new JScrollPane(localTable));
        localSplit.setResizeWeight(0.5);
        localPanel.add(localSplit, BorderLayout.CENTER);

        // 2.2 REMOTE SITE (Phải)
        remotePanel = new JPanel(new BorderLayout(5, 5));
        remotePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel remotePathPanel = new JPanel(new BorderLayout(5, 0));
        remotePathPanel.add(new JLabel("Remote site:"), BorderLayout.WEST);
        txtRemotePath = new JTextField("/");
        txtRemotePath.setEditable(false);
        remotePathPanel.add(txtRemotePath, BorderLayout.CENTER);
        remotePanel.add(remotePathPanel, BorderLayout.NORTH);

        remoteTree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("Not Connected")));
        styleTree(remoteTree); // Sửa lỗi cục đen đen cho cây Remote

        remoteTable = new JTable(new DefaultTableModel(new Object[]{"Name", "Size", "Type", "Last modified"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        });
        styleTable(remoteTable); // Xóa ô kẻ, căn phải cột Size cho bảng Remote

        JSplitPane remoteSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(remoteTree), new JScrollPane(remoteTable));
        remoteSplit.setResizeWeight(0.5);
        remotePanel.add(remoteSplit, BorderLayout.CENTER);

        // 2.3 GỘP LOCAL VÀ REMOTE BẰNG TRỤC DỌC (HORIZONTAL SPLIT)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
        mainSplitPane.setResizeWeight(0.5);
        mainSplitPane.setDividerLocation(530);
        add(mainSplitPane, BorderLayout.CENTER);

        // 3. KHU VỰC LOG & TRẠNG THÁI (Dưới cùng)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        txtLog = new JTextArea(6, 50);
        txtLog.setEditable(false);
        JScrollPane logScroll = new JScrollPane(txtLog);
        logScroll.setBorder(new TitledBorder("Nhật ký hệ thống"));

        lblStatus = new JLabel(" Trạng thái: Sẵn sàng kết nối");
        lblStatus.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        bottomPanel.add(logScroll, BorderLayout.CENTER);
        bottomPanel.add(lblStatus, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- HÀM HỖ TRỢ TRANG TRÍ (UI STYLING) ---

    // Hàm trang trí cho JTable (Bỏ ô kẻ, căn lề phải cột Size)
    private void styleTable(JTable table) {
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 0));

        // Tạo Renderer căn phải và tạo khoảng trống (padding)
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        rightRenderer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));

        table.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
    }

    // Hàm trang trí cho JTree (Bỏ icon lá/chấm đen, thay bằng icon thư mục mặc định)
    private void styleTree(JTree tree) {
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
        // Ép các node không có con (leaf node) dùng chung icon thư mục đóng
        renderer.setLeafIcon(renderer.getClosedIcon());
    }

    // ----------------------------------------

    public void setRemotePaneEnabled(boolean enabled) {
        txtRemotePath.setEnabled(enabled);
        remoteTree.setEnabled(enabled);
        remoteTable.setEnabled(enabled);

        if (!enabled) {
            lblStatus.setText(" Trạng thái: Chưa kết nối");
            remoteTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Not Connected")));
            ((DefaultTableModel)remoteTable.getModel()).setRowCount(0);
        } else {
            lblStatus.setText(" Trạng thái: Đã kết nối tới Server");
        }
    }

    // --- GETTERS DÀNH CHO CONTROLLER ---
    public JButton getBtnConnect() { return btnConnect; }
    public JButton getBtnAnonymous() { return btnAnonymous; }
    public JButton getBtnRegister() { return btnRegister; }
    public JTextField getTxtHost() { return txtHost; }
    public JTextField getTxtUsername() { return txtUsername; }
    public JPasswordField getTxtPassword() { return txtPassword; }
    public JTextField getTxtPort() { return txtPort; }

    public JTree getLocalTree() { return localTree; }
    public JTable getLocalTable() { return localTable; }
    public JTextField getTxtLocalPath() { return txtLocalPath; }

    public JTree getRemoteTree() { return remoteTree; }
    public JTable getRemoteTable() { return remoteTable; }
    public JTextField getTxtRemotePath() { return txtRemotePath; }

    public JLabel getLblStatus() { return lblStatus; }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLog.append(message + "\n");
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    public static class LocalFileNodeInfo {
        public File file;
        public boolean isLoaded = false;
        private FileSystemView fsv = FileSystemView.getFileSystemView();

        public LocalFileNodeInfo(File file) {
            this.file = file;
        }

        @Override
        public String toString() {
            String name = fsv.getSystemDisplayName(file);
            if (name == null || name.isEmpty()) {
                name = file.getName();
            }
            return name.isEmpty() ? file.getPath() : name;
        }
    }
}