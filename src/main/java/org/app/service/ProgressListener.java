package org.app.service;

public interface ProgressListener {
    void onStart(String stage, int total);

    void onProgress(int current, int total);

    void onDone(String stage);
}
