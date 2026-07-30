package server.dao;

import server.model.Group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    public List<Group> getGroupsByUserId(int userId) {
        List<Group> list = new ArrayList<>();
        String sql = "SELECT g.group_id, g.group_name, g.description " +
                "FROM `groups` g " +
                "JOIN `user_groups` ug ON g.group_id = ug.group_id " +
                "WHERE ug.user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new Group(
                        rs.getInt("group_id"),
                        rs.getString("group_name"),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // Tự động gán User mới vào Nhóm 'Public' mặc định
    public void addUserToDefaultGroup(int userId) {
        String sql = "INSERT IGNORE INTO `user_groups` (user_id, group_id) VALUES (?, 1)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}