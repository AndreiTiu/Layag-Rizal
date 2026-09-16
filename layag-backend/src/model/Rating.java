package model;

// One review of a delivered parcel, aimed at the rider who completed it.
public class Rating {
    private int ratingId;
    private int parcelId;
    private int userId;
    private int riderUserId;
    private int stars;      // 1..5
    private String comment;
    private String createdAt;

    public Rating(int ratingId, int parcelId, int userId, int riderUserId, int stars, String comment, String createdAt) {
        this.ratingId = ratingId;
        this.parcelId = parcelId;
        this.userId = userId;
        this.riderUserId = riderUserId;
        this.stars = stars;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public int getRatingId() { return ratingId; }
    public int getParcelId() { return parcelId; }
    public int getUserId() { return userId; }
    public int getRiderUserId() { return riderUserId; }
    public int getStars() { return stars; }
    public String getComment() { return comment; }
    public String getCreatedAt() { return createdAt; }
}