package repository;

import model.RiderProfile;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

// In-memory twin of RiderRepositoryJdbc. Kept in sync with the JDBC
// implementation so the layer can be swapped for demos/tests without
// touching the service.

public class RiderRepositoryInMemory implements RiderRepository {
    private final ConcurrentHashMap<Integer, RiderProfile> store = new ConcurrentHashMap<>();

    @Override
    public void createProfile(int riderUserId, String vehicleType, int maxConcurrent) {
        store.put(riderUserId, new RiderProfile(riderUserId, vehicleType, false, maxConcurrent));
    }

    @Override
    public RiderProfile findById(int riderUserId) {
        return store.get(riderUserId);
    }

    @Override
    public ArrayList<RiderProfile> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void setAvailable(int riderUserId, boolean available) {
        RiderProfile p = store.get(riderUserId);
        if (p != null) {
            p.setAvailable(available);
        }
    }
}