package service;

import model.Rating;
import repository.RatingRepository;

import java.util.ArrayList;

// Rating business rules:
//  - one rating per delivered parcel (parcel_id is UNIQUE)
//  - stars must be 1..5
//  - the sender rates the rider who COMPLETED the delivery.
// Averages are computed on read so the rider profile always reflects the
// latest ratings without an extra write transaction.
public class RatingService {
    private final RatingRepository repository;

    public RatingService(RatingRepository repository) {
        this.repository = repository;
    }

    public Rating rate(int parcelId, int userId, int riderUserId, int stars, String comment) {
        if (riderUserId <= 0) {
            throw new IllegalArgumentException("Parcel has no completed rider to rate.");
        }
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        if (repository.findByParcel(parcelId) != null) {
            throw new IllegalArgumentException("This parcel is already rated.");
        }
        String text = (comment == null || comment.isBlank()) ? null : comment.trim();
        if (text != null && text.length() > 500) {
            text = text.substring(0, 500);
        }
        return repository.create(parcelId, userId, riderUserId, stars, text);
    }

    public Rating byParcel(int parcelId) {
        return repository.findByParcel(parcelId);
    }

    public ArrayList<Rating> forRider(int riderUserId) {
        return repository.findByRider(riderUserId);
    }

    public double averageForRider(int riderUserId) {
        return repository.averageForRider(riderUserId);
    }
}