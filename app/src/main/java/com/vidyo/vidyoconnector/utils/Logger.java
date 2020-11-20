package com.vidyo.vidyoconnector.utils;

import android.util.Log;

import com.vidyo.vidyoconnector.BuildConfig;

public class Logger {

    public enum LogType {
        ERROR, INFO, WARNING, DEBUG
    }

    private static final boolean ENABLED = BuildConfig.DEBUG;

    private static final String TAG = "VidyoConnector";

    public static void e(String error) {
        log(getCallerClassName(), error, LogType.ERROR);
    }

    public static void e(String error, Object... format) {
        log(getCallerClassName(), String.format(error, format), LogType.ERROR);
    }

    public static void i(String info) {
        log(getCallerClassName(), info, LogType.INFO);
    }

    public static void i(String info, Object... format) {
        log(getCallerClassName(), String.format(info, format), LogType.INFO);
    }

    public static void d(String debug) {
        log(getCallerClassName(), debug, LogType.DEBUG);
    }

    public static void d(String debug, Object... format) {
        log(getCallerClassName(), String.format(debug, format), LogType.DEBUG);
    }

    public static void w(String warning) {
        log(getCallerClassName(), warning, LogType.WARNING);
    }

    public static void w(String warning, Object... format) {
        log(getCallerClassName(), String.format(warning, format), LogType.WARNING);
    }

    private static void log(String cls, String message, LogType logType) {
        StringBuilder builder = new StringBuilder();
        if (cls != null) {
            builder.append(cls);
            builder.append(": ");
        }

        if (message != null) {
            builder.append(message);
        }

        String data = builder.toString();

        if (ENABLED) {
            switch (logType) {
                case ERROR:
                    Log.e(TAG, data);
                    break;
                case WARNING:
                    Log.w(TAG, data);
                    break;
                case INFO:
                    Log.i(TAG, data);
                    break;
            }
        }
    }

    private static String getCallerClassName() {
        try {
            StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
            for (int i = 1; i < stElements.length; i++) {
                StackTraceElement ste = stElements[i];
                if (!ste.getClassName().equals(Logger.class.getName()) && ste.getClassName().indexOf("java.lang.Thread") != 0) {
                    return parseClassName(ste) + ": " + ste.getMethodName();
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String parseClassName(StackTraceElement stackTraceElement) {
        String className = stackTraceElement.getClassName();
        int dotIndex = className.lastIndexOf(".");
        return dotIndex > 0 ? className.substring(dotIndex) : className;
    }
}