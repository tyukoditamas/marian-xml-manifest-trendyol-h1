package org.app.logging;

public class NoOpLogger implements AppLogger {
    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void error(String message, Throwable error) {
    }
}
