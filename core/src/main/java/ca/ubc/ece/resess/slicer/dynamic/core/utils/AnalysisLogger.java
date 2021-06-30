package ca.ubc.ece.resess.slicer.dynamic.core.utils;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalysisLogger {

    static Integer sampleDump = 0;
    static Map<String, Integer> executionSamples = new HashMap<>();


    private AnalysisLogger() {
        throw new IllegalStateException("Utility class");
    }

    public static void log(boolean flag, String msg, Object ... args) {
        if (flag) {
            StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
            StackTraceElement e = stacktrace[2];
            String methodName = e.getMethodName();
            String className = e.getClassName();
            Logger logger = LoggerFactory.getLogger(className);
            logger.info(methodName + " (" + e.getLineNumber() + "): " +  msg, args);
        }
    }

    public static void warn(boolean flag, String msg, Object ... args) {
        if (flag) {
            StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
            StackTraceElement e = stacktrace[2];
            String methodName = e.getMethodName();
            String className = e.getClassName();
            Logger logger = LoggerFactory.getLogger(className);
            logger.warn(methodName + " (" + String.valueOf(e.getLineNumber()) + "): " +  msg, args);
        }
    }

    public static void error(String msg, Object ... args) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[2];
        String methodName = e.getMethodName();
        String className = e.getClassName();
        Logger logger = LoggerFactory.getLogger(className);
        logger.error(methodName + " (" + e.getLineNumber() + "): " +  msg, args);
        throw new Error("Logger error called");
    }

    public static void sample() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[2];
        String methodName = e.getMethodName();
        String location = methodName + " (" + e.getLineNumber() + ")";
        Integer samples = executionSamples.get(location);
        if (samples == null) {
            samples = 0;
        }
        executionSamples.put(location, samples+1);
        sampleDump++;
        if (sampleDump == 1000000) {
            AnalysisLogger.log(true, "Sample dump:\n{}", getExecutionSamples());
            sampleDump = 0;
        }
    }

    public static String getExecutionSamples() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> sample: executionSamples.entrySet()) {
            sb.append(sample.getKey());
            sb.append(": ");
            sb.append(sample.getValue());
            sb.append("\n");
        }
        return sb.toString();
    }
}