package client.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AppLoginView extends JFrame {
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    private JTextField txtUser, txtRegUser, txtRegEmail, txtRegOtp;
    private JPasswordField txtPass, txtRegPass;
    private JButton btnLogin, btnAnonymous, btnSendOtp, btnRegisterSubmit;

    public AppLoginView() {
        setTitle("Hệ thống Đăng nhập FTP");
        setSize(420, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createRegisterPanel(), "REGISTER");

        add(mainPanel);
    }

    private JPanel createLoginPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(30, 40, 30, 40));

        JLabel title = new JLabel("ĐĂNG NHẬP", JLabel.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Arial", Font.BOLD, 20));

        txtUser = createStyledTextField("Tên đăng nhập");
        txtPass = createStyledPasswordField("Mật khẩu");

        btnLogin = new JButton("Đăng nhập");
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(300, 40));

        btnAnonymous = new JButton("Truy cập Ẩn danh");
        btnAnonymous.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnAnonymous.setMaximumSize(new Dimension(300, 40));
        btnAnonymous.setBackground(new Color(230, 230, 230));

        JButton btnGoRegister = new JButton("Chưa có tài khoản? Đăng ký");
        btnGoRegister.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnGoRegister.setMaximumSize(new Dimension(300, 40));
        btnGoRegister.addActionListener(e -> cardLayout.show(mainPanel, "REGISTER"));

        p.add(title); p.add(Box.createRigidArea(new Dimension(0, 20)));
        p.add(txtUser); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(txtPass); p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(btnLogin); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(btnAnonymous); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(btnGoRegister);

        return p;
    }

    private JPanel createRegisterPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(20, 40, 20, 40));

        JLabel title = new JLabel("ĐĂNG KÝ TÀI KHOẢN", JLabel.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(new Font("Arial", Font.BOLD, 18));

        txtRegUser = createStyledTextField("Tên đăng nhập");
        txtRegPass = createStyledPasswordField("Mật khẩu");
        txtRegEmail = createStyledTextField("Email");

        // Panel kết hợp ô gõ OTP và Nút Gửi
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

        JButton btnBack = new JButton("Quay lại Đăng nhập");
        btnBack.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnBack.setMaximumSize(new Dimension(300, 40));
        btnBack.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        p.add(title); p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(txtRegUser); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(txtRegPass); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(txtRegEmail); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(otpPanel); p.add(Box.createRigidArea(new Dimension(0, 15)));
        p.add(btnRegisterSubmit); p.add(Box.createRigidArea(new Dimension(0, 10)));
        p.add(btnBack);

        return p;
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

    public void showMessage(String msg, String title, int type) {
        JOptionPane.showMessageDialog(this, msg, title, type);
    }

    public void showLoginCard() { cardLayout.show(mainPanel, "LOGIN"); }
    public String getUsername() { return txtUser.getText().trim(); }
    public String getPassword() { return new String(txtPass.getPassword()); }
    public String getRegUsername() { return txtRegUser.getText().trim(); }
    public String getRegPassword() { return new String(txtRegPass.getPassword()); }
    public String getRegEmail() { return txtRegEmail.getText().trim(); }
    public String getRegOtp() { return txtRegOtp.getText().trim(); }

    public JButton getBtnLogin() { return btnLogin; }
    public JButton getBtnAnonymous() { return btnAnonymous; }
    public JButton getBtnSendOtp() { return btnSendOtp; }
    public JButton getBtnRegisterSubmit() { return btnRegisterSubmit; }
}