package com.example.input;

interface IInputService {
    void injectKey(int keyCode, int action, int metaState) = 1;
    void injectMouseButton(int button, boolean down) = 2;
    void injectMouseMove(float dx, float dy) = 3;
    void injectMouseScroll(float vScroll, float hScroll) = 4;
    void resetAllInputs() = 5;
    void destroy() = 16777114;
}
