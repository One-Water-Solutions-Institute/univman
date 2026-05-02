import univman.UniManagement;
import univman.UniEvent;
import java.io.File;
import java.util.List;

public class Demo {
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0]
            : "c:\\projects\\git\\dataTwoModelEcosystem\\univman\\examples\\CT-NT.json";
        File f = new File(path);

        System.out.println("Loading: " + f.getCanonicalPath());
        List<UniManagement> mans = UniManagement.from(f);
        System.out.println("Found " + mans.size() + " management(s):");
        for (UniManagement m : mans) {
            System.out.println("  - " + m.getName()
                + (m.getYear() > 0 ? " (until_end_of " + m.getYear() + ")" : ""));
        }

        // The CT management runs until 2015, NT takes over through 2019
        String start = "2008-01-01";
        String end   = "2019-12-31";
        System.out.println("\nUnrolling event sequence: " + start + " -> " + end);

        List<UniEvent> events = UniManagement.createEventSequence(mans, start, end);
        System.out.println("Total events: " + events.size());
        System.out.println();
        System.out.println(UniEvent.toString(events));
    }
}
