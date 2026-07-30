package server.model;

public class Group {
    private int groupId;
    private String groupName;
    private String description;

    public Group(int groupId, String groupName, String description) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
    }

    public int getGroupId() { return groupId; }
    public String getGroupName() { return groupName; }
    public String getDescription() { return description; }
}