package repository;

import model.Notification;
import java.util.ArrayList;

public interface NotificationRepository {
    Notification create(int userId, String type, String title, String message);
    ArrayList<Notification> findByUser(int userId, int limit);
    int unreadCount(int userId);
    void markRead(int notificationId, int userId);
    void markAllRead(int userId);
}