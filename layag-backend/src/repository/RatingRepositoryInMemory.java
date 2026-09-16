package repository;

import model.Rating;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// In-memory twin of RatingRepositoryJdbc.
public class RatingRepositoryInMemory implements RatingRepository {
    private final ConcurrentHashMap<Integer, Rating> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1);

    @Override
    public Rating create(int parcelId, int userId, int riderUserId, int stars, String comment) {
        if (store.containsKey(parcelId)) {
            throw new RuntimeException("Parcel already rated.");
        }
        Rating r = new Rating(seq.getAndIncrement(), parcelId, userId, riderUserId, stars, comment, now());
        store.put(parcelId, r);
        return r;
    }

    @Override
    public Rating findByParcel(int parcelId) {
        return store.get(parcelId);
    }

    @Override
    public ArrayList<Rating> findByRider(int riderUserId) {
        ArrayList<Rating> out = new ArrayList<>();
        for (Rating r : store.values()) {
            if (r.getRiderUserId() == riderUserId) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public double averageForRider(int riderUserId) {
        ArrayList<Rating> all = findByRider(riderUserId);
        if (all.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Rating r : all) {
            sum += r.getStars();
        }
        return Math.round(sum / all.size() * 10.0) / 10.0;
    }

    private String now() {
        return new java.sql.Timestamp(System.currentTimeMillis()).toString();
    }
}