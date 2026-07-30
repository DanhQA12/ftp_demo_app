package server.model;

public class User {
    private int userId;
    private int roleId;
    private String username;
    private String email;
    private String fullName;
    private String roleName;
    private boolean canUpload;
    private boolean canDownload;
    private boolean isBlocked;

    // 1. Constructor rỗng
    public User() {}

    // 2. Constructor 6 tham số (Dùng cho phương thức getAllUsers)
    public User(int userId, String username, String email, boolean canUpload, boolean canDownload, boolean isBlocked) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.canUpload = canUpload;
        this.canDownload = canDownload;
        this.isBlocked = isBlocked;
    }

    // --- GETTERS & SETTERS ĐẦY ĐỦ ---
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    // Phương thức gộp (Alias) cho getId / setId nếu có đoạn code khác đang dùng
    public int getId() { return userId; }
    public void setId(int id) { this.userId = id; }

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public boolean isCanUpload() { return canUpload; }
    public void setCanUpload(boolean canUpload) { this.canUpload = canUpload; }

    public boolean isCanDownload() { return canDownload; }
    public void setCanDownload(boolean canDownload) { this.canDownload = canDownload; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }
}