import java.util.ArrayList;

public class MandolineLogger{
    static final int SIZE = 1;
    static final int DOUBLE_SIZE = 2;
    static ArrayList<String> queue = new ArrayList<>(DOUBLE_SIZE);
    static {
        Runtime.getRuntime().addShutdownHook(new MandolineShutdown());
    }

    public static synchronized void println(String e) {
        // queue.add(e);
        // if (queue.size() > SIZE) {
            // MandolineWriter mw = new MandolineWriter(queue);
            // queue = new ArrayList<>(DOUBLE_SIZE);
            // mw.start();
        // }
        System.out.println("SLICING:"+e);
        return;
    }
    /*
    public static synchronized void println(String e) {
        queue.add(e);
        if (queue.size() > SIZE) {
            MandolineWriter mw = new MandolineWriter(queue);
            queue = new ArrayList<>(DOUBLE_SIZE);
            mw.start();
        }
        return;
    }
    */
    public static synchronized void flush(String e) {
        System.out.println("Flushing queue");
        StringBuilder sb = new StringBuilder("SLICING:");
        for (String s: queue) {
            sb.append(s);
            sb.append("-");
        }
        System.out.println(sb.toString());
        queue = new ArrayList<>(DOUBLE_SIZE);
    }
}