package server.model;

import server.dao.UserDAO;
import server.util.EmailUtil;

import java.util.Random;

public class AuthModel {
    private final UserDAO userDAO;

    public AuthModel() {
        this.userDAO = new UserDAO();
    }

    public User login(String username, String password) {
        return userDAO.authenticate(username, password);
    }

    public User loginAnonymous() {
        return userDAO.getAnonymousUser();
    }

    public void initDefaultAccounts() {
        userDAO.ensureAnonymousUserExists();
    }

    // --- BỔ SUNG HÀM NÀY ĐỂ HẾT BÁO ĐỎ TRONG FTPServerModel ---
    public boolean register(String username, String password, String email) {
        return userDAO.register(username, password, email);
    }

    // 1. Tạo & Gửi mã OTP qua Gmail
    public boolean sendOtp(String email) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return false;
        }
        // Tạo ngẫu nhiên OTP 6 chữ số
        String otpCode = String.format("%06d", new Random().nextInt(900000) + 100000);

        if (userDAO.saveOtp(email, otpCode)) {
            return EmailUtil.sendOtpEmail(email, otpCode);
        }
        return false;
    }

    // 2. Xác nhận OTP và Đăng ký tài khoản
    public boolean registerWithOtp(String username, String password, String email, String otpCode) {
        if (!userDAO.verifyOtp(email, otpCode)) {
            return false; // Mã OTP không đúng hoặc đã hết hạn
        }
        return userDAO.register(username, password, email);
    }
}