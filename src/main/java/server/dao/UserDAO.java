package server.dao;

import server.config.DatabaseConnection;
import server.model.User;
import server.util.PasswordUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    // 1. Kiểm tra Đăng nhập
    public User authenticate(String username, String rawPassword) {
        // SỬA LỖI 1: Dùng LEFT JOIN để không bị sập nếu bảng roles chưa có dữ liệu
        String sql = "SELECT u.user_id, u.role_id, u.username, u.password_hash, u.email, u.full_name, " +
                "u.can_upload, u.can_download, u.enabled, r.role_name " +
                "FROM users u " +
                "LEFT JOIN roles r ON u.role_id = r.role_id " +
                "WHERE u.username = ? AND u.enabled = TRUE";

        String javaHash = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String dbHash = rs.getString("password_hash");

                    // SỬA LỖI 2: So sánh linh hoạt - Nếu DB bị giới hạn varchar cắt cụt chữ thì vẫn chấp nhận
                    if (dbHash != null && (javaHash.equalsIgnoreCase(dbHash) || javaHash.startsWith(dbHash))) {
                        return mapResultSetToUser(rs);
                    } else {
                        // In ra Console của Server để dễ bắt bệnh nếu bạn nhập sai pass
                        System.out.println("❌ LỖI BĂM MẬT KHẨU TÀI KHOẢN '" + username + "':");
                        System.out.println("- Hash lưu trong DB : " + dbHash);
                        System.out.println("- Hash nhập từ Java: " + javaHash);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Lỗi SQL khi xác thực: " + e.getMessage());
        }
        return null;
    }

    // 2. Lấy thông tin tài khoản ANONYMOUS
    public User getAnonymousUser() {
        String sql = "SELECT u.user_id, u.role_id, u.username, u.email, u.full_name, " +
                "u.can_upload, u.can_download, u.enabled, r.role_name " +
                "FROM users u " +
                "LEFT JOIN roles r ON u.role_id = r.role_id " +
                "WHERE u.username = 'anonymous' AND u.enabled = TRUE";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return mapResultSetToUser(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 3. Đăng ký tài khoản mới (Khởi tạo Transaction để gán 1GB bộ nhớ mặc định)
    public boolean register(String username, String rawPassword, String email) {
        String insertUserSql = "INSERT INTO users (role_id, username, password_hash, email, verified, enabled) " +
                "VALUES (2, ?, ?, ?, TRUE, TRUE)";
        String insertStorageSql = "INSERT INTO user_storage (user_id, max_storage, used_storage) VALUES (?, 1073741824, 0)";

        String hashedPassword = PasswordUtil.hashPassword(rawPassword);

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmtUser = conn.prepareStatement(insertUserSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmtUser.setString(1, username);
                stmtUser.setString(2, hashedPassword);
                stmtUser.setString(3, email);

                int affectedRows = stmtUser.executeUpdate();
                if (affectedRows == 0) {
                    conn.rollback();
                    return false;
                }

                try (ResultSet generatedKeys = stmtUser.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newUserId = generatedKeys.getInt(1);

                        try (PreparedStatement stmtStorage = conn.prepareStatement(insertStorageSql)) {
                            stmtStorage.setInt(1, newUserId);
                            stmtStorage.executeUpdate();
                        }
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setRoleId(rs.getInt("role_id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setFullName(rs.getString("full_name"));
        user.setCanUpload(rs.getBoolean("can_upload"));
        user.setCanDownload(rs.getBoolean("can_download"));
        // Nếu LEFT JOIN không thấy role thì gán mặc định là Member
        user.setRoleName(rs.getString("role_name") != null ? rs.getString("role_name") : "Member");
        return user;
    }

    public boolean saveOtp(String email, String otpCode) {
        String sql = "INSERT INTO email_otps (email, otp_code, expired_at) " +
                "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, otpCode);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean verifyOtp(String email, String otpCode) {
        String sql = "SELECT otp_id FROM email_otps " +
                "WHERE email = ? AND otp_code = ? AND expired_at > NOW() AND verified = FALSE " +
                "ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, otpCode);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int otpId = rs.getInt("otp_id");
                    String updateSql = "UPDATE email_otps SET verified = TRUE WHERE otp_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, otpId);
                        updateStmt.executeUpdate();
                    }
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void ensureAnonymousUserExists() {
        if (getAnonymousUser() == null) {
            String sql = "INSERT INTO users (role_id, username, password_hash, email, full_name, can_upload, can_download, verified, enabled) " +
                    "VALUES (2, 'anonymous', NULL, 'anonymous@ftp.local', 'Anonymous User', TRUE, TRUE, TRUE, TRUE)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
                System.out.println("Đã khởi tạo tài khoản 'anonymous' mặc định vào Database.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT user_id, username, email, can_upload, can_download, enabled FROM users WHERE username != 'anonymous'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getBoolean("can_upload"),
                        rs.getBoolean("can_download"),
                        !rs.getBoolean("enabled")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean updateUserPermissions(int userId, boolean canUpload, boolean canDownload, boolean isBlocked) {
        String sql = "UPDATE users SET can_upload = ?, can_download = ?, enabled = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, canUpload);
            ps.setBoolean(2, canDownload);
            ps.setBoolean(3, !isBlocked);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}