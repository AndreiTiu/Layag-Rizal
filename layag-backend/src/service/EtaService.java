package service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class EtaService {

    // Base transit days: same-island 1, inter-island 2. Express takes 1 day off (min 1).
    public int getDeliveryDays(boolean interIsland, String serviceType) {
        int days = interIsland ? 2 : 1;
        if ("regular".equalsIgnoreCase(serviceType)) {
            return days;
        }
        if ("express".equalsIgnoreCase(serviceType)) {
            return Math.max(1, days - 1);
        }
        if ("same-day".equalsIgnoreCase(serviceType)) {
            return 1;
        }
        return days;
    }

    // Vehicle adds its own time: motorcycle threads city traffic (faster),
    // truck takes main roads and is heavier (slower). Never below 1 day.
    public int getDeliveryDays(boolean interIsland, String serviceType, String vehicleType) {
        int days = getDeliveryDays(interIsland, serviceType);
        switch (vehicleType.toUpperCase()) {
            case "MOTORCYCLE": return Math.max(1, days - 1);
            case "TRUCK":      return days + 1;
            default:           return days; // VAN, MINIVAN
        }
    }

    public String getEstimatedDate(boolean interIsland, String serviceType) {
        LocalDate eta = LocalDate.now().plusDays(getDeliveryDays(interIsland, serviceType));
        return eta.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
    }

    public String getEstimatedDate(boolean interIsland, String serviceType, String vehicleType) {
        LocalDate eta = LocalDate.now().plusDays(getDeliveryDays(interIsland, serviceType, vehicleType));
        return eta.format(DateTimeFormatter.ofPattern("MMM d, yyyy"));
    }
}