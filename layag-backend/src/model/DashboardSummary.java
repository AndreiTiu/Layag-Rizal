package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class DashboardSummary {
    private int totalParcels;
    private double totalRevenue;
    private double averageFee;
    private Map<String, Long> statusCounts;
    private ArrayList<Parcel> recentParcels;

    public DashboardSummary(int totalParcels, double totalRevenue, double averageFee,
                            Map<String, Long> statusCounts, ArrayList<Parcel> recentParcels) {
        this.totalParcels = totalParcels;
        this.totalRevenue = totalRevenue;
        this.averageFee = averageFee;
        this.statusCounts = new LinkedHashMap<>(statusCounts);
        this.recentParcels = recentParcels;
    }

    public int getTotalParcels() { return totalParcels; }
    public double getTotalRevenue() { return totalRevenue; }
    public double getAverageFee() { return averageFee; }
    public Map<String, Long> getStatusCounts() { return statusCounts; }
    public ArrayList<Parcel> getRecentParcels() { return recentParcels; }
}