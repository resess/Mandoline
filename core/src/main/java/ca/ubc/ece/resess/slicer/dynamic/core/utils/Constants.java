package ca.ubc.ece.resess.slicer.dynamic.core.utils;

public class Constants {

    public static final double CALL_EDGE = -1.0;
    public static final double RETURN_EDGE = -2.0;
    public static final double FLOW_EDGE = 2.0;
    public static final Integer FORWARD = 1;
    public static final Integer BACKWARD = 2;
    public static final Integer ACCESS_PATH_LENGTH = 10;
    public static final Integer SEARCH_LENGTH= 5000;
    public static final Integer THIS = -1;
    public static final Integer CLEAR = -2;
    public static final Integer RETURN = -3;
    public static final Integer UNKNOWN = -4;
    public static final Integer THREAD_COUNT= 16;
    public static final Integer TIMEOUT = 60*1000*1000;
    public static final String ANDROID_LIBS = "android.support.";
    public static boolean DEBUG = false;

    private Constants() {
        throw new IllegalStateException("Utility class");
    }
}