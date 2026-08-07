package server.dao;

import server.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FileDAO {
    // Lưu thông tin file/thư mục mới vào Database
    public boolean addFileRecord(int ownerId, String fileName, String filePath, boolean isFolder, long fileSize) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "INSERT INTO files (owner_id, file_name, file_path, is_folder, file_size) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ownerId);
            ps.setString(2, fileName);
            ps.setString(3, cleanPath);
            ps.setBoolean(4, isFolder);
            ps.setLong(5, fileSize);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi lưu DB File: " + e.getMessage());
        }
        return false;
    }

    // Xóa record trong DB khi user xóa file/thư mục trên giao diện
    public void deleteFileRecord(String filePath) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "DELETE FROM files WHERE file_path = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cleanPath);
            ps.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Lỗi xóa DB File: " + e.getMessage());
        }
    }

    // Lấy ID của user sở hữu file dựa theo đường dẫn tuyệt đối trên server
    public int getFileOwnerId(String filePath) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "SELECT owner_id FROM files WHERE file_path = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cleanPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("owner_id");
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi lấy owner_id của file: " + e.getMessage());
        }
        return -1; // Trả về -1 nếu không tìm thấy bản ghi trong DB
    }

    public boolean updateFilePermission(String absolutePath, String permType) {
        String cleanPath = absolutePath.replace("\\", "/");

        // 1. Lấy file_id từ đường dẫn
        int fileId = getFileIdByPath(cleanPath);
        if (fileId == -1) return false;

        // 2. Lấy permission_type_id tương ứng với chuỗi 'READ_ONLY' hoặc 'FULL_CONTROL'
        int permTypeId = -1;
        String findTypeSql = "SELECT permission_type_id FROM permission_types WHERE permission_name = ?";

        String upsertSql = "INSERT INTO file_permissions (file_id, user_id, permission_type_id, granted_by) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE permission_type_id = VALUES(permission_type_id)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Lấy ID của loại quyền
            try (PreparedStatement psType = conn.prepareStatement(findTypeSql)) {
                psType.setString(1, permType);
                try (ResultSet rs = psType.executeQuery()) {
                    if (rs.next()) {
                        permTypeId = rs.getInt("permission_type_id");
                    }
                }
            }

            if (permTypeId == -1) return false;

            // Tiến hành thêm hoặc cập nhật quyền vào bảng file_permissions (áp dụng cho chủ sở hữu file hoặc phân quyền chung)
            int ownerId = getFileOwnerId(cleanPath);
            if (ownerId == -1) ownerId = 1; // Mặc định nếu không tìm thấy owner thì gán cho admin (id = 1)

            try (PreparedStatement psUpsert = conn.prepareStatement(upsertSql)) {
                psUpsert.setInt(1, fileId);
                psUpsert.setInt(2, ownerId);
                psUpsert.setInt(3, permTypeId);
                psUpsert.setInt(4, ownerId);

                return psUpsert.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật CSDL file_permissions: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public int getFileIdByPath(String filePath) {
        String cleanPath = filePath.replace("\\", "/");
        String sql = "SELECT file_id FROM files WHERE file_path = ?"; // Lưu ý: Đảm bảo cột khóa chính trong DB của bạn tên là file_id

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cleanPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("file_id");
                }
            }

        } catch (SQLException e) {
            System.err.println("Lỗi lấy file_id: " + e.getMessage());
        }
        return -1; // Trả về -1 nếu không tìm thấy
    }
}