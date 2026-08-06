package server.model;

import java.sql.Timestamp;

public class TransferLog {
    private int logId;
    private int userId;
    private String fileName;
    private long fileSize;
    private String commandType; // 'STOR' (Upload) hoặc 'RETR' (Download)
    private boolean success;
    private Timestamp createdAt;

    public TransferLog(int logId, int userId, String fileName, long fileSize, String commandType, boolean success, Timestamp createdAt) {
        this.logId = logId;
        this.userId = userId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.commandType = commandType;
        this.success = success;
        this.createdAt = createdAt;
    }

    // Các Getter và Setter
    public int getLogId() { return logId; }
    public int getUserId() { return userId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getCommandType() { return commandType; }
    public boolean isSuccess() { return success; }
    public Timestamp getCreatedAt() { return createdAt; }
}