package repository;

import model.Parcel;
import java.util.ArrayList;

public interface ParcelRepository {
    Parcel registerParcel(String sender, String receiver, double weight, double fee, String vehicleType, String trackingCode, String serviceType, int createdByUserId);
    Parcel findById(int id);
    Parcel findByTrackingCode(String trackingCode);
    ArrayList<Parcel> findAll();
    ArrayList<Parcel> findByStatus(String status);
    ArrayList<Parcel> search(String term);
    void updateStatus(int id, String status);
    int count();
    long countByStatus(String status);
    double totalFees();
    double averageFee();
}