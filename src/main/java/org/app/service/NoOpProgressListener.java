package org.app.service;

public class NoOpProgressListener implements ProgressListener {
    @Override
    public void onStart(String stage, int total) {
    }

    @Override
    public void onProgress(int current, int total) {
    }

    @Override
    public void onDone(String stage) {
    }
}
