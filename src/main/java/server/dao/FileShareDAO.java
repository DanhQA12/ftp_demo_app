package server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FileShareDAO {
    // Tìm user_id qua username
    public int getUserIdByUsername(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // Thêm Thông báo vào bảng notifications
    public boolean createNotification(int senderId, int receiverId, String title, String message) {
        String sql = "INSERT INTO notifications (sender_id, receiver_id, title, message) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, title);
            ps.setString(4, message);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Ghi nhận Phân quyền Chia sẻ File (ACL)
    public boolean grantFilePermission(int fileId, int targetUserId, int permissionTypeId, int grantedByUserId) {
        String sql = "INSERT INTO file_permissions (file_id, user_id, permission_type_id, granted_by) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE permission_type_id = VALUES(permission_type_id)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            ps.setInt(2, targetUserId);
            ps.setInt(3, permissionTypeId); // 1: READ_ONLY, 2: FULL_CONTROL
            ps.setInt(4, grantedByUserId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
