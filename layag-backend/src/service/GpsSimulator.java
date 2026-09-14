package service;

import repository.GpsRepository;
import repository.GpsRepositoryJdbc;

public class GpsSimulator {
    private GpsService gpsService;

    // Real Rizal last-mile route (approx): Quezon City -> Antipolo -> Taytay -> Binangonan
    private static final double[][] RIZAL_ROUTE = {
        {14.6324, 121.0381},  // Quezon City pickup
        {14.6010, 121.0550},  // en route
        {14.5862, 121.1770},  // Antipolo
        {14.5589, 121.1327},  // Taytay
        {14.4648, 121.1950}   // Binangonan (drop-off)
    };

    public GpsSimulator(GpsService gpsService) {
        this.gpsService = gpsService;
    }

    // Moves a parcel along the Rizal route, recording a coordinate every delay ms.
    // In production, the courier's device would send these pings instead.
    public void sendPings(int parcelId) throws InterruptedException {
        for (double[] point : RIZAL_ROUTE) {
            gpsService.recordLocation(parcelId, point[0], point[1]);
            System.out.printf("[GPS-SIM] parcel #%d at %.4f, %.4f%n",
                parcelId, point[0], point[1]);
            Thread.sleep(1500);
        }
    }
}