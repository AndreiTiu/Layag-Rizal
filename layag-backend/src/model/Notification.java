package model;

// An in-app alert row (the email leg is sent by NotificationService).
public class Notification {
    private int notificationId;
    private int userId;
    private String type;
    private String title;
    private String message;
    private boolean isRead;
    private String createdAt;

    public Notification(int notificationId, int userId, String type, String title,
                        String message, boolean isRead, String createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public int getNotificationId() { return notificationId; }
    public int getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return isRead; }
    public String getCreatedAt() { return createdAt; }
}