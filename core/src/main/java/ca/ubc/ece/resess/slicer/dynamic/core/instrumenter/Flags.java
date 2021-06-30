package ca.ubc.ece.resess.slicer.dynamic.core.instrumenter;


public class Flags {
    public boolean timeTracking;
    public boolean threadTracking;
    public boolean fieldTracking;
    public boolean isCallbackOrThread;
    public boolean threadIdInserted;
    public boolean isOriginal;
    public boolean superCalled;
    public Flags(boolean timeTracking, boolean threadTracking, boolean fieldTracking, boolean isCallbackOrThread, boolean threadIdInserted, boolean isOriginal){
       this.timeTracking = timeTracking;
       this.threadTracking = threadTracking;
       this.fieldTracking = fieldTracking;
       this.isCallbackOrThread = isCallbackOrThread;
       this.threadIdInserted = threadIdInserted;
       this.isOriginal = isOriginal;
       this.superCalled = false;
    }
}
