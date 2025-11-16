package com.chatai.hotword;

public interface HotwordDetector {
    void setCallback(HotwordDetectionService.HotwordCallback callback);
    boolean initialize();
    void start();
    void stop();
    void pause();
    void resume();
    void release();
    boolean isRunning();
}

