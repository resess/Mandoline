public class MandolineShutdown extends Thread {
    MandolineShutdown() {
    }

    @Override
    public void run() {
        MandolineLogger.flush();
    }
}