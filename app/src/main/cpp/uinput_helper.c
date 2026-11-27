#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

static int emit_raw(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ie;
    memset(&ie, 0, sizeof(ie));
    ie.type = type;
    ie.code = code;
    ie.value = value;
    ssize_t written = write(fd, &ie, sizeof(ie));
    return (written == (ssize_t) sizeof(ie)) ? 0 : -1;
}

static int emit_syn(int fd) {
    return emit_raw(fd, EV_SYN, SYN_REPORT, 0);
}

JNIEXPORT jint JNICALL
Java_com_example_input_UinputBridge_nativeOpenUinput(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    }
    if (fd < 0) {
        return -errno;
    }

    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_REL) < 0) {
        int err = errno;
        close(fd);
        return -err;
    }

    /* Relative mouse axes: REL_X (0x00), REL_Y (0x01), REL_WHEEL (0x08), REL_HWHEEL (0x06) */
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);
    ioctl(fd, UI_SET_RELBIT, REL_HWHEEL);

    /* Mouse buttons: BTN_LEFT (0x110), BTN_RIGHT (0x111), BTN_MIDDLE (0x112) */
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);

    /* Standard Linux keyboard keycodes 1..248 */
    for (int code = 1; code <= 248; code++) {
        ioctl(fd, UI_SET_KEYBIT, code);
    }

    int setup_ok = 0;
#ifdef UI_DEV_SETUP
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = 0x1234;
    usetup.id.product = 0x5678;
    usetup.id.version = 1;
    snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "Swift KeyM Virtual Mouse");
    if (ioctl(fd, UI_DEV_SETUP, &usetup) >= 0) {
        setup_ok = 1;
    }
#endif

    if (!setup_ok) {
        struct uinput_user_dev uidev;
        memset(&uidev, 0, sizeof(uidev));
        snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "Swift KeyM Virtual Mouse");
        uidev.id.bustype = BUS_USB;
        uidev.id.vendor = 0x1234;
        uidev.id.product = 0x5678;
        uidev.id.version = 1;
        if (write(fd, &uidev, sizeof(uidev)) != (ssize_t) sizeof(uidev)) {
            int err = errno;
            close(fd);
            return -err;
        }
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        int err = errno;
        close(fd);
        return -err;
    }

    /* Allow Android InputReader a moment to register the new virtual input device */
    usleep(50000);
    return fd;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseButton(
        JNIEnv *env, jclass clazz, jint fd, jint linuxButton, jboolean down) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;

    if (emit_raw(fd, EV_KEY, (uint16_t) linuxButton, down ? 1 : 0) < 0) {
        return JNI_FALSE;
    }
    return emit_syn(fd) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseMove(
        JNIEnv *env, jclass clazz, jint fd, jint dx, jint dy) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;
    if (dx == 0 && dy == 0) return JNI_TRUE;

    if (dx != 0 && emit_raw(fd, EV_REL, REL_X, (int32_t) dx) < 0) {
        return JNI_FALSE;
    }
    if (dy != 0 && emit_raw(fd, EV_REL, REL_Y, (int32_t) dy) < 0) {
        return JNI_FALSE;
    }
    return emit_syn(fd) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseScroll(
        JNIEnv *env, jclass clazz, jint fd, jint vScroll, jint hScroll) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;
    if (vScroll == 0 && hScroll == 0) return JNI_TRUE;

    if (vScroll != 0 && emit_raw(fd, EV_REL, REL_WHEEL, (int32_t) vScroll) < 0) {
        return JNI_FALSE;
    }
    if (hScroll != 0 && emit_raw(fd, EV_REL, REL_HWHEEL, (int32_t) hScroll) < 0) {
        return JNI_FALSE;
    }
    return emit_syn(fd) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectKey(
        JNIEnv *env, jclass clazz, jint fd, jint linuxKeyCode, jboolean down) {
    (void) env;
    (void) clazz;
    if (fd < 0 || linuxKeyCode <= 0) return JNI_FALSE;

    if (emit_raw(fd, EV_KEY, (uint16_t) linuxKeyCode, down ? 1 : 0) < 0) {
        return JNI_FALSE;
    }
    return emit_syn(fd) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_input_UinputBridge_nativeCloseUinput(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (fd >= 0) {
        ioctl(fd, UI_DEV_DESTROY);
        close(fd);
    }
}
