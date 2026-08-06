package client.view;

import server.model.AuthModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RegisterView extends JDialog {
    private final MainClientView parentView;
    private final AuthModel authModel;

    private JTextField txtRegUser, txtRegEmail, txtRegOtp;
    private JPasswordField txtRegPass;
    private JButton btnSendOtp, btnRegisterSubmit, btnBack;

    public RegisterView(MainClientView parentView) {
        super(parentView, "Đăng ký tài khoản FTP", true);
        this.parentView = parentView;
        this.authModel = new AuthModel();

        setSize(400, 480);
        setLocationRelativeTo(parentView);
        initComponents();
        initEvents();
    }

    private void initComponents() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(20, 40, 20, 40));

        JLabel title = new JLabel("ĐĂNG KÝ TÀI KHOẢN", JLabel.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        txtRegUser = createStyledTextField("Tên đăng nhập");
        txtRegPass = createStyledPasswordField("Mật khẩu");
        txtRegEmail = createStyledTextField("Email");

        JPanel otpPanel = new JPanel(new BorderLayout(5, 0));
        otpPanel.setMaximumSize(new Dimension(300, 45));
        txtRegOtp = createStyledTextField("Mã OTP (6 số)");
        btnSendOtp = new JButton("Gửi OTP");
        btnSendOtp.setPreferredSize(new Dimension(90, 40));
        otpPanel.add(txtRegOtp, BorderLayout.CENTER);
        otpPanel.add(btnSendOtp, BorderLayout.EAST);

        btnRegisterSubmit = new JButton("Hoàn tất Đăng ký");
        btnRegisterSubmit.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnRegisterSubmit.setMaximumSize(new Dimension(300, 40));

        btnBack = new JButton("Hủy");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setMaximumSize(new Dimension(300, 40));

        p.add(title); p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(txtRegUser); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(txtRegPass); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(txtRegEmail); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(otpPanel); p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(btnRegisterSubmit); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(btnBack);

        add(p);
    }

    private void initEvents() {
        btnBack.addActionListener(e -> dispose());

        btnSendOtp.addActionListener(e -> {
            String email = txtRegEmail.getText().trim();
            if (email.isEmpty() || !email.contains("@")) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập Email hợp lệ để nhận OTP!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            btnSendOtp.setEnabled(false);
            btnSendOtp.setText("Đang gửi...");

            new Thread(() -> {
                boolean sent = authModel.sendOtp(email);
                SwingUtilities.invokeLater(() -> {
                    btnSendOtp.setEnabled(true);
                    btnSendOtp.setText("Gửi OTP");
                    if (sent) {
                        JOptionPane.showMessageDialog(this, "Mã OTP đã được gửi đến email " + email + ". Hãy kiểm tra hộp thư!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, "Gửi email thất bại! Hãy kiểm tra lại kết nối mạng hoặc cấu hình Gmail.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }).start();
        });

        btnRegisterSubmit.addActionListener(e -> {
            String regUser = txtRegUser.getText().trim();
            String regPass = new String(txtRegPass.getPassword());
            String regEmail = txtRegEmail.getText().trim();
            String regOtp = txtRegOtp.getText().trim();

            if (regUser.isEmpty() || regPass.isEmpty() || regEmail.isEmpty() || regOtp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng điền đầy đủ thông tin đăng ký và mã OTP!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean registered = authModel.registerWithOtp(regUser, regPass, regEmail, regOtp);
            if (registered) {
                JOptionPane.showMessageDialog(this, "Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                // Tự động điền dữ liệu vào form của giao diện chính
                parentView.getTxtUsername().setText(regUser);
                parentView.getTxtPassword().setText(regPass);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Mã OTP không đúng, đã hết hạn hoặc Tên đăng nhập/Email đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private JTextField createStyledTextField(String title) {
        JTextField tf = new JTextField();
        tf.setMaximumSize(new Dimension(300, 40));
        tf.setBorder(BorderFactory.createTitledBorder(title));
        return tf;
    }

    private JPasswordField createStyledPasswordField(String title) {
        JPasswordField pf = new JPasswordField();
        pf.setMaximumSize(new Dimension(300, 40));
        pf.setBorder(BorderFactory.createTitledBorder(title));
        return pf;
    }
}