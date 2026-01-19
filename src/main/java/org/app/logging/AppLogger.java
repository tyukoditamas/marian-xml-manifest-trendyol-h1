package org.app.logging;

public interface AppLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable error);
}
