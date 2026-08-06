package server.util;

import java.io.File;
import java.io.IOException;

public class SecurityUtil {
    /**
     * Kiểm tra Path Traversal (Tấn công vượt thư mục).
     * Ngăn chặn Client dùng các lệnh như: CWD ../../../windows/system32
     *
     * @param rootDir Thư mục gốc của Server (VD: server_files)
     * @param targetFile Đường dẫn Client muốn truy cập
     * @return true nếu an toàn (nằm trong rootDir), false nếu nguy hiểm.
     */
    public static boolean isPathSafe(File rootDir, File targetFile) {
        try {
            // getCanonicalPath() tự động phân giải các ký tự "../" hoặc "./"
            // về đường dẫn tuyệt đối thực sự trên ổ cứng.
            String rootPath = rootDir.getCanonicalPath();
            String targetPath = targetFile.getCanonicalPath();

            // Nếu đường dẫn thực tế bắt đầu bằng đường dẫn gốc -> Hợp lệ
            return targetPath.startsWith(rootPath);

        } catch (IOException e) {
            // Nếu có lỗi phân giải đường dẫn, chặn luôn cho an toàn
            return false;
        }
    }
}
