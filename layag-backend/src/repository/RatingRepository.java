package repository;

import model.Rating;
import java.util.ArrayList;

public interface RatingRepository {
    // One rating per parcel: MySQL rejects a second row for the same parcel_id.
    Rating create(int parcelId, int userId, int riderUserId, int stars, String comment);
    Rating findByParcel(int parcelId);
    ArrayList<Rating> findByRider(int riderUserId);
    // Average stars (rounded to 1 decimal) for a rider; 0.0 when no ratings.
    double averageForRider(int riderUserId);
}