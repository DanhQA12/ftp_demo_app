package server.dao;

import server.config.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    public void addNotification(int receiverId, int senderId, String title, String message) {
        String sql = "INSERT INTO notifications (receiver_id, sender_id, title, message) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ps.setInt(2, senderId);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Lấy thông báo chưa đọc và đánh dấu là đã đọc
    public List<String> getAndMarkUnreadNotifications(int userId) {
        List<String> notifs = new ArrayList<>();
        String selectSql = "SELECT notification_id, title, message FROM notifications WHERE receiver_id = ? AND is_read = FALSE";
        String updateSql = "UPDATE notifications SET is_read = TRUE WHERE notification_id = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql);
                 PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {

                psSelect.setInt(1, userId);
                ResultSet rs = psSelect.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("notification_id");
                    String title = rs.getString("title");
                    String msg = rs.getString("message");
                    notifs.add(title + ": " + msg);

                    // Mark as read
                    psUpdate.setInt(1, id);
                    psUpdate.addBatch();
                }
                psUpdate.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notifs;
    }
}