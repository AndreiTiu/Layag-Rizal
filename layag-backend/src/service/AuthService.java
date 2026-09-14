package service;

import model.User;
import repository.UserRepository;
import security.PasswordHasher;

public class AuthService {
    private static final int MIN_PASSWORD_LEN = 6;

    private UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String name, String email, String rawPassword, String role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LEN + " characters.");
        }
        String norm = email.trim().toLowerCase();
        String hash = PasswordHasher.hash(rawPassword);
        int id = userRepository.register(new User(0, name.trim(), norm, hash, role.toUpperCase()));
        return new User(id, name.trim(), norm, hash, role.toUpperCase(), false);
    }

    public User login(String email, String rawPassword) {
        String norm = (email == null) ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(norm);
        // One message for missing account AND wrong password: an attacker must
        // not be able to tell which emails are registered.
        if (user == null || !PasswordHasher.verify(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }
        if (!user.isVerified()) {
            throw new EmailNotVerifiedException(user.getEmail());
        }
        // Old SHA-256 hashes are upgraded to PBKDF2 on first successful login.
        if (PasswordHasher.isLegacyHex(user.getPasswordHash())) {
            String secureHash = PasswordHasher.hash(rawPassword);
            userRepository.updateProfile(user.getUserId(), user.getName(), secureHash);
            user = new User(user.getUserId(), user.getName(), user.getEmail(), secureHash, user.getRole(), true);
        }
        return user;
    }

    public User updateProfile(int userId, String newName, String newRawPassword) {
        User current = userRepository.findById(userId);
        if (current == null) {
            throw new IllegalArgumentException("No such user.");
        }
        String name = (newName == null || newName.trim().isEmpty()) ? current.getName() : newName.trim();
        String hash = current.getPasswordHash();
        if (newRawPassword != null && !newRawPassword.isEmpty()) {
            if (newRawPassword.length() < MIN_PASSWORD_LEN) {
                throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD_LEN + " characters.");
            }
            hash = PasswordHasher.hash(newRawPassword);
        }
        userRepository.updateProfile(userId, name, hash);
        return new User(userId, name, current.getEmail(), hash, current.getRole(), current.isVerified());
    }
}