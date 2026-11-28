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

static int s_mouse_fd = -1;
static int s_kbd_fd = -1;

JNIEXPORT void JNICALL
Java_com_example_input_UinputBridge_nativeDestroyUinput(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    if (s_mouse_fd >= 0) {
        ioctl(s_mouse_fd, UI_DEV_DESTROY);
        close(s_mouse_fd);
        s_mouse_fd = -1;
    }
    if (s_kbd_fd >= 0) {
        ioctl(s_kbd_fd, UI_DEV_DESTROY);
        close(s_kbd_fd);
        s_kbd_fd = -1;
    }
}

JNIEXPORT jint JNICALL
Java_com_example_input_UinputBridge_nativeOpenMouseUinput(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;

    if (s_mouse_fd >= 0) {
        ioctl(s_mouse_fd, UI_DEV_DESTROY);
        close(s_mouse_fd);
        s_mouse_fd = -1;
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    }
    if (fd < 0) {
        return -errno;
    }

    /* Strictly relative mouse mode ONLY: EV_SYN + EV_KEY + EV_REL (zero EV_ABS bits) */
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

    int setup_ok = 0;
#ifdef UI_DEV_SETUP
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = 0x1234;
    usetup.id.product = 0x5678;
    usetup.id.version = 1;
    snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "SwiftKeyM-VirtualMouse");
    if (ioctl(fd, UI_DEV_SETUP, &usetup) >= 0) {
        setup_ok = 1;
    }
#endif

    if (!setup_ok) {
        struct uinput_user_dev uidev;
        memset(&uidev, 0, sizeof(uidev));
        snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "SwiftKeyM-VirtualMouse");
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

    s_mouse_fd = fd;
    usleep(50000);
    return fd;
}

JNIEXPORT jint JNICALL
Java_com_example_input_UinputBridge_nativeOpenKeyboardUinput(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;

    if (s_kbd_fd >= 0) {
        ioctl(s_kbd_fd, UI_DEV_DESTROY);
        close(s_kbd_fd);
        s_kbd_fd = -1;
    }

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) {
        fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    }
    if (fd < 0) {
        return -errno;
    }

    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0 ||
        ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) {
        int err = errno;
        close(fd);
        return -err;
    }

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
    usetup.id.product = 0x5679;
    usetup.id.version = 1;
    snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "SwiftKeyM-VirtualKeyboard");
    if (ioctl(fd, UI_DEV_SETUP, &usetup) >= 0) {
        setup_ok = 1;
    }
#endif

    if (!setup_ok) {
        struct uinput_user_dev uidev;
        memset(&uidev, 0, sizeof(uidev));
        snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "SwiftKeyM-VirtualKeyboard");
        uidev.id.bustype = BUS_USB;
        uidev.id.vendor = 0x1234;
        uidev.id.product = 0x5679;
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

    s_kbd_fd = fd;
    usleep(50000);
    return fd;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseButton(
        JNIEnv *env, jclass clazz, jint fd, jint linuxButton, jboolean down) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;

    struct input_event events[2];
    memset(events, 0, sizeof(events));
    events[0].type = EV_KEY;
    events[0].code = (uint16_t) linuxButton;
    events[0].value = down ? 1 : 0;
    events[1].type = EV_SYN;
    events[1].code = SYN_REPORT;
    events[1].value = 0;

    ssize_t written = write(fd, events, sizeof(events));
    return (written == (ssize_t) sizeof(events)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseMove(
        JNIEnv *env, jclass clazz, jint fd, jint dx, jint dy) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;

    struct input_event events[3];
    memset(events, 0, sizeof(events));
    events[0].type = EV_REL;
    events[0].code = REL_X;
    events[0].value = (int32_t) dx;
    events[1].type = EV_REL;
    events[1].code = REL_Y;
    events[1].value = (int32_t) dy;
    events[2].type = EV_SYN;
    events[2].code = SYN_REPORT;
    events[2].value = 0;

    ssize_t written = write(fd, events, sizeof(events));
    return (written == (ssize_t) sizeof(events)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseKeepAlive(
        JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;

    struct input_event events[2];
    memset(events, 0, sizeof(events));
    events[0].type = EV_REL;
    events[0].code = REL_X;
    events[0].value = 0;
    events[1].type = EV_SYN;
    events[1].code = SYN_REPORT;
    events[1].value = 0;

    ssize_t written = write(fd, events, sizeof(events));
    return (written == (ssize_t) sizeof(events)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectMouseScroll(
        JNIEnv *env, jclass clazz, jint fd, jint vScroll, jint hScroll) {
    (void) env;
    (void) clazz;
    if (fd < 0) return JNI_FALSE;
    if (vScroll == 0 && hScroll == 0) return JNI_TRUE;

    struct input_event events[3];
    memset(events, 0, sizeof(events));
    int count = 0;
    if (vScroll != 0) {
        events[count].type = EV_REL;
        events[count].code = REL_WHEEL;
        events[count].value = (int32_t) vScroll;
        count++;
    }
    if (hScroll != 0) {
        events[count].type = EV_REL;
        events[count].code = REL_HWHEEL;
        events[count].value = (int32_t) hScroll;
        count++;
    }
    events[count].type = EV_SYN;
    events[count].code = SYN_REPORT;
    events[count].value = 0;
    count++;

    ssize_t bytes = (ssize_t) (count * sizeof(struct input_event));
    ssize_t written = write(fd, events, (size_t) bytes);
    return (written == bytes) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_input_UinputBridge_nativeInjectKey(
        JNIEnv *env, jclass clazz, jint fd, jint linuxKeyCode, jboolean down) {
    (void) env;
    (void) clazz;
    if (fd < 0 || linuxKeyCode <= 0) return JNI_FALSE;

    struct input_event events[2];
    memset(events, 0, sizeof(events));
    events[0].type = EV_KEY;
    events[0].code = (uint16_t) linuxKeyCode;
    events[0].value = down ? 1 : 0;
    events[1].type = EV_SYN;
    events[1].code = SYN_REPORT;
    events[1].value = 0;

    ssize_t written = write(fd, events, sizeof(events));
    return (written == (ssize_t) sizeof(events)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_input_UinputBridge_nativeCloseUinput(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (fd >= 0) {
        ioctl(fd, UI_DEV_DESTROY);
        close(fd);
        if (fd == s_mouse_fd) s_mouse_fd = -1;
        if (fd == s_kbd_fd) s_kbd_fd = -1;
    }
}
