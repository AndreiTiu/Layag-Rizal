package repository;

import model.User;

public interface UserRepository {
    User findByEmail(String email);
    User findById(int userId);
    int register(User user);
    void updateProfile(int userId, String name, String passwordHash);
    void setVerified(int userId);
}