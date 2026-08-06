package common;

public enum FTPResponseCode {
    // 1xx: Khởi tạo
    DATA_OPEN(150, "Status file ổn; chuẩn bị mở kết nối dữ liệu."),

    // 2xx: Thành công
    COMMAND_OK(200, "Lệnh hợp lệ."),
    READY(220, "Dịch vụ sẵn sàng tiếp nhận người dùng mới."),
    CLOSING_DATA(226, "Đang đóng kết nối dữ liệu. Quá trình xử lý file đã hoàn thành."),
    ENTERING_PASSIVE(227, "Chuyển sang Passive Mode"),
    LOGGED_IN(230, "Đăng nhập thành công."),
    DIR_CHANGED(250, "Thay đổi thư mục thành công."),

    // 3xx: Cần thêm thông tin
    NEED_PASSWORD(331, "Username hợp lệ, cần password."),

    // 4xx: Lỗi tạm thời
    DATA_CONN_FAILED(425, "Không thể mở được kết nối dữ liệu."),

    // 5xx: Lỗi / Từ chối
    SYNTAX_ERROR(500, "Sai cú pháp, không thể xác định lệnh."),
    NOT_LOGGED_IN(530, "Chưa đăng nhập."),
    FILE_ACTION_NOT_TAKEN(550, "Yêu cầu bị từ chối. File không tồn tại hoặc bạn không có quyền truy cập.");

    private final int code;
    private final String defaultMessage;

    FTPResponseCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    // Định dạng mã và nội dung mặc định để gửi qua Socket.
    public String format() {
        return code + " " + defaultMessage + "\r\n";
    }

    // Định dạng mã với nội dung tùy chỉnh (dành cho PASV hoặc báo lỗi cụ thể).
    public String format(String customMessage) {
        return code + " " + customMessage + "\r\n";
    }
}