package repository;

import model.RiderProfile;
import java.util.ArrayList;

public interface RiderRepository {
    // Admin provisions a rider account: one profile row per courier user.
    void createProfile(int riderUserId, String vehicleType, int maxConcurrent);
    RiderProfile findById(int riderUserId);
    ArrayList<RiderProfile> findAll();
    // Rider toggles their own availability (work-shift switch).
    void setAvailable(int riderUserId, boolean available);
}