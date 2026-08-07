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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MainClientView extends JFrame {
    // --- TOP BAR ---
    private JTextField txtHost, txtUsername, txtPort;
    private JPasswordField txtPassword;
    private JButton btnConnect, btnAnonymous, btnRegister, btnQuit;
    private JMenuItem mnuRemCut, mnuRemCopy, mnuRemPaste;
    private JProgressBar pbQuota;

    // --- LOCAL SITE ---
    private JTextField txtLocalPath;
    private JTree localTree;
    private JTable localTable;
    private JPanel localPanel;

    // Menu chuột phải Local
    private JPopupMenu localPopup;
    private JMenuItem mnuLocUpload, mnuLocOpen, mnuLocEdit, mnuLocMkdir, mnuLocMkdirEnter, mnuLocRefresh, mnuLocDelete, mnuLocRename;

    // --- REMOTE SITE ---
    private JTextField txtRemotePath;
    private JTree remoteTree;
    private JTable remoteTable;
    private JPanel remotePanel;

    // Menu chuột phải Remote
    private JPopupMenu remotePopup;
    private JMenuItem mnuRemDownload, mnuRemViewEdit, mnuRemMkdir, mnuRemMkdirEnter, mnuRemCreateFile, mnuRemRefresh, mnuRemDelete, mnuRemRename, mnuRemCopyUrl, mnuRemPerms, mnuRemShare;

    // --- LOG & STATUS ---
    private JTextArea txtLog;
    private JLabel lblStatus;

    public MainClientView() {
        setTitle("FTP Client");
        setSize(1150, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        initPopupMenus();

        setRemotePaneEnabled(false);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        txtHost = new JTextField("127.0.0.1", 15);
        txtUsername = new JTextField("", 10);
        txtPassword = new JPasswordField("", 10);
        txtPort = new JTextField("5000", 5);
        btnConnect = new JButton("Kết nối");
        btnAnonymous = new JButton("Ẩn danh");
        btnRegister = new JButton("Đăng ký");
        btnQuit = new JButton("Quit");
        btnQuit.setForeground(Color.RED);

        leftPanel.add(new JLabel("Host:")); leftPanel.add(txtHost);
        leftPanel.add(new JLabel("Username:")); leftPanel.add(txtUsername);
        leftPanel.add(new JLabel("Password:")); leftPanel.add(txtPassword);
        leftPanel.add(new JLabel("Port:")); leftPanel.add(txtPort);
        leftPanel.add(btnConnect); leftPanel.add(btnAnonymous); leftPanel.add(btnRegister);
        leftPanel.add(btnQuit);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        pbQuota = new JProgressBar(0, 100);
        pbQuota.setStringPainted(true);
        pbQuota.setPreferredSize(new Dimension(200, 25)); // To và rõ ràng hơn
        pbQuota.setString("0 MB / 0 MB");
        rightPanel.add(new JLabel("Lưu trữ:"));
        rightPanel.add(pbQuota);

        topBar.add(leftPanel, BorderLayout.CENTER);
        topBar.add(rightPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

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
        for (File root : File.listRoots()) {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new LocalFileNodeInfo(root));
            rootNode.add(new DefaultMutableTreeNode("Đang tải..."));
            localRoot.add(rootNode);
        }

        localTree = new JTree(localRoot);
        localTree.setRootVisible(false);
        localTree.setShowsRootHandles(true);
        styleTree(localTree);

        String[] locCols = {"Name", "Size", "Type", "Last modified"};
        localTable = new JTable(new DefaultTableModel(locCols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        styleTable(localTable);

        localTable.setDragEnabled(true);
        localTable.setDropMode(DropMode.ON_OR_INSERT_ROWS);
        localTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JSplitPane localSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(localTree), new JScrollPane(localTable));
        localSplit.setResizeWeight(0.5);
        localPanel.add(localSplit, BorderLayout.CENTER);

        remotePanel = new JPanel(new BorderLayout(5, 5));
        remotePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel remotePathPanel = new JPanel(new BorderLayout(5, 0));
        remotePathPanel.add(new JLabel("Remote site:"), BorderLayout.WEST);
        txtRemotePath = new JTextField("/");
        txtRemotePath.setEditable(false);
        remotePathPanel.add(txtRemotePath, BorderLayout.CENTER);
        remotePanel.add(remotePathPanel, BorderLayout.NORTH);

        remoteTree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode("Not Connected")));
        styleTree(remoteTree);

        String[] remCols = {"Name", "Size", "Type", "Last modified", "Permissions", "Owner"};
        remoteTable = new JTable(new DefaultTableModel(remCols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        styleTable(remoteTable);

        remoteTable.setDragEnabled(true);
        remoteTable.setDropMode(DropMode.ON_OR_INSERT_ROWS);
        remoteTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JSplitPane remoteSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(remoteTree), new JScrollPane(remoteTable));
        remoteSplit.setResizeWeight(0.5);
        remotePanel.add(remoteSplit, BorderLayout.CENTER);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
        mainSplitPane.setResizeWeight(0.5);
        mainSplitPane.setDividerLocation(560);
        add(mainSplitPane, BorderLayout.CENTER);

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

    private void initPopupMenus() {
        // --- MENU LOCAL ---
        localPopup = new JPopupMenu();
        mnuLocUpload = new JMenuItem("Upload");
        mnuLocOpen = new JMenuItem("Open");
        mnuLocEdit = new JMenuItem("Edit");
        mnuLocMkdir = new JMenuItem("Tạo Folder");
        mnuLocMkdirEnter = new JMenuItem("Tạo Folder và vào trong");
        mnuLocRefresh = new JMenuItem("Làm mới");
        mnuLocDelete = new JMenuItem("Delete");
        mnuLocRename = new JMenuItem("Rename");

        localPopup.add(mnuLocUpload);
        localPopup.addSeparator();
        localPopup.add(mnuLocOpen);
        localPopup.add(mnuLocEdit);
        localPopup.addSeparator();
        localPopup.add(mnuLocMkdir);
        localPopup.add(mnuLocMkdirEnter);
        localPopup.add(mnuLocRefresh);
        localPopup.addSeparator();
        localPopup.add(mnuLocDelete);
        localPopup.add(mnuLocRename);

        localTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { showPopup(e, localTable, localPopup); }
            @Override public void mousePressed(MouseEvent e) { showPopup(e, localTable, localPopup); }
        });

        // --- MENU REMOTE ---
        remotePopup = new JPopupMenu();
        mnuRemDownload = new JMenuItem("Download");
        mnuRemViewEdit = new JMenuItem("Edit");
        mnuRemCut = new JMenuItem("Cut");
        mnuRemCopy = new JMenuItem("Copy");
        mnuRemPaste = new JMenuItem("Paste");
        mnuRemMkdir = new JMenuItem("Tạo Folder");
        mnuRemMkdirEnter = new JMenuItem("Tạo Folder và vào trong");
        mnuRemCreateFile = new JMenuItem("Tạo File");
        mnuRemRefresh = new JMenuItem("Làm mới");
        mnuRemDelete = new JMenuItem("Delete");
        mnuRemRename = new JMenuItem("Rename");
        mnuRemCopyUrl = new JMenuItem("Copy URL(s) to clipboard");
        mnuRemPerms = new JMenuItem("File permissions...");
        mnuRemShare = new JMenuItem("Share...");

        remotePopup.add(mnuRemDownload);
        remotePopup.add(mnuRemViewEdit);
        remotePopup.addSeparator();
        remotePopup.add(mnuRemCut);
        remotePopup.add(mnuRemCopy);
        remotePopup.add(mnuRemPaste);
        remotePopup.add(mnuRemMkdir);
        remotePopup.add(mnuRemMkdirEnter);
        remotePopup.add(mnuRemCreateFile);
        remotePopup.add(mnuRemRefresh);
        remotePopup.addSeparator();
        remotePopup.add(mnuRemDelete);
        remotePopup.add(mnuRemRename);
        remotePopup.add(mnuRemCopyUrl);
        remotePopup.add(mnuRemPerms);
        remotePopup.add(mnuRemShare);

        remoteTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { showPopup(e, remoteTable, remotePopup); }
            @Override public void mousePressed(MouseEvent e) { showPopup(e, remoteTable, remotePopup); }
        });
    }

    private void showPopup(MouseEvent e, JTable table, JPopupMenu popup) {
        if (e.isPopupTrigger()) {
            int row = table.rowAtPoint(e.getPoint());

            // Nếu click vào vùng trống, bỏ chọn tất cả các dòng hiện tại
            if (row < 0) {
                table.clearSelection();
                popup.show(e.getComponent(), e.getX(), e.getY());
                return;
            }

            // Nếu click vào dòng có sẵn
            if (!table.isRowSelected(row)) {
                table.setRowSelectionInterval(row, row);
            }

            String name = (String) table.getValueAt(row, 0);
            // Cho phép hiển thị menu nếu không phải dòng lệnh quay lại ".."
            if (!"..".equals(name)) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    private void styleTable(JTable table) {
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(false);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(22);

        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return this;
            }
        };

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 15));
                return this;
            }
        };
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);

        table.getColumnModel().getColumn(0).setCellRenderer(new IconTableCellRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(defaultRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(defaultRenderer);

        if (table.getColumnCount() > 4) {
            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
            table.getColumnModel().getColumn(5).setCellRenderer(defaultRenderer);

            table.getColumnModel().getColumn(0).setPreferredWidth(180);
            table.getColumnModel().getColumn(4).setPreferredWidth(70);
            table.getColumnModel().getColumn(5).setPreferredWidth(80);
        }
    }

    class IconTableCellRenderer extends DefaultTableCellRenderer {
        private final FileSystemView fsv = FileSystemView.getFileSystemView();
        private final Map<String, Icon> iconCache = new HashMap<>();
        private final Icon folderIcon = UIManager.getIcon("Tree.closedIcon");
        private final Icon defaultFileIcon = UIManager.getIcon("FileView.fileIcon");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value != null) {
                String name = value.toString();
                if ("..".equals(name)) {
                    label.setIcon(folderIcon);
                    label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                    return label;
                }

                String type = "";
                if (table.getColumnCount() > 2) {
                    Object typeObj = table.getValueAt(row, 2);
                    if (typeObj != null) type = typeObj.toString();
                }

                boolean isDir = type.equalsIgnoreCase("File folder") || type.toLowerCase().contains("thư mục") || "d".equals(type);
                label.setIcon(getIconFor(name, isDir));
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            }
            return label;
        }

        private Icon getIconFor(String name, boolean isDirectory) {
            if (isDirectory) return folderIcon;
            String ext = "";
            int i = name.lastIndexOf('.');
            if (i > 0 && i < name.length() - 1) ext = name.substring(i);
            if (iconCache.containsKey(ext)) return iconCache.get(ext);
            if (!ext.isEmpty()) {
                try {
                    File temp = File.createTempFile("icon_temp", ext);
                    Icon nativeIcon = fsv.getSystemIcon(temp);
                    temp.delete();
                    if (nativeIcon != null) {
                        iconCache.put(ext, nativeIcon);
                        return nativeIcon;
                    }
                } catch (Exception ignored) {}
            }
            iconCache.put(ext, defaultFileIcon);
            return defaultFileIcon;
        }
    }

    private void styleTree(JTree tree) {
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
        renderer.setLeafIcon(renderer.getClosedIcon());
    }

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

    // --- GETTERS ---
    public JButton getBtnConnect() { return btnConnect; }
    public JButton getBtnAnonymous() { return btnAnonymous; }
    public JButton getBtnRegister() { return btnRegister; }
    public JButton getBtnQuit() { return btnQuit; }
    public JTextField getTxtHost() { return txtHost; }
    public JTextField getTxtUsername() { return txtUsername; }
    public JPasswordField getTxtPassword() { return txtPassword; }
    public JTextField getTxtPort() { return txtPort; }
    public JTextField getTxtLocalPath() { return txtLocalPath; }
    public JTree getLocalTree() { return localTree; }
    public JTable getLocalTable() { return localTable; }
    public JTextField getTxtRemotePath() { return txtRemotePath; }
    public JTree getRemoteTree() { return remoteTree; }
    public JTable getRemoteTable() { return remoteTable; }

    public JMenuItem getMnuLocUpload() { return mnuLocUpload; }
    public JMenuItem getMnuLocOpen() { return mnuLocOpen; }
    public JMenuItem getMnuLocEdit() { return mnuLocEdit; }
    public JMenuItem getMnuLocMkdir() { return mnuLocMkdir; }
    public JMenuItem getMnuLocMkdirEnter() { return mnuLocMkdirEnter; }
    public JMenuItem getMnuLocRefresh() { return mnuLocRefresh; }
    public JMenuItem getMnuLocDelete() { return mnuLocDelete; }
    public JMenuItem getMnuLocRename() { return mnuLocRename; }

    public JMenuItem getMnuRemDownload() { return mnuRemDownload; }
    public JMenuItem getMnuRemViewEdit() { return mnuRemViewEdit; }
    public JMenuItem getMnuRemCut() { return mnuRemCut; }
    public JMenuItem getMnuRemCopy() { return mnuRemCopy; }
    public JMenuItem getMnuRemPaste() { return mnuRemPaste; }
    public JMenuItem getMnuRemMkdir() { return mnuRemMkdir; }
    public JMenuItem getMnuRemMkdirEnter() { return mnuRemMkdirEnter; }
    public JMenuItem getMnuRemCreateFile() { return mnuRemCreateFile; }
    public JMenuItem getMnuRemRefresh() { return mnuRemRefresh; }
    public JMenuItem getMnuRemDelete() { return mnuRemDelete; }
    public JMenuItem getMnuRemRename() { return mnuRemRename; }
    public JMenuItem getMnuRemCopyUrl() { return mnuRemCopyUrl; }
    public JMenuItem getMnuRemPerms() { return mnuRemPerms; }
    public JMenuItem getMnuRemShare() { return mnuRemShare; }
    public JProgressBar getPbQuota() { return pbQuota; }

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
        public LocalFileNodeInfo(File file) { this.file = file; }
        @Override public String toString() {
            String name = fsv.getSystemDisplayName(file);
            if (name == null || name.isEmpty()) name = file.getName();
            return name.isEmpty() ? file.getPath() : name;
        }
    }
}