package mandoline.instrumenter;

public class Instrumenter {
    public void start (String args[]) {
        throw new IllegalStateException("Should not call this method");
    }

    public void start (String options, String staticLogFile, String apkPath, String mandolineJar) {
        throw new IllegalStateException("Should not call this method");
    }
}
