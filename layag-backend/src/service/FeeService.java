package service;

import model.FeeBreakdown;
import model.RateTier;
import java.util.ArrayList;

public class FeeService {
    public static final double INTER_ISLAND_SURCHARGE = 100.0;
    // Distance factor (per km). Covers fuel + wear; makes the fee weight AND
    // distance driven, matching the spec's "weight, distance, type of transport".
    public static final double PER_KM_RATE = 15.0;

    private ArrayList<RateTier> rateTiers;

    public FeeService() {
        rateTiers = new ArrayList<>();
        rateTiers.add(new RateTier(0, 1, 50));
        rateTiers.add(new RateTier(1, 3, 40));
        rateTiers.add(new RateTier(3, 10, 35));
        rateTiers.add(new RateTier(10, 999, 30));
    }

    public double getRatePerKg(double weightKg) {
        for (RateTier tier : rateTiers) {
            if (weightKg > tier.getMinWeight() && weightKg <= tier.getMaxWeight()) {
                return tier.getRatePerKg();
            }
        }
        return 50.0;
    }

    public String getTierLabel(double weightKg) {
        for (RateTier tier : rateTiers) {
            if (weightKg > tier.getMinWeight() && weightKg <= tier.getMaxWeight()) {
                return tier.getMaxWeight() >= 999
                    ? "> " + tier.getMinWeight() + " kg"
                    : (int) tier.getMinWeight() + "-" + (int) tier.getMaxWeight() + " kg";
            }
        }
        return "0-1 kg";
    }

    public double getMultiplier(String serviceType) {
        switch (serviceType.toLowerCase()) {
            case "express":  return 1.5;
            case "same-day": return 2.0;
            default:         return 1.0;
        }
    }

    // Vehicle cost factor: bigger/faster-capable vehicles cost more to run.
    // VAN is the baseline (1.0); motorcycle is cheap for city runs;
    // truck costs most (fuel + capacity).
    public double getVehicleMultiplier(String vehicleType) {
        switch (vehicleType.toUpperCase()) {
            case "MOTORCYCLE": return 0.8;
            case "MINIVAN":    return 1.2;
            case "TRUCK":      return 1.5;
            default:           return 1.0; // VAN
        }
    }

    public double computeBaseFee(double weightKg) {
        return weightKg * getRatePerKg(weightKg);
    }

    public double computeTotalFee(double weightKg, String serviceType, String vehicleType) {
        return computeTotalFee(weightKg, serviceType, vehicleType, 0);
    }

    public double computeTotalFee(double weightKg, String serviceType, String vehicleType, double distanceKm) {
        double base = computeBaseFee(weightKg);
        double fee = base * getMultiplier(serviceType) * getVehicleMultiplier(vehicleType)
            + Math.max(0, distanceKm) * PER_KM_RATE;
        return Math.round(fee * 100.0) / 100.0;
    }

    // Full itemized receipt for the UI / demo.
    public FeeBreakdown computeBreakdown(double weightKg, String serviceType, boolean interIsland) {
        return computeBreakdown(weightKg, serviceType, "VAN", interIsland, 0);
    }

    public FeeBreakdown computeBreakdown(double weightKg, String serviceType, String vehicleType, boolean interIsland) {
        return computeBreakdown(weightKg, serviceType, vehicleType, interIsland, 0);
    }

    public FeeBreakdown computeBreakdown(double weightKg, String serviceType, boolean interIsland, double distanceKm) {
        return computeBreakdown(weightKg, serviceType, "VAN", interIsland, distanceKm);
    }

    public FeeBreakdown computeBreakdown(double weightKg, String serviceType, String vehicleType, boolean interIsland, double distanceKm) {
        double rate = getRatePerKg(weightKg);
        double base = weightKg * rate;
        double multiplier = getMultiplier(serviceType);
        double vehicleMultiplier = getVehicleMultiplier(vehicleType);
        double dist = Math.max(0, distanceKm);
        double distanceFee = dist * PER_KM_RATE;
        double surcharge = interIsland ? INTER_ISLAND_SURCHARGE : 0.0;
        double total = base * multiplier * vehicleMultiplier + distanceFee + surcharge;
        total = Math.round(total * 100.0) / 100.0;
        return new FeeBreakdown(weightKg, getTierLabel(weightKg), rate, base, multiplier, vehicleMultiplier, dist, distanceFee, surcharge, total);
    }
}