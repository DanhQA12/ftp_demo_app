package common;

public enum FTPCommand {
    // Nhóm xác thực
    USER, PASS, QUIT,

    // Nhóm điều hướng và quản lý thư mục
    PWD, CWD, MKD, RMD, DELE,

    // Nhóm quản lý kênh Dữ liệu & Truyền tải
    PASV, PORT, LIST, RETR, STOR, REST,

    // Lệnh không hợp lệ
    UNKNOWN;

    // Xử lý ngoại lệ nếu Client gửi lệnh không được hỗ trợ
    public static FTPCommand fromString(String command) {
        if (command == null || command.trim().isEmpty()) {
            return UNKNOWN;
        }
        try {
            return FTPCommand.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}