package server.dao;

import server.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class FileShareDAO {

    public int getUserIdByUsername(String username) {
        String sql = "SELECT user_id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("user_id");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public int getFileIdByPath(String filePath) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "SELECT file_id FROM files WHERE file_path = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("file_id");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    // Cấp quyền chia sẻ file cho User
    public boolean shareFileToUser(int fileId, int targetUserId, int permissionTypeId, int grantedById) {
        // Sử dụng ON DUPLICATE KEY UPDATE để nếu đã share rồi thì chỉ cập nhật quyền mới
        String sql = "INSERT INTO file_permissions (file_id, user_id, permission_type_id, granted_by) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE permission_type_id = VALUES(permission_type_id)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            ps.setInt(2, targetUserId);
            ps.setInt(3, permissionTypeId);
            ps.setInt(4, grantedById);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // Lấy danh sách file được chia sẻ định dạng chuẩn để gửi về Client
    public List<String> getSharedFilesForUser(int userId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT f.file_name, f.is_folder, f.file_size, f.created_at, u.username as owner_name, pt.permission_name " +
                "FROM file_permissions fp " +
                "JOIN files f ON fp.file_id = f.file_id " +
                "JOIN users u ON f.owner_id = u.user_id " +
                "JOIN permission_types pt ON fp.permission_type_id = pt.permission_type_id " +
                "WHERE fp.user_id = ?";

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("file_name");
                    boolean isFolder = rs.getBoolean("is_folder");
                    long size = rs.getLong("file_size");
                    String date = rs.getTimestamp("created_at") != null ? sdf.format(rs.getTimestamp("created_at")) : "";
                    String owner = rs.getString("owner_name");

                    // Lấy chính xác mã quyền từ DB: "READ_ONLY" hoặc "FULL_CONTROL"
                    String perm = rs.getString("permission_name");

                    String type = isFolder ? "File folder" : "File";
                    String sizeStr = isFolder ? "" : String.valueOf(size);

                    // Nối chuỗi theo chuẩn 6 cột: Name | Date | Type | Size | Owner | Permission
                    list.add(name + "|" + date + "|" + type + "|" + sizeStr + "|" + owner + "|" + perm);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean removeShare(int userId, String fileName) {
        String sql = "DELETE fp FROM file_permissions fp " +
                "JOIN files f ON fp.file_id = f.file_id " +
                "WHERE fp.user_id = ? AND f.file_name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, fileName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public String getFilePermissionType(String filePath) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "SELECT pt.permission_name FROM file_permissions fp " +
                "JOIN files f ON fp.file_id = f.file_id " +
                "JOIN permission_types pt ON fp.permission_type_id = pt.permission_type_id " +
                "WHERE f.file_path = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("permission_name");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "FULL_CONTROL"; // Mặc định nếu không có bản ghi riêng
    }
}