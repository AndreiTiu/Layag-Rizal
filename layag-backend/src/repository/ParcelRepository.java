package repository;

import model.Parcel;
import java.util.ArrayList;

public interface ParcelRepository {
    Parcel registerParcel(String sender, String receiver, double weight, double fee, String vehicleType, String trackingCode, String serviceType, int createdByUserId, String pickUpAddress, String dropOffAddress, double distanceKm, boolean interIsland);
    Parcel findById(int id);
    Parcel findByTrackingCode(String trackingCode);
    ArrayList<Parcel> findAll();
    ArrayList<Parcel> findByStatus(String status);
    ArrayList<Parcel> findByCreatedBy(int userId);
    ArrayList<Parcel> search(String term);
    void updateStatus(int id, String status);
    void confirm(int id);
    void updateDetails(int id, String sender, String receiver, String pickUpAddress, String dropOffAddress, double weight, double fee, String vehicleType, String serviceType, double distanceKm, boolean interIsland);
    int count();
    long countByStatus(String status);
    double totalFees();
    double averageFee();
}