import java.util.ArrayList;

public class MandolineWriter extends Thread {
    ArrayList<String> queue;
    MandolineWriter(ArrayList<String> q) {
        this.queue = new ArrayList<>(q);
    }

    public void run() {
        StringBuilder sb = new StringBuilder("SLICING:");
        for (String s: queue) {
            sb.append(s);
            sb.append("-");
        }
        System.out.println(sb.toString());
    }
}