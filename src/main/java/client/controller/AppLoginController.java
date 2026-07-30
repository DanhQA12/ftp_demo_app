package client.controller;

import client.view.AppLoginView;
import client.view.FTPClientView;
import server.model.AuthModel;
import client.model.FTPClientModel;
import server.model.User;

import javax.swing.*;

public class AppLoginController {
    private final AuthModel authModel;
    private final AppLoginView loginView;

    public AppLoginController(AuthModel authModel, AppLoginView loginView) {
        this.authModel = authModel;
        this.loginView = loginView;

        // Bắt sự kiện các nút
        this.loginView.getBtnLogin().addActionListener(e -> handleLogin());
        this.loginView.getBtnAnonymous().addActionListener(e -> handleAnonymousLogin());
        this.loginView.getBtnSendOtp().addActionListener(e -> handleSendOtp());
        this.loginView.getBtnRegisterSubmit().addActionListener(e -> handleRegister());
    }

    private void handleLogin() {
        String username = loginView.getUsername();
        String password = loginView.getPassword();

        if (username.isEmpty() || password.isEmpty()) {
            loginView.showMessage("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User user = authModel.login(username, password);

        if (user != null) {
            loginView.showMessage("Đăng nhập thành công với tài khoản: " + username, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            openFTPClient(username, password);
        } else {
            loginView.showMessage("Sai tài khoản hoặc mật khẩu (hoặc tài khoản bị khóa)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleAnonymousLogin() {
        User anonUser = authModel.loginAnonymous();
        if (anonUser != null) {
            loginView.showMessage("Bạn đang truy cập dưới tư cách Khách Ẩn danh.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            openFTPClient("anonymous", "");
        } else {
            loginView.showMessage("Chế độ truy cập Ẩn danh hiện không khả dụng!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleSendOtp() {
        String email = loginView.getRegEmail();
        if (email.isEmpty() || !email.contains("@")) {
            loginView.showMessage("Vui lòng nhập Email hợp lệ để nhận OTP!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginView.getBtnSendOtp().setEnabled(false);
        loginView.getBtnSendOtp().setText("Đang gửi...");

        // Chạy trên Thread riêng để tránh đơ giao diện Swing khi gửi mail
        new Thread(() -> {
            boolean sent = authModel.sendOtp(email);
            SwingUtilities.invokeLater(() -> {
                loginView.getBtnSendOtp().setEnabled(true);
                loginView.getBtnSendOtp().setText("Gửi OTP");
                if (sent) {
                    loginView.showMessage("Mã OTP đã được gửi đến email " + email + ". Hãy kiểm tra hộp thư!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    loginView.showMessage("Gửi email thất bại! Hãy kiểm tra lại kết nối mạng hoặc cấu hình Gmail.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void handleRegister() {
        String regUser = loginView.getRegUsername();
        String regPass = loginView.getRegPassword();
        String regEmail = loginView.getRegEmail();
        String regOtp = loginView.getRegOtp();

        if (regUser.isEmpty() || regPass.isEmpty() || regEmail.isEmpty() || regOtp.isEmpty()) {
            loginView.showMessage("Vui lòng điền đầy đủ thông tin đăng ký và mã OTP!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean registered = authModel.registerWithOtp(regUser, regPass, regEmail, regOtp);
        if (registered) {
            loginView.showMessage("Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            loginView.showLoginCard();
        } else {
            loginView.showMessage("Mã OTP không đúng, đã hết hạn hoặc Tên đăng nhập/Email đã tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFTPClient(String username, String password) {
        loginView.dispose();
        FTPClientModel ftpModel = new FTPClientModel();
        FTPClientView ftpView = new FTPClientView(username);
        ftpView.setVisible(true);
        new FTPClientController(ftpModel, ftpView, username, password);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            AuthModel model = new AuthModel();
            AppLoginView view = new AppLoginView();
            new AppLoginController(model, view);
            view.setVisible(true);
        });
    }
}