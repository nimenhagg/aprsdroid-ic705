/*
 * Minimal Hamlib JNI boundary for APRSdroid Mod (AGENT.md section 16.3, PR2).
 *
 * This layer stays deliberately thin: it owns a small handle registry and maps
 * the Hamlib calls that the Kotlin side needs (version, rig enumeration,
 * create/open/close, frequency, mode, PTT, error text). It does not know about
 * APRS, AFSK, AX.25, sessions, transports or the IC-705 WLAN path.
 *
 * Rules kept here on purpose:
 *   - every handle call is serialized with a per-handle mutex;
 *   - handle ids are never reused, so a stale id can never alias a new rig;
 *   - a closed or unknown handle is rejected instead of touching freed memory;
 *   - no Hamlib C struct is exposed to Kotlin, only scalars and strings.
 */

#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include <hamlib/rig.h>

#ifndef HAMLIB_JNI_PINNED_VERSION
#define HAMLIB_JNI_PINNED_VERSION "unknown"
#endif
#ifndef HAMLIB_JNI_PINNED_REVISION
#define HAMLIB_JNI_PINNED_REVISION "unknown"
#endif

/* Registry size for the first JNI iteration. Handles are meant to be long
 * lived, so a small fixed table keeps the boundary allocation-free. */
#define HJNI_MAX_HANDLES 16

/* JNI-layer errors. Hamlib's own codes are small negative numbers, so these
 * live far below that range and can never be confused with a Hamlib error. */
#define HJNI_EHANDLE (-4096)
#define HJNI_ETOOMANY (-4097)
#define HJNI_EBACKEND (-4098)
#define HJNI_EENCODE (-4099)
#define HJNI_EINVAL_ARG (-4100)

typedef struct {
    jlong id;              /* 0 means the slot is free */
    RIG *rig;
    int opened;
    pthread_mutex_t lock;
} hjni_slot;

static hjni_slot g_slots[HJNI_MAX_HANDLES];
static pthread_mutex_t g_registry_lock = PTHREAD_MUTEX_INITIALIZER;
static jlong g_next_id = 1;
static int g_backends_loaded = 0;

/* ------------------------------------------------------------------ helpers */

static hjni_slot *hjni_lookup_locked(jlong id)
{
    int i;

    if (id == 0) {
        return NULL;
    }
    for (i = 0; i < HJNI_MAX_HANDLES; i++) {
        if (g_slots[i].id == id) {
            return &g_slots[i];
        }
    }
    return NULL;
}

/*
 * Resolves a handle and takes its call lock. The registry lock is released
 * before the Hamlib call runs so long operations never block other handles.
 * Returns NULL when the handle is unknown or already destroyed.
 */
static hjni_slot *hjni_acquire(jlong id)
{
    hjni_slot *slot;

    pthread_mutex_lock(&g_registry_lock);
    slot = hjni_lookup_locked(id);
    if (slot != NULL) {
        pthread_mutex_lock(&slot->lock);
    }
    pthread_mutex_unlock(&g_registry_lock);
    return slot;
}

static void hjni_release(hjni_slot *slot)
{
    if (slot != NULL) {
        pthread_mutex_unlock(&slot->lock);
    }
}

static int hjni_load_backends_locked(void)
{
    int rc;

    if (g_backends_loaded) {
        return RIG_OK;
    }
    rc = rig_load_all_backends();
    if (rc != RIG_OK) {
        return HJNI_EBACKEND;
    }
    g_backends_loaded = 1;
    return RIG_OK;
}

static jstring hjni_new_string(JNIEnv *env, const char *text)
{
    const char *value = (text != NULL) ? text : "";
    return (*env)->NewStringUTF(env, value);
}

/* ------------------------------------------------------------------- version */

JNIEXPORT jstring JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeVersion(JNIEnv *env, jclass clazz)
{
    char buffer[256];

    (void)clazz;
    snprintf(buffer, sizeof(buffer), "%s (%s)", hamlib_version2,
             hamlib_version);
    return hjni_new_string(env, buffer);
}

JNIEXPORT jstring JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeBackendRevision(JNIEnv *env,
                                                                 jclass clazz)
{
    char buffer[256];

    (void)clazz;
    snprintf(buffer, sizeof(buffer), "%s@%s", HAMLIB_JNI_PINNED_VERSION,
             HAMLIB_JNI_PINNED_REVISION);
    return hjni_new_string(env, buffer);
}

/* --------------------------------------------------------------- enumeration */

typedef struct {
    JNIEnv *env;
    jobjectArray array;
    jint index;
    jint capacity;
    jclass string_class;
} hjni_enum_context;

/* Hamlib 4.7 does not expose a single "supported modes" mask on rig_caps, so
 * the mask is derived from the advertised receive ranges. */
static rmode_t hjni_caps_modes(const struct rig_caps *caps)
{
    rmode_t mask = RIG_MODE_NONE;
    const freq_range_t *lists[2];
    int list;
    int i;

    lists[0] = caps->rx_range_list1;
    lists[1] = caps->rx_range_list2;
    for (list = 0; list < 2; list++) {
        for (i = 0; i < HAMLIB_FRQRANGESIZ; i++) {
            /* A zero start/end pair terminates the advertised range list. */
            if (lists[list][i].startf == 0 && lists[list][i].endf == 0) {
                break;
            }
            mask |= lists[list][i].modes;
        }
    }
    return mask;
}

static int hjni_caps_callback(const struct rig_caps *caps, rig_ptr_t data)
{
    hjni_enum_context *ctx = (hjni_enum_context *)data;
    char line[512];
    int flags = 0;
    jstring text;

    if (ctx == NULL || caps == NULL) {
        return 0;
    }
    if (caps->get_freq != NULL) {
        flags |= 1 << 0;
    }
    if (caps->set_freq != NULL) {
        flags |= 1 << 1;
    }
    if (caps->get_mode != NULL) {
        flags |= 1 << 2;
    }
    if (caps->set_mode != NULL) {
        flags |= 1 << 3;
    }
    if (caps->get_ptt != NULL) {
        flags |= 1 << 4;
    }
    if (caps->set_ptt != NULL) {
        flags |= 1 << 5;
    }

    ctx->index++;
    if (ctx->array == NULL) {
        /* Counting pass: no JNI objects are created yet.
         * Hamlib rig_list_foreach stops when cfunc returns 0;
         * return non-zero to continue enumerating all rigs. */
        return -1;
    }

    /* Tab separated, ASCII only: the Kotlin side decodes this into a value
     * object and never sees a Hamlib struct. */
    snprintf(line, sizeof(line), "%d\t%s\t%s\t%s\t%d\t%d\t%llu\t%d",
             (int)caps->rig_model,
             (caps->mfg_name != NULL) ? caps->mfg_name : "",
             (caps->model_name != NULL) ? caps->model_name : "",
             (caps->version != NULL) ? caps->version : "",
             (int)caps->status,
             flags,
             (unsigned long long)hjni_caps_modes(caps),
             (int)caps->port_type);

    text = (*ctx->env)->NewStringUTF(ctx->env, line);
    if (text == NULL) {
        return 0; /* stop: an exception is already pending */
    }
    if (ctx->index - 1 < ctx->capacity) {
        (*ctx->env)->SetObjectArrayElement(ctx->env, ctx->array, ctx->index - 1,
                                           text);
    }
    (*ctx->env)->DeleteLocalRef(ctx->env, text);
    /* Hamlib rig_list_foreach stops when cfunc returns 0;
     * return non-zero to continue enumerating all rigs. */
    return -1;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeRigCount(JNIEnv *env,
                                                          jclass clazz)
{
    hjni_enum_context ctx;
    int rc;

    (void)clazz;
    memset(&ctx, 0, sizeof(ctx));
    ctx.env = env;
    pthread_mutex_lock(&g_registry_lock);
    rc = hjni_load_backends_locked();
    if (rc == RIG_OK) {
        rc = rig_list_foreach(hjni_caps_callback, (rig_ptr_t)&ctx);
        if (rc == RIG_OK) {
            rc = (int)ctx.index;
        }
    }
    pthread_mutex_unlock(&g_registry_lock);
    if (rc < 0) {
        return 0;
    }
    return (jint)rc;
}

JNIEXPORT jobjectArray JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeListRigs(JNIEnv *env,
                                                          jclass clazz)
{
    hjni_enum_context ctx;
    jobjectArray result;
    int rc;

    (void)clazz;
    memset(&ctx, 0, sizeof(ctx));
    ctx.env = env;
    ctx.string_class = (*env)->FindClass(env, "java/lang/String");
    if (ctx.string_class == NULL) {
        return NULL;
    }

    pthread_mutex_lock(&g_registry_lock);
    rc = hjni_load_backends_locked();
    if (rc != RIG_OK) {
        pthread_mutex_unlock(&g_registry_lock);
        (*env)->DeleteLocalRef(env, ctx.string_class);
        return NULL;
    }
    rc = rig_list_foreach(hjni_caps_callback, (rig_ptr_t)&ctx);
    if (rc == RIG_OK) {
        ctx.capacity = ctx.index;
        ctx.index = 0;
        result = (*env)->NewObjectArray(env, ctx.capacity, ctx.string_class,
                                        NULL);
        if (result != NULL) {
            ctx.array = result;
            rc = rig_list_foreach(hjni_caps_callback, (rig_ptr_t)&ctx);
        }
    }
    pthread_mutex_unlock(&g_registry_lock);
    (*env)->DeleteLocalRef(env, ctx.string_class);
    if (rc != RIG_OK) {
        return NULL;
    }
    return ctx.array;
}

/* --------------------------------------------------------------- lifecycle */

JNIEXPORT jlong JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeCreate(JNIEnv *env, jclass clazz,
                                                        jint model_id)
{
    RIG *rig;
    int i;
    int rc;
    jlong id = 0;

    (void)clazz;
    if (model_id <= 0) {
        return 0;
    }

    pthread_mutex_lock(&g_registry_lock);
    rc = hjni_load_backends_locked();
    if (rc != RIG_OK) {
        pthread_mutex_unlock(&g_registry_lock);
        return 0;
    }
    for (i = 0; i < HJNI_MAX_HANDLES; i++) {
        if (g_slots[i].id == 0) {
            break;
        }
    }
    if (i == HJNI_MAX_HANDLES) {
        pthread_mutex_unlock(&g_registry_lock);
        return HJNI_ETOOMANY;
    }

    /* rig_init must not run while holding the registry lock longer than
     * necessary, but the slot reservation has to stay atomic. */
    g_slots[i].id = g_next_id++;
    pthread_mutex_unlock(&g_registry_lock);

    rig = rig_init((rig_model_t)model_id);
    if (rig == NULL) {
        pthread_mutex_lock(&g_registry_lock);
        g_slots[i].id = 0;
        pthread_mutex_unlock(&g_registry_lock);
        return 0;
    }

    pthread_mutex_lock(&g_registry_lock);
    id = g_slots[i].id;
    g_slots[i].rig = rig;
    g_slots[i].opened = 0;
    pthread_mutex_unlock(&g_registry_lock);
    (void)env;
    return id;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeOpen(JNIEnv *env, jclass clazz,
                                                      jlong id, jstring pathname)
{
    hjni_slot *slot;
    const char *path = NULL;
    int rc;

    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }

    if (pathname != NULL) {
        path = (*env)->GetStringUTFChars(env, pathname, NULL);
        if (path == NULL) {
            hjni_release(slot);
            return HJNI_EENCODE;
        }
    }
    if (path != NULL && path[0] != '\0') {
        hamlib_token_t token = rig_token_lookup(slot->rig, "rig_pathname");
        if (token != RIG_CONF_END) {
            rc = rig_set_conf(slot->rig, token, path);
            if (rc != RIG_OK) {
                (*env)->ReleaseStringUTFChars(env, pathname, path);
                hjni_release(slot);
                return rc;
            }
        }
    }
    if (path != NULL) {
        (*env)->ReleaseStringUTFChars(env, pathname, path);
    }

    rc = rig_open(slot->rig);
    if (rc == RIG_OK) {
        slot->opened = 1;
    }
    hjni_release(slot);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeClose(JNIEnv *env, jclass clazz,
                                                       jlong id)
{
    hjni_slot *slot;
    int rc;

    (void)env;
    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    if (!slot->opened) {
        hjni_release(slot);
        return RIG_OK;
    }
    rc = rig_close(slot->rig);
    if (rc == RIG_OK) {
        slot->opened = 0;
    }
    hjni_release(slot);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeDestroy(JNIEnv *env, jclass clazz,
                                                         jlong id)
{
    hjni_slot *slot;
    RIG *rig;
    int rc;

    (void)env;
    (void)clazz;
    if (id == 0) {
        return RIG_OK;
    }

    pthread_mutex_lock(&g_registry_lock);
    slot = hjni_lookup_locked(id);
    if (slot == NULL) {
        pthread_mutex_unlock(&g_registry_lock);
        return HJNI_EHANDLE;
    }
    /* Wait for in-flight calls, then retire the id so it can never be
     * resolved again even if the slot is reused. */
    pthread_mutex_lock(&slot->lock);
    rig = slot->rig;
    slot->rig = NULL;
    slot->opened = 0;
    slot->id = 0;
    pthread_mutex_unlock(&slot->lock);
    pthread_mutex_unlock(&g_registry_lock);

    rc = rig_cleanup(rig);
    return (rc == RIG_OK) ? RIG_OK : rc;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeIsOpen(JNIEnv *env, jclass clazz,
                                                        jlong id)
{
    hjni_slot *slot;
    int opened;

    (void)env;
    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    opened = slot->opened;
    hjni_release(slot);
    return opened;
}

/* ------------------------------------------------------- freq / mode / ptt */

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetFreq(JNIEnv *env, jclass clazz,
                                                         jlong id, jdoubleArray out)
{
    hjni_slot *slot;
    freq_t freq = 0.0;
    jdouble value;
    int rc;

    (void)clazz;
    if (out == NULL || (*env)->GetArrayLength(env, out) < 1) {
        return HJNI_EINVAL_ARG;
    }
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_get_freq(slot->rig, RIG_VFO_CURR, &freq);
    hjni_release(slot);
    if (rc != RIG_OK) {
        return rc;
    }
    value = (jdouble)freq;
    (*env)->SetDoubleArrayRegion(env, out, 0, 1, &value);
    return RIG_OK;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetFreq(JNIEnv *env, jclass clazz,
                                                         jlong id, jdouble hz)
{
    hjni_slot *slot;
    int rc;

    (void)env;
    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_set_freq(slot->rig, RIG_VFO_CURR, (freq_t)hz);
    hjni_release(slot);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetMode(JNIEnv *env, jclass clazz,
                                                         jlong id, jlongArray out)
{
    hjni_slot *slot;
    rmode_t mode = RIG_MODE_NONE;
    pbwidth_t width = 0;
    jlong values[2];
    int rc;

    (void)clazz;
    if (out == NULL || (*env)->GetArrayLength(env, out) < 2) {
        return HJNI_EINVAL_ARG;
    }
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_get_mode(slot->rig, RIG_VFO_CURR, &mode, &width);
    hjni_release(slot);
    if (rc != RIG_OK) {
        return rc;
    }
    values[0] = (jlong)mode;
    values[1] = (jlong)width;
    (*env)->SetLongArrayRegion(env, out, 0, 2, values);
    return RIG_OK;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetMode(JNIEnv *env, jclass clazz,
                                                         jlong id, jlong mode,
                                                         jlong width)
{
    hjni_slot *slot;
    int rc;

    (void)env;
    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_set_mode(slot->rig, RIG_VFO_CURR, (rmode_t)mode, (pbwidth_t)width);
    hjni_release(slot);
    return rc;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeGetPtt(JNIEnv *env, jclass clazz,
                                                        jlong id, jintArray out)
{
    hjni_slot *slot;
    ptt_t ptt = RIG_PTT_OFF;
    jint value;
    int rc;

    (void)clazz;
    if (out == NULL || (*env)->GetArrayLength(env, out) < 1) {
        return HJNI_EINVAL_ARG;
    }
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_get_ptt(slot->rig, RIG_VFO_CURR, &ptt);
    hjni_release(slot);
    if (rc != RIG_OK) {
        return rc;
    }
    value = (jint)ptt;
    (*env)->SetIntArrayRegion(env, out, 0, 1, &value);
    return RIG_OK;
}

JNIEXPORT jint JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeSetPtt(JNIEnv *env, jclass clazz,
                                                        jlong id, jboolean on)
{
    hjni_slot *slot;
    int rc;

    (void)env;
    (void)clazz;
    slot = hjni_acquire(id);
    if (slot == NULL) {
        return HJNI_EHANDLE;
    }
    rc = rig_set_ptt(slot->rig, RIG_VFO_CURR,
                     (on == JNI_TRUE) ? RIG_PTT_ON : RIG_PTT_OFF);
    hjni_release(slot);
    return rc;
}

/* ------------------------------------------------------------ error / names */

JNIEXPORT jstring JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeErrorText(JNIEnv *env, jclass clazz,
                                                           jint code)
{
    const char *text;
    const char *alternate;

    (void)clazz;
    text = rigerror((int)code);
    if (text == NULL || text[0] == '\0' ||
        strcmp(text, "Unknown error") == 0) {
        /* Hamlib's own callers pass both the negative return value and the
         * positive enum value, so try the other sign before giving up. */
        alternate = rigerror(-(int)code);
        if (alternate != NULL && alternate[0] != '\0' &&
            strcmp(alternate, "Unknown error") != 0) {
            text = alternate;
        }
    }
    return hjni_new_string(env, text);
}

JNIEXPORT jstring JNICALL
Java_org_aprsdroid_app_hamlib_HamlibNative_nativeModeName(JNIEnv *env, jclass clazz,
                                                          jlong mode)
{
    (void)clazz;
    return hjni_new_string(env, rig_strrmode((rmode_t)mode));
}
