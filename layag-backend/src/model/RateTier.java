package model;

public class RateTier {
    private double minWeight;
    private double maxWeight;
    private double ratePerKg;

    public RateTier(double minWeight, double maxWeight, double ratePerKg) {
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.ratePerKg = ratePerKg;
    }

    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public double getRatePerKg() { return ratePerKg; }
}