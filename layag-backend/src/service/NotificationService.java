package service;

import mail.Mailer;
import model.Notification;
import repository.NotificationRepository;

import java.util.ArrayList;

// Dual-channel notification: every important system event is persisted in the
// notifications table (in-app) AND emailed to the user via the configured
// Mailer. Email failures are swallowed so they never block the API; in-app
// alerts are the single source of truth.
public class NotificationService {
    private final NotificationRepository repository;
    private final Mailer mailer;

    public NotificationService(NotificationRepository repository, Mailer mailer) {
        this.repository = repository;
        this.mailer = mailer;
    }

    // Create the in-app row, then attempt to send the email (non-blocking).
    public void notify(int userId, String email, String type, String title, String message) {
        repository.create(userId, type, title, message);
        if (email != null && mailer != null) {
            try {
                mailer.send(email, "LAYAG \u2022 " + title, message);
            } catch (Exception ignored) {
            }
        }
    }

    public ArrayList<Notification> list(int userId, int limit) {
        return repository.findByUser(userId, limit);
    }

    public int unreadCount(int userId) {
        return repository.unreadCount(userId);
    }

    public void markRead(int notificationId, int userId) {
        repository.markRead(notificationId, userId);
    }

    public void markAllRead(int userId) {
        repository.markAllRead(userId);
    }
}