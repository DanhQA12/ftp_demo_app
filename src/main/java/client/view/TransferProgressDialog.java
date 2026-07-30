package client.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TransferProgressDialog extends JDialog {
    private JLabel lblStatus;
    private JProgressBar progressBar;
    private JButton btnPauseResume;
    private JButton btnCancel;

    private boolean paused = false;
    private boolean cancelled = false;

    public TransferProgressDialog(Frame owner, String title, String fileName) {
        super(owner, title, false); // false: non-modal để không làm đóng băng cửa sổ chính
        setSize(400, 170);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Thông tin file đang xử lý
        lblStatus = new JLabel("Đang xử lý tệp: " + fileName, JLabel.LEFT);
        lblStatus.setFont(new Font("Arial", Font.PLAIN, 12));
        mainPanel.add(lblStatus, BorderLayout.NORTH);

        // Thanh tiến trình
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(350, 25));
        mainPanel.add(progressBar, BorderLayout.CENTER);

        // Bảng nút điều khiển
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        btnPauseResume = new JButton("Tạm dừng");
        btnPauseResume.setPreferredSize(new Dimension(100, 30));

        btnCancel = new JButton("Hủy");
        btnCancel.setPreferredSize(new Dimension(80, 30));

        btnPauseResume.addActionListener(e -> {
            paused = !paused;
            if (paused) {
                btnPauseResume.setText("Tiếp tục");
                lblStatus.setText("Đã tạm dừng: " + fileName);
            } else {
                btnPauseResume.setText("Tạm dừng");
                lblStatus.setText("Đang xử lý tệp: " + fileName);
            }
        });

        btnCancel.addActionListener(e -> {
            cancelled = true;
            dispose();
        });

        buttonPanel.add(btnPauseResume);
        buttonPanel.add(btnCancel);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    public void updateProgress(long current, long total) {
        if (total <= 0) return;
        int percent = (int) ((current * 100) / total);
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            progressBar.setString(percent + "% (" + (current / 1024) + " KB / " + (total / 1024) + " KB)");
        });
    }

    public boolean isPaused() { return paused; }
    public boolean isCancelled() { return cancelled; }
}