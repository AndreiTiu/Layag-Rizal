package repository;

import model.Notification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// In-memory twin of NotificationRepositoryJdbc.
public class NotificationRepositoryInMemory implements NotificationRepository {
    private final ConcurrentHashMap<Integer, Notification> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    @Override
    public Notification create(int userId, String type, String title, String message) {
        Notification n = new Notification(seq.getAndIncrement(), userId, type, title, message, false, now());
        store.put(n.getNotificationId(), n);
        return n;
    }

    @Override
    public ArrayList<Notification> findByUser(int userId, int limit) {
        ArrayList<Notification> out = new ArrayList<>();
        for (Notification n : store.values()) {
            if (n.getUserId() == userId) {
                out.add(n);
            }
        }
        out.sort(Comparator.comparingInt(Notification::getNotificationId).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    @Override
    public int unreadCount(int userId) {
        int n = 0;
        for (Notification x : store.values()) {
            if (x.getUserId() == userId && !x.isRead()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void markRead(int notificationId, int userId) {
        Notification n = store.get(notificationId);
        if (n != null && n.getUserId() == userId) {
            store.replace(notificationId, new Notification(n.getNotificationId(), n.getUserId(),
                n.getType(), n.getTitle(), n.getMessage(), true, n.getCreatedAt()));
        }
    }

    @Override
    public void markAllRead(int userId) {
        store.values().stream()
            .filter(n -> n.getUserId() == userId && !n.isRead())
            .forEach(n -> markRead(n.getNotificationId(), userId));
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}