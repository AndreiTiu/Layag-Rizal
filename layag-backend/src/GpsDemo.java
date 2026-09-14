import model.LocationPoint;
import repository.GpsRepositoryJdbc;
import service.GpsService;
import service.GpsSimulator;
import java.util.ArrayList;

public class GpsDemo {
    public static void main(String[] args) throws InterruptedException {
        GpsService gpsService = new GpsService(new GpsRepositoryJdbc());
        GpsSimulator sim = new GpsSimulator(gpsService);

        System.out.println("=== GPS SIMULATION: Rizal last-mile route ===");
        System.out.println("QC pickup -> Antipolo -> Taytay -> Binangonan drop-off\n");

        int parcelId = 55; // pretend parcel #55 is out for delivery in Rizal
        sim.sendPings(parcelId);

        System.out.println("\n=== ROUTE RETRIEVED FROM DATABASE ===");
        ArrayList<LocationPoint> route = gpsService.getRoute(parcelId);
        for (LocationPoint pt : route) {
            System.out.println("  [" + pt.getRecordedAt() + "] " + pt.getLatitude() + ", " + pt.getLongitude());
        }

        System.out.println("\n=== HAVERSINE DISTANCE CHECK (QC -> Binangonan) ===");
        double d = gpsService.distanceKm(14.6324, 121.0381, 14.4648, 121.1950);
        System.out.printf("  Straight-line distance: %.1f km%n", d);
    }
}