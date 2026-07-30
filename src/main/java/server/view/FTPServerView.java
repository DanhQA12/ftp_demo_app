package server.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class FTPServerView extends JFrame {
    private JTextArea logArea;
    private JButton startButton, stopButton, exitButton;

    public FTPServerView() {
        setTitle("FTP - Server");
        setSize(600, 450);
        setMinimumSize(new Dimension(450, 300));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(10, 10));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane logScrollPane = new JScrollPane(logArea);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
        centerPanel.add(logScrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        startButton = new JButton("Bật Server");
        startButton.setPreferredSize(new Dimension(120, 35));

        stopButton = new JButton("Tắt Server");
        stopButton.setPreferredSize(new Dimension(120, 35));
        stopButton.setEnabled(false);

        exitButton = new JButton("Thoát");
        exitButton.setPreferredSize(new Dimension(100, 35));

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(exitButton);

        add(buttonPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Tự động cuộn con trỏ thanh Scrollbar xuống dòng log mới nhất
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public JButton getStartButton() { return startButton; }
    public JButton getStopButton() { return stopButton; }
    public JButton getExitButton() { return exitButton; }
}