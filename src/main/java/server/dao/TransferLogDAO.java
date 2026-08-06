package server.dao;

import server.config.DatabaseConnection;
import server.model.TransferLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransferLogDAO {

    // Ghi lại nhật ký mỗi khi Client thực hiện Upload (STOR) hoặc Download (RETR).
    public boolean logTransfer(int userId, String fileName, long fileSize, String commandType, boolean success) {
        String sql = "INSERT INTO transfer_logs (user_id, file_name, file_size, command_type, success) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, fileName);
            stmt.setLong(3, fileSize);
            stmt.setString(4, commandType);
            stmt.setBoolean(5, success);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi ghi log truyền tải: " + e.getMessage());
        }
        return false;
    }

    /**
     * Cập nhật dung lượng đã sử dụng của User (Cộng thêm khi Upload, Trừ đi khi Delete).
     * Hàm này liên kết chặt chẽ với bảng user_storage được tạo ra lúc đăng ký tài khoản.
     */
    public boolean updateUsedStorage(int userId, long fileSizeDelta) {
        // fileSizeDelta: Số byte dương (nếu upload thêm) hoặc âm (nếu xóa file)
        String sql = "UPDATE user_storage SET used_storage = used_storage + ? " +
                "WHERE user_id = ? AND (used_storage + ?) <= max_storage";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, fileSizeDelta);
            stmt.setInt(2, userId);
            stmt.setLong(3, fileSizeDelta);

            // Trả về true nếu update thành công (không vượt quá max_storage)
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật dung lượng: " + e.getMessage());
        }
        return false;
    }

    /**
     * Lấy lịch sử logs của một User cụ thể.
     */
    public List<TransferLog> getLogsByUser(int userId) {
        List<TransferLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM transfer_logs WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(new TransferLog(
                            rs.getInt("log_id"),
                            rs.getInt("user_id"),
                            rs.getString("file_name"),
                            rs.getLong("file_size"),
                            rs.getString("command_type"),
                            rs.getBoolean("success"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }
}