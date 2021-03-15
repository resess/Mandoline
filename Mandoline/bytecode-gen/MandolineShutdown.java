public class MandolineShutdown extends Thread {
    MandolineShutdown() {
    }

    public void run() {
        System.out.println("Dumping queue on shutdown");
        StringBuilder sb = new StringBuilder("SLICING:");
        for (String s: MandolineLogger.queue) {
            sb.append(s);
            sb.append("-");
        }
        System.out.println(sb.toString());
    }
}