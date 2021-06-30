package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Instrumenter {
    String instrumentationPaths;
    protected List<String> instrumentationPackagesList;
    public void start (String args[]) {
        throw new IllegalStateException("Should not call this method");
    }

    public void start (String options, String staticLogFile, String apkPath, String mandolineJar) {
        throw new IllegalStateException("Should not call this method");
    }

    public void createInstrumentationPackagesList(){
        instrumentationPackagesList = new ArrayList<>();
        if (instrumentationPaths != null) {
            instrumentationPackagesList = Arrays.asList(instrumentationPaths.split("-"));
        }
    }
}
