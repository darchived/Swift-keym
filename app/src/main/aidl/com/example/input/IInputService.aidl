package com.example.input;

interface IInputService {
    void injectKey(int keyCode, int action, int metaState) = 1;
    void injectMouseButton(int button, boolean down) = 2;
    void injectMouseMove(float dx, float dy) = 3;
    void injectMouseScroll(float vScroll, float hScroll) = 4;
    void resetAllInputs() = 5;
    String execShell(String command) = 6;
    boolean allowOverlayAppOps(String packageName) = 7;
    void destroyUinput() = 8;
    void setVirtualMouseActive(boolean active) = 9;
    void sendMouseKeepAlive() = 10;
    void destroy() = 16777114;
}
