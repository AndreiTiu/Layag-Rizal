import model.Parcel;
import repository.ParcelRepository;
import repository.ParcelRepositoryJdbc;
import repository.ParcelRepositoryInMemory;
import service.ParcelService;
import service.FeeService;
import java.util.ArrayList;

public class JdbcTest {
    public static void main(String[] args) {
        System.out.println("=== IN-MEMORY VERSION ===");
        runApp(new ParcelRepositoryInMemory());

        System.out.println();
        System.out.println("=== MYSQL VERSION ===");
        runApp(new ParcelRepositoryJdbc());
    }

    static void runApp(ParcelRepository repo) {
        ParcelService service = new ParcelService(repo, new FeeService());

        Parcel p1 = service.registerParcel("Andre", "Maria", 2.5, "regular");
        System.out.println("Registered ID " + p1.getParcelId() + ": " + p1.getSenderName() + " -> " + p1.getReceiverName() + " | Fee: " + p1.getFee());

        ArrayList<Parcel> all = repo.findAll();
        for (Parcel p : all) {
            System.out.println("ID " + p.getParcelId() + " | " + p.getSenderName() + " -> " + p.getReceiverName() + " | " + p.getWeightKg() + " kg | Fee " + p.getFee() + " | " + p.getCurrentStatus());
        }

        service.updateStatus(p1.getParcelId(), "IN_TRANSIT");
        System.out.println("ID " + p1.getParcelId() + " status now: " + service.trackParcel(p1.getParcelId()).getCurrentStatus());
        System.out.println("Total: " + repo.count());
    }
}