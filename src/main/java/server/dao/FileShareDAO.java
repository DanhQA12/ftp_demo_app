package server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

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

    // Lấy danh sách tệp/thư mục được người khác chia sẻ từ bảng notifications chuẩn 5 cột
    public List<String> getSharedFilesForUser(int userId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT n.message, n.created_at, u.username AS sender_name " +
                "FROM notifications n " +
                "JOIN users u ON n.sender_id = u.user_id " +
                "WHERE n.receiver_id = ? " +
                "ORDER BY n.created_at DESC";

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String msg = rs.getString("message");
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                String senderName = rs.getString("sender_name");
                String dateStr = (createdAt != null) ? sdf.format(createdAt) : "--";

                // Tách tên tệp gốc ra khỏi câu thông báo
                String fileName = msg;
                if (fileName.startsWith("Tệp: ")) {
                    fileName = fileName.substring(5);
                }
                if (fileName.contains(" (Được chia sẻ bởi")) {
                    fileName = fileName.substring(0, fileName.indexOf(" (Được chia sẻ bởi"));
                }
                fileName = fileName.trim();

                String typeStr = fileName.contains(".")
                        ? fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase() + " File"
                        : "File";

                // Lấy dung lượng tệp thực tế từ thư mục của người gửi trên Server
                java.io.File sharedFile = new java.io.File("server_files/users/" + senderName + "/" + fileName);
                String sizeStr = "--";
                if (sharedFile.exists() && sharedFile.isFile()) {
                    sizeStr = String.valueOf(sharedFile.length()); // Trả về số Bytes thực tế
                }

                // Định dạng 5 cột chuẩn: Name|Date|Type|Size|Sender
                list.add(fileName + "|" + dateStr + "|" + typeStr + "|" + sizeStr + "|" + senderName);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

}