package model;

public class FeeBreakdown {
    private double weightKg;
    private String tierLabel;
    private double ratePerKg;
    private double baseFee;
    private double multiplier;
    private double vehicleMultiplier;
    private double distanceKm;
    private double distanceFee;
    private double surcharge;
    private double totalFee;

    public FeeBreakdown(double weightKg, String tierLabel, double ratePerKg,
                        double baseFee, double multiplier, double vehicleMultiplier,
                        double surcharge, double totalFee) {
        this(weightKg, tierLabel, ratePerKg, baseFee, multiplier, vehicleMultiplier, 0, 0, surcharge, totalFee);
    }

    public FeeBreakdown(double weightKg, String tierLabel, double ratePerKg,
                        double baseFee, double multiplier, double vehicleMultiplier,
                        double distanceKm, double distanceFee, double surcharge, double totalFee) {
        this.weightKg = weightKg;
        this.tierLabel = tierLabel;
        this.ratePerKg = ratePerKg;
        this.baseFee = baseFee;
        this.multiplier = multiplier;
        this.vehicleMultiplier = vehicleMultiplier;
        this.distanceKm = distanceKm;
        this.distanceFee = distanceFee;
        this.surcharge = surcharge;
        this.totalFee = totalFee;
    }

    public double getWeightKg() { return weightKg; }
    public String getTierLabel() { return tierLabel; }
    public double getRatePerKg() { return ratePerKg; }
    public double getBaseFee() { return baseFee; }
    public double getMultiplier() { return multiplier; }
    public double getVehicleMultiplier() { return vehicleMultiplier; }
    public double getDistanceKm() { return distanceKm; }
    public double getDistanceFee() { return distanceFee; }
    public double getSurcharge() { return surcharge; }
    public double getTotalFee() { return totalFee; }
}