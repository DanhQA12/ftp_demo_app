package common;

import java.io.Serializable;

public class FileItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private long size; // Dung lượng tính bằng Byte
    private String lastModified;
    private boolean isDirectory;

    public FileItem(String name, long size, String lastModified, boolean isDirectory) {
        this.name = name;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    /**
     * Hỗ trợ xuất ra định dạng chuẩn của FTP LIST (tương tự ls -l trên Unix)
     * nếu bạn muốn Client parse chuỗi String, hoặc hiển thị trực tiếp lên Console.
     */
    @Override
    public String toString() {
        String type = isDirectory ? "d" : "-";
        return type + "|" + size + "|" + lastModified + "|" + name;
    }
}