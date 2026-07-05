#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#define SUSHUO_EXPORT __declspec(dllexport)
#define SUSHUO_CALL __stdcall
#else
#define SUSHUO_EXPORT __attribute__((visibility("default")))
#define SUSHUO_CALL
#endif

static volatile unsigned char sushuo_native_secret[16] = {
        0x53, 0x53, 0x4B, 0x21,
        0x5A, 0x4E, 0x31, 0x43,
        0x39, 0x2A, 0x77, 0x10,
        0x6D, 0x55, 0x42, 0x7E
};

enum {
    ENCODED_MARKER = 0x53535632,
    PACKED_MARKER = 0x53535033,
    RESOURCE_MARKER = 0x53535234,
    RESOURCE_MAGIC = 0x6D4F9B17,
    RESOURCE_VERSION = 2,
    RESOURCE_SALT = 0x6A09E667,
    SEAL_SALT = 0x4B455931,
    RESOURCE_HEADER_BYTES = 24,
    RESOURCE_CONSTANT_NULL = 0,
    RESOURCE_CONSTANT_INT = 1,
    RESOURCE_CONSTANT_LONG = 2,
    RESOURCE_CONSTANT_FLOAT = 3,
    RESOURCE_CONSTANT_DOUBLE = 4,
    RESOURCE_CONSTANT_STRING = 5,
    CODE_SALT = 0x41C64E6D,
    MAP_SALT = 0x27D4EB2D,

    PUSH_CONST = 1,
    LOAD = 2,
    STORE = 3,
    POP = 4,
    DUP = 5,
    IADD = 10,
    ISUB = 11,
    IMUL = 12,
    IDIV = 13,
    IREM = 14,
    INEG = 15,
    IXOR = 16,
    IAND = 17,
    IOR = 18,
    ISHL = 19,
    LADD = 20,
    LSUB = 21,
    LMUL = 22,
    LDIV = 23,
    LREM = 24,
    LNEG = 25,
    LXOR = 26,
    ISHR = 27,
    IUSHR = 28,
    LSHL = 29,
    FADD = 30,
    FSUB = 31,
    FMUL = 32,
    FDIV = 33,
    FREM = 34,
    FNEG = 35,
    LSHR = 36,
    LUSHR = 37,
    DADD = 40,
    DSUB = 41,
    DMUL = 42,
    DDIV = 43,
    DREM = 44,
    DNEG = 45,
    I2L = 50,
    I2F = 51,
    I2D = 52,
    L2I = 53,
    L2F = 54,
    L2D = 55,
    F2I = 56,
    F2L = 57,
    F2D = 58,
    D2I = 59,
    D2L = 60,
    D2F = 61,
    I2B = 62,
    I2C = 63,
    I2S = 64,
    LCMP = 65,
    FCMPL = 66,
    FCMPG = 67,
    DCMPL = 68,
    DCMPG = 69,
    INVOKE_STATIC = 70,
    IINC = 71,
    GET_STATIC = 72,
    PUT_STATIC = 73,
    GET_FIELD = 74,
    PUT_FIELD = 75,
    INVOKE = 76,
    CHECKCAST = 77,
    INSTANCEOF = 78,
    GOTO = 79,
    IFEQ = 91,
    IFNE = 92,
    IFLT = 93,
    IFGE = 94,
    IFGT = 95,
    IFLE = 96,
    IF_ICMPEQ = 97,
    IF_ICMPNE = 98,
    IF_ICMPLT = 99,
    IF_ICMPGE = 100,
    IF_ICMPGT = 101,
    IF_ICMPLE = 102,
    IF_ACMPEQ = 103,
    IF_ACMPNE = 104,
    IFNULL = 105,
    IFNONNULL = 106,
    RETURN_OP = 90
};

static uint32_t rotl32(uint32_t value, uint32_t shift) {
    shift &= 31U;
    return shift == 0U ? value : (value << shift) | (value >> (32U - shift));
}

static uint32_t rotr32(uint32_t value, uint32_t shift) {
    shift &= 31U;
    return shift == 0U ? value : (value >> shift) | (value << (32U - shift));
}

static uint32_t native_secret_word(int lane) {
    int offset = lane * 4;
    return ((uint32_t) sushuo_native_secret[offset] << 24U)
            | ((uint32_t) sushuo_native_secret[offset + 1] << 16U)
            | ((uint32_t) sushuo_native_secret[offset + 2] << 8U)
            | (uint32_t) sushuo_native_secret[offset + 3];
}

static jint stream_next(jint state, jint index) {
    uint32_t value = (uint32_t) state;
    value ^= (uint32_t) index * 0x45D9F3BU;
    value = rotl32(value + 0x7F4A7C15U, 9U);
    value ^= value >> 13U;
    value *= 0x5BD1E995U;
    value ^= value >> 15U;
    return (jint) value;
}

static jint *decode_int_array(JNIEnv *env, jintArray array, jint key, jint salt, jsize *out_len) {
    jsize len = (*env)->GetArrayLength(env, array);
    jint *raw = (*env)->GetIntArrayElements(env, array, NULL);
    if (raw == NULL) {
        return NULL;
    }
    jint *decoded = (jint *) malloc((size_t) len * sizeof(jint));
    if (decoded == NULL) {
        (*env)->ReleaseIntArrayElements(env, array, raw, JNI_ABORT);
        return NULL;
    }
    jint state = key ^ salt ^ (jint) len;
    for (jsize i = 0; i < len; i++) {
        state = stream_next(state, (jint) i);
        jint rotate = ((uint32_t) state >> 27U) & 15U;
        decoded[i] = (jint) (rotr32((uint32_t) raw[i], (uint32_t) rotate) ^ (uint32_t) state ^ (uint32_t) i);
    }
    (*env)->ReleaseIntArrayElements(env, array, raw, JNI_ABORT);
    *out_len = len;
    return decoded;
}

static jint *decode_int_words(const jint *raw, jsize len, jint key, jint salt) {
    jint *decoded = (jint *) malloc((size_t) len * sizeof(jint));
    if (decoded == NULL) {
        return NULL;
    }
    jint state = key ^ salt ^ (jint) len;
    for (jsize i = 0; i < len; i++) {
        state = stream_next(state, (jint) i);
        jint rotate = ((uint32_t) state >> 27U) & 15U;
        decoded[i] = (jint) (rotr32((uint32_t) raw[i], (uint32_t) rotate) ^ (uint32_t) state ^ (uint32_t) i);
    }
    return decoded;
}

typedef struct {
    jint *plain;
    jobjectArray chunks;
    jbyteArray resource_array;
    jbyte *resource_bytes;
    jint resource_bytes_len;
    jint resource_payload_len;
    jint resource_code_bytes_len;
    jint resource_nonce;
    jint resource_hash;
    jint resource_version;
    jint key;
    jint salt;
    jint length;
    jint chunk_bytes;
    jint packed;
    jint resource_packed;
} vm_code_reader;

static jint packed_stream_next(jint key, jint salt, jint index, jint length) {
    uint32_t state = (uint32_t) key ^ (uint32_t) salt ^ (uint32_t) length
            ^ rotl32((uint32_t) index * 0x9E3779B9U, 5U);
    state ^= (uint32_t) index * 0x45D9F3BU;
    state = rotl32(state + 0x7F4A7C15U, 9U);
    state ^= state >> 13U;
    state *= 0x5BD1E995U;
    state ^= state >> 15U;
    return (jint) state;
}

static jint java_hash_utf(const char *value) {
    jint hash = 0;
    while (*value != '\0') {
        hash = (jint) (31U * (uint32_t) hash + (uint8_t) *value);
        value++;
    }
    return hash;
}

static uint8_t resource_mask(jint key, jint nonce, jint resource_hash, jint code_length,
                             jint payload_length, jint index) {
    uint32_t state = (uint32_t) key ^ (uint32_t) nonce ^ (uint32_t) resource_hash ^ RESOURCE_SALT;
    state ^= native_secret_word(0);
    state ^= rotl32(native_secret_word(1), (uint32_t) index & 31U);
    state ^= rotl32(native_secret_word(2), ((uint32_t) index >> 3U) & 31U);
    state ^= native_secret_word(3) + (uint32_t) payload_length;
    state ^= rotl32((uint32_t) code_length * 0x45D9F3BU, 7U);
    state ^= rotl32((uint32_t) payload_length * 0x27D4EB2DU, 11U);
    state ^= rotl32((uint32_t) index * 0x9E3779B9U, 3U);
    state = rotl32(state + 0x7F4A7C15U, 9U);
    state ^= state >> 16U;
    state *= 0x85EBCA6BU;
    state ^= state >> 13U;
    state *= 0xC2B2AE35U;
    state ^= state >> 16U;
    return (uint8_t) (state >> 24U);
}

static jint read_be32(const jbyte *bytes, jint offset) {
    return (jint) (((uint32_t) (uint8_t) bytes[offset] << 24U)
            | ((uint32_t) (uint8_t) bytes[offset + 1] << 16U)
            | ((uint32_t) (uint8_t) bytes[offset + 2] << 8U)
            | ((uint32_t) (uint8_t) bytes[offset + 3]));
}

static void throw_illegal_state(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static jbyteArray load_resource_bytes(JNIEnv *env, jstring resource_name) {
    char bridge_name[] = {
            's','u','s','h','u','o','1','3','3','7','/',
            's','u','s','h','u','o','p','r','o','t','e','c','t','/',
            'l','i','b','/','N','a','t','i','v','e','B','r','i','d','g','e','\0'
    };
    jclass bridge = (*env)->FindClass(env, bridge_name);
    jclass class_cls = (*env)->FindClass(env, "java/lang/Class");
    if (bridge == NULL || class_cls == NULL) {
        return NULL;
    }
    jmethodID get_resource = (*env)->GetMethodID(env, class_cls, "getResourceAsStream",
            "(Ljava/lang/String;)Ljava/io/InputStream;");
    if (get_resource == NULL) {
        return NULL;
    }
    jobject stream = (*env)->CallObjectMethod(env, bridge, get_resource, resource_name);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    if (stream == NULL) {
        throw_illegal_state(env, "VM payload resource missing");
        return NULL;
    }

    jclass input_cls = (*env)->FindClass(env, "java/io/InputStream");
    jmethodID read_mid = input_cls == NULL ? NULL : (*env)->GetMethodID(env, input_cls, "read", "([B)I");
    jmethodID close_mid = input_cls == NULL ? NULL : (*env)->GetMethodID(env, input_cls, "close", "()V");
    jclass baos_cls = (*env)->FindClass(env, "java/io/ByteArrayOutputStream");
    jmethodID baos_ctor = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, "<init>", "()V");
    jmethodID write_mid = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, "write", "([BII)V");
    jmethodID to_byte_array_mid = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, "toByteArray", "()[B");
    if (read_mid == NULL || close_mid == NULL || baos_ctor == NULL || write_mid == NULL || to_byte_array_mid == NULL) {
        return NULL;
    }

    jobject output = (*env)->NewObject(env, baos_cls, baos_ctor);
    jbyteArray buffer = (*env)->NewByteArray(env, 4096);
    if (output == NULL || buffer == NULL) {
        return NULL;
    }
    for (;;) {
        jint read = (*env)->CallIntMethod(env, stream, read_mid, buffer);
        if ((*env)->ExceptionCheck(env)) {
            return NULL;
        }
        if (read < 0) {
            break;
        }
        if (read > 0) {
            (*env)->CallVoidMethod(env, output, write_mid, buffer, 0, read);
            if ((*env)->ExceptionCheck(env)) {
                return NULL;
            }
        }
    }
    (*env)->CallVoidMethod(env, stream, close_mid);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    return (jbyteArray) (*env)->CallObjectMethod(env, output, to_byte_array_mid);
}

static void release_reader_resource(JNIEnv *env, vm_code_reader *reader) {
    if (reader->resource_array != NULL && reader->resource_bytes != NULL) {
        (*env)->ReleaseByteArrayElements(env, reader->resource_array, reader->resource_bytes, JNI_ABORT);
        reader->resource_bytes = NULL;
    }
}

static uint8_t resource_payload_byte_at(vm_code_reader *reader, jint offset) {
    uint8_t encoded = (uint8_t) reader->resource_bytes[RESOURCE_HEADER_BYTES + offset];
    return (uint8_t) (encoded ^ resource_mask(reader->key, reader->resource_nonce,
            reader->resource_hash, reader->length, reader->resource_payload_len, offset));
}

static jbyte packed_byte_at(JNIEnv *env, vm_code_reader *reader, jint offset) {
    if (reader->resource_packed) {
        if (offset < 0 || offset >= reader->resource_code_bytes_len
                || RESOURCE_HEADER_BYTES + offset >= reader->resource_bytes_len) {
            jclass cls = (*env)->FindClass(env, "java/lang/ArrayIndexOutOfBoundsException");
            if (cls != NULL) {
                (*env)->ThrowNew(env, cls, "VM payload out of range");
            }
            return 0;
        }
        return (jbyte) resource_payload_byte_at(reader, offset);
    }
    jint chunk_index = offset / reader->chunk_bytes;
    jint chunk_offset = offset % reader->chunk_bytes;
    jbyteArray chunk = (jbyteArray) (*env)->GetObjectArrayElement(env, reader->chunks, chunk_index);
    if (chunk == NULL) {
        return 0;
    }
    jbyte value = 0;
    (*env)->GetByteArrayRegion(env, chunk, chunk_offset, 1, &value);
    (*env)->DeleteLocalRef(env, chunk);
    return value;
}

static jint decode_packed_code(JNIEnv *env, vm_code_reader *reader, jint index) {
    if (index < 0 || index >= reader->length || reader->chunk_bytes <= 0) {
        jclass cls = (*env)->FindClass(env, "java/lang/ArrayIndexOutOfBoundsException");
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, "VM pc out of range");
        }
        return 0;
    }
    jint offset = index * 4;
    uint32_t encoded = ((uint32_t) (uint8_t) packed_byte_at(env, reader, offset) << 24U)
            | ((uint32_t) (uint8_t) packed_byte_at(env, reader, offset + 1) << 16U)
            | ((uint32_t) (uint8_t) packed_byte_at(env, reader, offset + 2) << 8U)
            | ((uint32_t) (uint8_t) packed_byte_at(env, reader, offset + 3));
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    jint state = packed_stream_next(reader->key, reader->salt, index, reader->length);
    jint rotate = ((uint32_t) state >> 27U) & 15U;
    return (jint) (rotr32(encoded, (uint32_t) rotate) ^ (uint32_t) state ^ (uint32_t) index);
}

static jint read_vm_code(JNIEnv *env, vm_code_reader *reader, jint index) {
    if (reader->packed) {
        return decode_packed_code(env, reader, index);
    }
    return reader->plain[index];
}

static jint as_int(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Number");
    jmethodID mid = (*env)->GetMethodID(env, cls, "intValue", "()I");
    return (*env)->CallIntMethod(env, value, mid);
}

static jlong as_long(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Number");
    jmethodID mid = (*env)->GetMethodID(env, cls, "longValue", "()J");
    return (*env)->CallLongMethod(env, value, mid);
}

static jfloat as_float(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Number");
    jmethodID mid = (*env)->GetMethodID(env, cls, "floatValue", "()F");
    return (*env)->CallFloatMethod(env, value, mid);
}

static jdouble as_double(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Number");
    jmethodID mid = (*env)->GetMethodID(env, cls, "doubleValue", "()D");
    return (*env)->CallDoubleMethod(env, value, mid);
}

static jboolean as_boolean(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Boolean");
    jmethodID mid = (*env)->GetMethodID(env, cls, "booleanValue", "()Z");
    return (*env)->CallBooleanMethod(env, value, mid);
}

static jchar as_char(JNIEnv *env, jobject value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Character");
    jmethodID mid = (*env)->GetMethodID(env, cls, "charValue", "()C");
    return (*env)->CallCharMethod(env, value, mid);
}

static jobject box_int(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(I)Ljava/lang/Integer;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_boolean(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Boolean");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(Z)Ljava/lang/Boolean;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value != 0 ? JNI_TRUE : JNI_FALSE);
}

static jobject box_byte(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Byte");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(B)Ljava/lang/Byte;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jbyte) value);
}

static jobject box_short(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Short");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(S)Ljava/lang/Short;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jshort) value);
}

static jobject box_char(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Character");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(C)Ljava/lang/Character;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jchar) value);
}

static jobject box_long(JNIEnv *env, jlong value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Long");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(J)Ljava/lang/Long;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_float(JNIEnv *env, jfloat value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Float");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(F)Ljava/lang/Float;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_double(JNIEnv *env, jdouble value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Double");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(D)Ljava/lang/Double;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jint compare_float(jfloat left, jfloat right, jint nan_value) {
    if (left != left || right != right) {
        return nan_value;
    }
    return left > right ? 1 : (left < right ? -1 : 0);
}

static jint compare_double(jdouble left, jdouble right, jint nan_value) {
    if (left != left || right != right) {
        return nan_value;
    }
    return left > right ? 1 : (left < right ? -1 : 0);
}

static void throw_unsupported(JNIEnv *env) {
    jclass cls = (*env)->FindClass(env, "java/lang/UnsupportedOperationException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, "native VM fallback");
    }
}

static jclass primitive_class(JNIEnv *env, char kind) {
    const char *class_name = NULL;
    const char *field_sig = "Ljava/lang/Class;";
    switch (kind) {
        case 'V':
            class_name = "java/lang/Void";
            break;
        case 'Z':
            class_name = "java/lang/Boolean";
            break;
        case 'C':
            class_name = "java/lang/Character";
            break;
        case 'B':
            class_name = "java/lang/Byte";
            break;
        case 'S':
            class_name = "java/lang/Short";
            break;
        case 'I':
            class_name = "java/lang/Integer";
            break;
        case 'J':
            class_name = "java/lang/Long";
            break;
        case 'F':
            class_name = "java/lang/Float";
            break;
        case 'D':
            class_name = "java/lang/Double";
            break;
        default:
            return NULL;
    }
    jclass wrapper = (*env)->FindClass(env, class_name);
    if (wrapper == NULL) {
        return NULL;
    }
    jfieldID type_field = (*env)->GetStaticFieldID(env, wrapper, "TYPE", field_sig);
    if (type_field == NULL) {
        return NULL;
    }
    return (jclass) (*env)->GetStaticObjectField(env, wrapper, type_field);
}

static char *copy_descriptor_name(const char *descriptor, int start, int end) {
    int len = end - start;
    char *name = (char *) malloc((size_t) len + 1U);
    if (name == NULL) {
        return NULL;
    }
    memcpy(name, descriptor + start, (size_t) len);
    name[len] = '\0';
    for (int i = 0; i < len; i++) {
        if (name[i] == '/') {
            name[i] = '.';
        }
    }
    return name;
}

static char *internal_to_binary(const char *internal_name) {
    int len = (int) strlen(internal_name);
    char *name = (char *) malloc((size_t) len + 1U);
    if (name == NULL) {
        return NULL;
    }
    memcpy(name, internal_name, (size_t) len + 1U);
    for (int i = 0; i < len; i++) {
        if (name[i] == '/') {
            name[i] = '.';
        }
    }
    return name;
}

static jclass class_for_name(JNIEnv *env, const char *name) {
    jclass cls_class = (*env)->FindClass(env, "java/lang/Class");
    if (cls_class == NULL) {
        return NULL;
    }
    jmethodID for_name = (*env)->GetStaticMethodID(env, cls_class, "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (for_name == NULL) {
        return NULL;
    }
    jstring java_name = (*env)->NewStringUTF(env, name);
    if (java_name == NULL) {
        return NULL;
    }
    return (jclass) (*env)->CallStaticObjectMethod(env, cls_class, for_name, java_name);
}

static int next_type_index(const char *descriptor, int index) {
    int cursor = index;
    while (descriptor[cursor] == '[') {
        cursor++;
    }
    if (descriptor[cursor] == 'L') {
        while (descriptor[cursor] != ';' && descriptor[cursor] != '\0') {
            cursor++;
        }
    }
    return cursor + 1;
}

static int parameter_count(const char *descriptor) {
    int count = 0;
    int index = 1;
    while (descriptor[index] != ')' && descriptor[index] != '\0') {
        count++;
        index = next_type_index(descriptor, index);
    }
    return count;
}

static jobjectArray parameter_classes(JNIEnv *env, const char *descriptor, char *kinds, int count) {
    jclass class_cls = (*env)->FindClass(env, "java/lang/Class");
    if (class_cls == NULL) {
        return NULL;
    }
    jobjectArray result = (*env)->NewObjectArray(env, count, class_cls, NULL);
    if (result == NULL) {
        return NULL;
    }
    int index = 1;
    for (int i = 0; i < count; i++) {
        int start = index;
        char kind = descriptor[index];
        kinds[i] = kind;
        jclass param = NULL;
        if (kind == '[') {
            int end = next_type_index(descriptor, index);
            char *name = copy_descriptor_name(descriptor, start, end);
            if (name == NULL) {
                return NULL;
            }
            param = class_for_name(env, name);
            free(name);
            index = end;
        } else if (kind == 'L') {
            int end = next_type_index(descriptor, index);
            char *name = copy_descriptor_name(descriptor, start + 1, end - 1);
            if (name == NULL) {
                return NULL;
            }
            param = class_for_name(env, name);
            free(name);
            index = end;
        } else {
            param = primitive_class(env, kind);
            index++;
        }
        if (param == NULL || (*env)->ExceptionCheck(env)) {
            return NULL;
        }
        (*env)->SetObjectArrayElement(env, result, i, param);
    }
    return result;
}

static jobject coerce_arg(JNIEnv *env, char kind, jobject value) {
    switch (kind) {
        case 'Z':
            return box_boolean(env, as_int(env, value));
        case 'B':
            return box_byte(env, as_int(env, value));
        case 'S':
            return box_short(env, as_int(env, value));
        case 'C':
            return box_char(env, as_int(env, value));
        default:
            return value;
    }
}

static char return_kind(const char *descriptor) {
    const char *end = strchr(descriptor, ')');
    return end == NULL ? 'V' : end[1];
}

static jclass descriptor_class(JNIEnv *env, const char *descriptor) {
    if (descriptor[0] == '[') {
        char *name = internal_to_binary(descriptor);
        if (name == NULL) {
            return NULL;
        }
        jclass cls = class_for_name(env, name);
        free(name);
        return cls;
    }
    if (descriptor[0] == 'L') {
        int end = next_type_index(descriptor, 0);
        char *name = copy_descriptor_name(descriptor, 1, end - 1);
        if (name == NULL) {
            return NULL;
        }
        jclass cls = class_for_name(env, name);
        free(name);
        return cls;
    }
    return primitive_class(env, descriptor[0]);
}

static jobject normalize_return(JNIEnv *env, char kind, jobject value) {
    switch (kind) {
        case 'V':
            return NULL;
        case 'Z':
            return box_int(env, value == NULL ? 0 : (as_boolean(env, value) ? 1 : 0));
        case 'C':
            return box_int(env, value == NULL ? 0 : (jint) as_char(env, value));
        case 'B':
        case 'S':
            return box_int(env, value == NULL ? 0 : as_int(env, value));
        default:
            return value;
    }
}

static jobject coerce_value(JNIEnv *env, char kind, jobject value) {
    switch (kind) {
        case 'Z':
            return box_boolean(env, as_int(env, value));
        case 'B':
            return box_byte(env, as_int(env, value));
        case 'S':
            return box_short(env, as_int(env, value));
        case 'C':
            return box_char(env, as_int(env, value));
        default:
            return value;
    }
}

static jobject find_declared_member(JNIEnv *env, jclass start_class, jobject name_string,
                                    jobjectArray parameter_types, jint method) {
    jclass class_cls = (*env)->FindClass(env, "java/lang/Class");
    jmethodID get_superclass = (*env)->GetMethodID(env, class_cls, "getSuperclass", "()Ljava/lang/Class;");
    jmethodID getter = method
            ? (*env)->GetMethodID(env, class_cls, "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")
            : (*env)->GetMethodID(env, class_cls, "getDeclaredField",
                    "(Ljava/lang/String;)Ljava/lang/reflect/Field;");
    jobject cursor = start_class;
    jobject member = NULL;
    jclass no_such_method = (*env)->FindClass(env, "java/lang/NoSuchMethodException");
    jclass no_such_field = (*env)->FindClass(env, "java/lang/NoSuchFieldException");

    while (cursor != NULL && !(*env)->ExceptionCheck(env)) {
        if (method) {
            member = (*env)->CallObjectMethod(env, cursor, getter, name_string, parameter_types);
        } else {
            member = (*env)->CallObjectMethod(env, cursor, getter, name_string);
        }
        if (!(*env)->ExceptionCheck(env)) {
            return member;
        }
        jthrowable thrown = (*env)->ExceptionOccurred(env);
        (*env)->ExceptionClear(env);
        if ((method && no_such_method != NULL && (*env)->IsInstanceOf(env, thrown, no_such_method))
                || (!method && no_such_field != NULL && (*env)->IsInstanceOf(env, thrown, no_such_field))) {
            cursor = (*env)->CallObjectMethod(env, cursor, get_superclass);
            continue;
        }
        (*env)->Throw(env, thrown);
        return NULL;
    }
    return NULL;
}

static jclass owner_class_from_constants(JNIEnv *env, jobjectArray constants, jint owner_index) {
    jstring owner_string = (jstring) (*env)->GetObjectArrayElement(env, constants, owner_index);
    const char *owner_utf = (*env)->GetStringUTFChars(env, owner_string, NULL);
    if (owner_utf == NULL) {
        return NULL;
    }
    char *owner_name = internal_to_binary(owner_utf);
    (*env)->ReleaseStringUTFChars(env, owner_string, owner_utf);
    if (owner_name == NULL) {
        return NULL;
    }
    jclass owner_class = class_for_name(env, owner_name);
    free(owner_name);
    return owner_class;
}

static jobject invoke_reflect(JNIEnv *env, jobjectArray constants, jint owner_index, jint name_index,
                              jint descriptor_index, jobjectArray call_args, jint argc,
                              jobject target, jint invoke_opcode, jint *push_result) {
    (void) invoke_opcode;
    jstring name_string = (jstring) (*env)->GetObjectArrayElement(env, constants, name_index);
    jstring descriptor_string = (jstring) (*env)->GetObjectArrayElement(env, constants, descriptor_index);
    const char *descriptor_utf = (*env)->GetStringUTFChars(env, descriptor_string, NULL);
    if (descriptor_utf == NULL) {
        return NULL;
    }

    int count = parameter_count(descriptor_utf);
    char *kinds = (char *) calloc((size_t) count, sizeof(char));
    jobjectArray parameter_types = parameter_classes(env, descriptor_utf, kinds, count);
    jclass owner_class = owner_class_from_constants(env, constants, owner_index);
    jobject result = NULL;
    *push_result = return_kind(descriptor_utf) != 'V';

    if (parameter_types != NULL && owner_class != NULL && !(*env)->ExceptionCheck(env)) {
        jobject method = find_declared_member(env, owner_class, name_string, parameter_types, JNI_TRUE);
        if (method != NULL && !(*env)->ExceptionCheck(env)) {
            jclass method_cls = (*env)->FindClass(env, "java/lang/reflect/Method");
            jmethodID set_accessible = (*env)->GetMethodID(env, method_cls, "setAccessible", "(Z)V");
            jmethodID invoke = (*env)->GetMethodID(env, method_cls, "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
            jobjectArray coerced_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, "java/lang/Object"), NULL);
            for (jint i = 0; i < argc; i++) {
                jobject arg = (*env)->GetObjectArrayElement(env, call_args, i);
                jobject coerced = coerce_arg(env, kinds[i], arg);
                (*env)->SetObjectArrayElement(env, coerced_args, i, coerced);
            }
            if (!(*env)->ExceptionCheck(env)) {
                (*env)->CallVoidMethod(env, method, set_accessible, JNI_TRUE);
            }
            if (!(*env)->ExceptionCheck(env)) {
                jobject raw = (*env)->CallObjectMethod(env, method, invoke, target, coerced_args);
                if (!(*env)->ExceptionCheck(env)) {
                    result = normalize_return(env, return_kind(descriptor_utf), raw);
                }
            }
        }
    }

    free(kinds);
    (*env)->ReleaseStringUTFChars(env, descriptor_string, descriptor_utf);
    return result;
}

static jobject access_field(JNIEnv *env, jobjectArray constants, jint owner_index, jint name_index,
                            jint descriptor_index, jobject target, jobject value, jint write) {
    jstring name_string = (jstring) (*env)->GetObjectArrayElement(env, constants, name_index);
    jstring descriptor_string = (jstring) (*env)->GetObjectArrayElement(env, constants, descriptor_index);
    const char *descriptor_utf = (*env)->GetStringUTFChars(env, descriptor_string, NULL);
    if (descriptor_utf == NULL) {
        return NULL;
    }

    jclass owner_class = owner_class_from_constants(env, constants, owner_index);
    jobject result = NULL;
    if (owner_class != NULL && !(*env)->ExceptionCheck(env)) {
        jobject field = find_declared_member(env, owner_class, name_string, NULL, JNI_FALSE);
        if (field != NULL && !(*env)->ExceptionCheck(env)) {
            jclass field_cls = (*env)->FindClass(env, "java/lang/reflect/Field");
            jmethodID set_accessible = (*env)->GetMethodID(env, field_cls, "setAccessible", "(Z)V");
            if (!(*env)->ExceptionCheck(env)) {
                (*env)->CallVoidMethod(env, field, set_accessible, JNI_TRUE);
            }
            if (write) {
                jmethodID set = (*env)->GetMethodID(env, field_cls, "set",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V");
                jobject coerced = coerce_value(env, descriptor_utf[0], value);
                if (!(*env)->ExceptionCheck(env)) {
                    (*env)->CallVoidMethod(env, field, set, target, coerced);
                }
            } else {
                jmethodID get = (*env)->GetMethodID(env, field_cls, "get",
                        "(Ljava/lang/Object;)Ljava/lang/Object;");
                jobject raw = NULL;
                if (!(*env)->ExceptionCheck(env)) {
                    raw = (*env)->CallObjectMethod(env, field, get, target);
                }
                if (!(*env)->ExceptionCheck(env)) {
                    result = normalize_return(env, descriptor_utf[0], raw);
                }
            }
        }
    }

    (*env)->ReleaseStringUTFChars(env, descriptor_string, descriptor_utf);
    return result;
}

static jclass type_from_constant(JNIEnv *env, jobjectArray constants, jint type_index) {
    jstring type_string = (jstring) (*env)->GetObjectArrayElement(env, constants, type_index);
    const char *type_utf = (*env)->GetStringUTFChars(env, type_string, NULL);
    if (type_utf == NULL) {
        return NULL;
    }
    char *type_name = internal_to_binary(type_utf);
    (*env)->ReleaseStringUTFChars(env, type_string, type_utf);
    if (type_name == NULL) {
        return NULL;
    }
    jclass cls = class_for_name(env, type_name);
    free(type_name);
    return cls;
}

static jint resource_read_i32(JNIEnv *env, vm_code_reader *reader, jint *cursor) {
    if (*cursor < 0 || *cursor + 4 > reader->resource_payload_len) {
        throw_illegal_state(env, "VM payload metadata truncated");
        return 0;
    }
    uint32_t value = ((uint32_t) resource_payload_byte_at(reader, *cursor) << 24U)
            | ((uint32_t) resource_payload_byte_at(reader, *cursor + 1) << 16U)
            | ((uint32_t) resource_payload_byte_at(reader, *cursor + 2) << 8U)
            | (uint32_t) resource_payload_byte_at(reader, *cursor + 3);
    *cursor += 4;
    return (jint) value;
}

static jlong resource_read_i64(JNIEnv *env, vm_code_reader *reader, jint *cursor) {
    uint32_t high = (uint32_t) resource_read_i32(env, reader, cursor);
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    uint32_t low = (uint32_t) resource_read_i32(env, reader, cursor);
    return (jlong) (((uint64_t) high << 32U) | (uint64_t) low);
}

static jint resource_read_u8(JNIEnv *env, vm_code_reader *reader, jint *cursor) {
    if (*cursor < 0 || *cursor >= reader->resource_payload_len) {
        throw_illegal_state(env, "VM payload metadata truncated");
        return 0;
    }
    uint8_t value = resource_payload_byte_at(reader, *cursor);
    *cursor += 1;
    return (jint) value;
}

static jobject new_string_utf8(JNIEnv *env, const jbyte *data, jint length) {
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    if (bytes == NULL) {
        return NULL;
    }
    if (length > 0) {
        (*env)->SetByteArrayRegion(env, bytes, 0, length, data);
        if ((*env)->ExceptionCheck(env)) {
            return NULL;
        }
    }
    jclass string_cls = (*env)->FindClass(env, "java/lang/String");
    jclass charset_cls = (*env)->FindClass(env, "java/nio/charset/StandardCharsets");
    if (string_cls == NULL || charset_cls == NULL) {
        return NULL;
    }
    jfieldID utf8_field = (*env)->GetStaticFieldID(env, charset_cls, "UTF_8", "Ljava/nio/charset/Charset;");
    jmethodID ctor = (*env)->GetMethodID(env, string_cls, "<init>", "([BLjava/nio/charset/Charset;)V");
    if (utf8_field == NULL || ctor == NULL) {
        return NULL;
    }
    jobject utf8 = (*env)->GetStaticObjectField(env, charset_cls, utf8_field);
    return (*env)->NewObject(env, string_cls, ctor, bytes, utf8);
}

static jint guard_token_utf(const char *owner, const char *method, jint site) {
    uint32_t value = (uint32_t) SEAL_SALT ^ (uint32_t) java_hash_utf(owner);
    value ^= rotl32((uint32_t) site * 0x45D9F3BU, 7U);
    value = rotl32(value + 0x7F4A7C15U, 11U);
    value ^= (uint32_t) java_hash_utf(method) * 0x5BD1E995U;
    value ^= value >> 16U;
    value *= 0x85EBCA6BU;
    value ^= value >> 13U;
    value *= 0xC2B2AE35U;
    value ^= value >> 16U;
    return value == 0U ? (jint) 0x2468ACE1U : (jint) value;
}

static uint16_t seal_mask(jint token, jint site, jint index) {
    uint32_t value = (uint32_t) token ^ rotl32((uint32_t) site * 0x27D4EB2DU, 9U);
    value ^= (uint32_t) index * 0x9E3779B9U;
    value = rotl32(value + 0x165667B1U, 7U);
    value ^= value >> 15U;
    value *= 0x85EBCA6BU;
    value ^= value >> 13U;
    return (uint16_t) value;
}

static int should_skip_frame(const char *owner, const char *method) {
    if (strcmp(owner, "java.lang.Thread") == 0) {
        return 1;
    }
    if (strcmp(method, "getStackTrace") == 0 || strcmp(method, "_v") == 0 || strcmp(method, "_n") == 0) {
        return 1;
    }
    if (method[0] == '_' && method[1] == 'v' && method[2] == 'p' && method[3] == '$') {
        return 1;
    }
    return 0;
}

static jint current_call_token(JNIEnv *env, jint site) {
    jclass thread_cls = (*env)->FindClass(env, "java/lang/Thread");
    jclass ste_cls = (*env)->FindClass(env, "java/lang/StackTraceElement");
    if (thread_cls == NULL || ste_cls == NULL) {
        return 0;
    }
    jmethodID current_thread = (*env)->GetStaticMethodID(env, thread_cls, "currentThread", "()Ljava/lang/Thread;");
    jmethodID get_stack = (*env)->GetMethodID(env, thread_cls, "getStackTrace", "()[Ljava/lang/StackTraceElement;");
    jmethodID get_class_name = (*env)->GetMethodID(env, ste_cls, "getClassName", "()Ljava/lang/String;");
    jmethodID get_method_name = (*env)->GetMethodID(env, ste_cls, "getMethodName", "()Ljava/lang/String;");
    if (current_thread == NULL || get_stack == NULL || get_class_name == NULL || get_method_name == NULL) {
        return 0;
    }
    jobject thread = (*env)->CallStaticObjectMethod(env, thread_cls, current_thread);
    jobjectArray trace = thread == NULL ? NULL : (jobjectArray) (*env)->CallObjectMethod(env, thread, get_stack);
    if (trace == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jsize count = (*env)->GetArrayLength(env, trace);
    for (jsize i = 0; i < count; i++) {
        jobject element = (*env)->GetObjectArrayElement(env, trace, i);
        if (element == NULL) {
            continue;
        }
        jstring owner_string = (jstring) (*env)->CallObjectMethod(env, element, get_class_name);
        jstring method_string = (jstring) (*env)->CallObjectMethod(env, element, get_method_name);
        if (owner_string == NULL || method_string == NULL || (*env)->ExceptionCheck(env)) {
            return 0;
        }
        const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
        const char *method = (*env)->GetStringUTFChars(env, method_string, NULL);
        if (owner == NULL || method == NULL) {
            return 0;
        }
        int skip = should_skip_frame(owner, method);
        jint token = 0;
        if (!skip) {
            token = guard_token_utf(owner, method, site);
        }
        (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        (*env)->ReleaseStringUTFChars(env, method_string, method);
        if (!skip) {
            return token;
        }
    }
    throw_illegal_state(env, "VM call context missing");
    return 0;
}

static jstring unseal_resource_name(JNIEnv *env, jstring sealed, jint token, jint site) {
    jsize len = (*env)->GetStringLength(env, sealed);
    const jchar *raw = (*env)->GetStringChars(env, sealed, NULL);
    if (raw == NULL) {
        return NULL;
    }
    jchar *plain = (jchar *) malloc((size_t) len * sizeof(jchar));
    if (plain == NULL) {
        (*env)->ReleaseStringChars(env, sealed, raw);
        return NULL;
    }
    for (jsize i = 0; i < len; i++) {
        plain[i] = (jchar) (raw[i] ^ seal_mask(token, site, (jint) i));
    }
    (*env)->ReleaseStringChars(env, sealed, raw);
    jstring result = (*env)->NewString(env, plain, len);
    free(plain);
    return result;
}

static jint *decode_resource_map(JNIEnv *env, vm_code_reader *reader, jint *cursor, jsize *out_len) {
    jint map_len = resource_read_i32(env, reader, cursor);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    if (map_len < 0 || map_len > 4096 || *cursor + map_len * 4 > reader->resource_payload_len) {
        throw_illegal_state(env, "VM opcode map metadata invalid");
        return NULL;
    }
    jint *encoded = (jint *) malloc((size_t) map_len * sizeof(jint));
    if (encoded == NULL) {
        return NULL;
    }
    for (jint i = 0; i < map_len; i++) {
        encoded[i] = resource_read_i32(env, reader, cursor);
        if ((*env)->ExceptionCheck(env)) {
            free(encoded);
            return NULL;
        }
    }
    jint *decoded = decode_int_words(encoded, map_len, reader->key, MAP_SALT);
    free(encoded);
    if (decoded != NULL) {
        *out_len = (jsize) map_len;
    }
    return decoded;
}

static jobjectArray decode_resource_constants(JNIEnv *env, vm_code_reader *reader, jint *cursor) {
    jint count = resource_read_i32(env, reader, cursor);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    if (count < 0 || count > 65535) {
        throw_illegal_state(env, "VM constant metadata invalid");
        return NULL;
    }
    jclass object_cls = (*env)->FindClass(env, "java/lang/Object");
    if (object_cls == NULL) {
        return NULL;
    }
    jobjectArray constants = (*env)->NewObjectArray(env, count, object_cls, NULL);
    if (constants == NULL) {
        return NULL;
    }
    for (jint i = 0; i < count; i++) {
        jint tag = resource_read_u8(env, reader, cursor);
        if ((*env)->ExceptionCheck(env)) {
            return NULL;
        }
        jobject value = NULL;
        switch (tag) {
            case RESOURCE_CONSTANT_NULL:
                value = NULL;
                break;
            case RESOURCE_CONSTANT_INT:
                value = box_int(env, resource_read_i32(env, reader, cursor));
                break;
            case RESOURCE_CONSTANT_LONG:
                value = box_long(env, resource_read_i64(env, reader, cursor));
                break;
            case RESOURCE_CONSTANT_FLOAT: {
                jint bits = resource_read_i32(env, reader, cursor);
                union {
                    uint32_t i;
                    jfloat f;
                } u;
                u.i = (uint32_t) bits;
                value = box_float(env, u.f);
                break;
            }
            case RESOURCE_CONSTANT_DOUBLE: {
                jlong bits = resource_read_i64(env, reader, cursor);
                union {
                    uint64_t i;
                    jdouble d;
                } u;
                u.i = (uint64_t) bits;
                value = box_double(env, u.d);
                break;
            }
            case RESOURCE_CONSTANT_STRING: {
                jint length = resource_read_i32(env, reader, cursor);
                if ((*env)->ExceptionCheck(env)) {
                    return NULL;
                }
                if (length < 0 || *cursor + length > reader->resource_payload_len) {
                    throw_illegal_state(env, "VM string metadata invalid");
                    return NULL;
                }
                jbyte *bytes = length == 0 ? NULL : (jbyte *) malloc((size_t) length);
                if (length != 0 && bytes == NULL) {
                    return NULL;
                }
                for (jint j = 0; j < length; j++) {
                    bytes[j] = (jbyte) resource_payload_byte_at(reader, *cursor + j);
                }
                *cursor += length;
                value = new_string_utf8(env, bytes, length);
                free(bytes);
                break;
            }
            default:
                throw_illegal_state(env, "VM constant tag invalid");
                return NULL;
        }
        if ((*env)->ExceptionCheck(env)) {
            return NULL;
        }
        if (value != NULL) {
            (*env)->SetObjectArrayElement(env, constants, i, value);
            if ((*env)->ExceptionCheck(env)) {
                return NULL;
            }
        }
    }
    return constants;
}

static jobject SUSHUO_CALL sushuo_native_vm(
        JNIEnv *env, jclass ignored, jobjectArray program, jobjectArray args) {
    (void) ignored;

    jobject first = (*env)->GetObjectArrayElement(env, program, 0);
    jint marker = first != NULL ? as_int(env, first) : 0;
    jint encoded = marker == ENCODED_MARKER;
    jint resource_packed = marker == RESOURCE_MARKER;
    jint packed = marker == PACKED_MARKER || resource_packed;
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }

    jint max_locals;
    jintArray code_array = NULL;
    jobjectArray constants;
    jint *owned_code = NULL;
    jint *owned_reverse = NULL;
    jint *code = NULL;
    jsize code_len = 0;
    vm_code_reader reader;
    memset(&reader, 0, sizeof(reader));
    reader.salt = CODE_SALT;

    if (packed) {
        jobject max_locals_obj = (*env)->GetObjectArrayElement(env, program, 1);
        max_locals = as_int(env, max_locals_obj);
        jobject key_obj = (*env)->GetObjectArrayElement(env, program, 4);
        jobject code_len_obj = (*env)->GetObjectArrayElement(env, program, 5);
        jobject chunk_bytes_obj = (*env)->GetObjectArrayElement(env, program, 6);
        jint key = as_int(env, key_obj);
        code_len = as_int(env, code_len_obj);
        jint chunk_bytes = as_int(env, chunk_bytes_obj);
        if (code_len < 0 || chunk_bytes <= 0) {
            return NULL;
        }
        jobject payload = (*env)->GetObjectArrayElement(env, program, 7);
        constants = NULL;
        reader.key = key;
        reader.length = (jint) code_len;
        reader.chunk_bytes = chunk_bytes;
        reader.packed = JNI_TRUE;
        jsize map_len = 0;
        jint *map = NULL;
        if (resource_packed) {
            jobject site_obj = (*env)->GetObjectArrayElement(env, program, 8);
            jint site = as_int(env, site_obj);
            jint token = current_call_token(env, site);
            if ((*env)->ExceptionCheck(env)) {
                return NULL;
            }
            key = key ^ token ^ SEAL_SALT;
            reader.key = key;
            jstring resource_name = unseal_resource_name(env, (jstring) payload, token, site);
            if (resource_name == NULL || (*env)->ExceptionCheck(env)) {
                return NULL;
            }
            const char *resource_utf = (*env)->GetStringUTFChars(env, resource_name, NULL);
            if (resource_utf == NULL) {
                return NULL;
            }
            jint resource_hash = java_hash_utf(resource_utf);
            (*env)->ReleaseStringUTFChars(env, resource_name, resource_utf);

            jbyteArray resource_array = load_resource_bytes(env, resource_name);
            if (resource_array == NULL || (*env)->ExceptionCheck(env)) {
                return NULL;
            }
            jsize resource_len = (*env)->GetArrayLength(env, resource_array);
            jbyte *resource_bytes = (*env)->GetByteArrayElements(env, resource_array, NULL);
            if (resource_bytes == NULL) {
                return NULL;
            }
            if (resource_len < RESOURCE_HEADER_BYTES) {
                (*env)->ReleaseByteArrayElements(env, resource_array, resource_bytes, JNI_ABORT);
                throw_illegal_state(env, "VM payload header truncated");
                return NULL;
            }
            jint magic = read_be32(resource_bytes, 0);
            jint version = read_be32(resource_bytes, 4);
            jint nonce = read_be32(resource_bytes, 8);
            jint key_tag = read_be32(resource_bytes, 12);
            jint resource_code_len = read_be32(resource_bytes, 16);
            jint payload_len = read_be32(resource_bytes, 20);
            jint resource_key = key_tag ^ resource_hash ^ RESOURCE_MAGIC ^ nonce;
            jint code_bytes_len = code_len * 4;
            if (magic != RESOURCE_MAGIC || (version != 1 && version != RESOURCE_VERSION)
                    || resource_key != key || resource_code_len != code_len
                    || payload_len < code_bytes_len
                    || payload_len < 0 || payload_len > resource_len - RESOURCE_HEADER_BYTES) {
                (*env)->ReleaseByteArrayElements(env, resource_array, resource_bytes, JNI_ABORT);
                throw_illegal_state(env, "VM payload integrity check failed");
                return NULL;
            }
            reader.resource_array = resource_array;
            reader.resource_bytes = resource_bytes;
            reader.resource_bytes_len = (jint) resource_len;
            reader.resource_payload_len = payload_len;
            reader.resource_code_bytes_len = code_bytes_len;
            reader.resource_nonce = nonce;
            reader.resource_hash = resource_hash;
            reader.resource_version = version;
            reader.resource_packed = JNI_TRUE;
            if (version == RESOURCE_VERSION) {
                jint cursor = code_bytes_len;
                map = decode_resource_map(env, &reader, &cursor, &map_len);
                if (map == NULL || (*env)->ExceptionCheck(env)) {
                    free(map);
                    release_reader_resource(env, &reader);
                    return NULL;
                }
                constants = decode_resource_constants(env, &reader, &cursor);
                if (constants == NULL || (*env)->ExceptionCheck(env)) {
                    free(map);
                    release_reader_resource(env, &reader);
                    return NULL;
                }
            }
        } else {
            reader.chunks = (jobjectArray) payload;
        }
        if (map == NULL) {
            constants = (jobjectArray) (*env)->GetObjectArrayElement(env, program, 8);
            jintArray map_array = (jintArray) (*env)->GetObjectArrayElement(env, program, 9);
            map = decode_int_array(env, map_array, key, MAP_SALT, &map_len);
        }
        if (map == NULL || code_len < 0 || chunk_bytes <= 0) {
            free(map);
            release_reader_resource(env, &reader);
            return NULL;
        }
        owned_reverse = (jint *) calloc(128, sizeof(jint));
        if (owned_reverse == NULL) {
            free(map);
            release_reader_resource(env, &reader);
            return NULL;
        }
        for (jsize i = 0; i < map_len; i++) {
            jint physical = map[i];
            if (physical >= 0 && physical < 128) {
                owned_reverse[physical] = (jint) i;
            }
        }
        free(map);
    } else if (encoded) {
        jobject max_locals_obj = (*env)->GetObjectArrayElement(env, program, 1);
        max_locals = as_int(env, max_locals_obj);
        jobject key_obj = (*env)->GetObjectArrayElement(env, program, 7);
        jint key = as_int(env, key_obj);
        code_array = (jintArray) (*env)->GetObjectArrayElement(env, program, 4);
        constants = (jobjectArray) (*env)->GetObjectArrayElement(env, program, 5);
        owned_code = decode_int_array(env, code_array, key, CODE_SALT, &code_len);
        if (owned_code == NULL) {
            return NULL;
        }
        jintArray map_array = (jintArray) (*env)->GetObjectArrayElement(env, program, 8);
        jsize map_len = 0;
        jint *map = decode_int_array(env, map_array, key, MAP_SALT, &map_len);
        if (map == NULL) {
            free(owned_code);
            return NULL;
        }
        owned_reverse = (jint *) calloc(128, sizeof(jint));
        if (owned_reverse == NULL) {
            free(map);
            free(owned_code);
            return NULL;
        }
        for (jsize i = 0; i < map_len; i++) {
            jint physical = map[i];
            if (physical >= 0 && physical < 128) {
                owned_reverse[physical] = (jint) i;
            }
        }
        free(map);
        code = owned_code;
        reader.plain = code;
        reader.length = (jint) code_len;
    } else {
        jobject max_locals_obj = (*env)->GetObjectArrayElement(env, program, 0);
        max_locals = as_int(env, max_locals_obj);
        code_array = (jintArray) (*env)->GetObjectArrayElement(env, program, 3);
        constants = (jobjectArray) (*env)->GetObjectArrayElement(env, program, 4);
        code_len = (*env)->GetArrayLength(env, code_array);
        code = (*env)->GetIntArrayElements(env, code_array, NULL);
        if (code == NULL) {
            return NULL;
        }
        reader.plain = code;
        reader.length = (jint) code_len;
    }

    jsize arg_len = (*env)->GetArrayLength(env, args);
    jsize local_len = max_locals > arg_len ? max_locals : arg_len;

    jobject *locals = (jobject *) calloc((size_t) local_len, sizeof(jobject));
    jobject *stack = (jobject *) calloc((size_t) (code_len + 8), sizeof(jobject));
    if (locals == NULL || stack == NULL) {
        free(locals);
        free(stack);
        free(owned_code);
        free(owned_reverse);
        release_reader_resource(env, &reader);
        if (!encoded && !packed && code_array != NULL && code != NULL) {
            (*env)->ReleaseIntArrayElements(env, code_array, code, JNI_ABORT);
        }
        return NULL;
    }

    for (jsize i = 0; i < arg_len; i++) {
        locals[i] = (*env)->GetObjectArrayElement(env, args, i);
    }

    jint pc = 0;
    jint sp = 0;
    jobject result = NULL;
#define VM_AT(index) read_vm_code(env, &reader, (jint) (index))
#define VM_NEXT() read_vm_code(env, &reader, pc++)
    while (pc < code_len) {
        jint op = VM_NEXT();
        if (owned_reverse != NULL && op >= 0 && op < 128) {
            op = owned_reverse[op];
        }
        switch (op) {
            case PUSH_CONST:
                stack[sp++] = (*env)->GetObjectArrayElement(env, constants, VM_NEXT());
                break;
            case LOAD:
                stack[sp++] = locals[VM_NEXT()];
                break;
            case STORE:
                locals[VM_NEXT()] = stack[--sp];
                break;
            case POP:
                sp--;
                break;
            case DUP:
                stack[sp] = stack[sp - 1];
                sp++;
                break;
            case IADD:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) + as_int(env, stack[sp - 1]));
                sp--;
                break;
            case ISUB:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) - as_int(env, stack[sp - 1]));
                sp--;
                break;
            case IMUL:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) * as_int(env, stack[sp - 1]));
                sp--;
                break;
            case IDIV:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) / as_int(env, stack[sp - 1]));
                sp--;
                break;
            case IREM:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) % as_int(env, stack[sp - 1]));
                sp--;
                break;
            case INEG:
                stack[sp - 1] = box_int(env, -as_int(env, stack[sp - 1]));
                break;
            case IXOR:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) ^ as_int(env, stack[sp - 1]));
                sp--;
                break;
            case IAND:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) & as_int(env, stack[sp - 1]));
                sp--;
                break;
            case IOR:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) | as_int(env, stack[sp - 1]));
                sp--;
                break;
            case ISHL:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) << (as_int(env, stack[sp - 1]) & 31));
                sp--;
                break;
            case ISHR:
                stack[sp - 2] = box_int(env, as_int(env, stack[sp - 2]) >> (as_int(env, stack[sp - 1]) & 31));
                sp--;
                break;
            case IUSHR:
                stack[sp - 2] = box_int(env, (jint) (((uint32_t) as_int(env, stack[sp - 2])) >> (as_int(env, stack[sp - 1]) & 31)));
                sp--;
                break;
            case LADD:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) + as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LSUB:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) - as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LMUL:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) * as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LDIV:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) / as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LREM:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) % as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LNEG:
                stack[sp - 1] = box_long(env, -as_long(env, stack[sp - 1]));
                break;
            case LXOR:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) ^ as_long(env, stack[sp - 1]));
                sp--;
                break;
            case LSHL:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) << (as_int(env, stack[sp - 1]) & 63));
                sp--;
                break;
            case LSHR:
                stack[sp - 2] = box_long(env, as_long(env, stack[sp - 2]) >> (as_int(env, stack[sp - 1]) & 63));
                sp--;
                break;
            case LUSHR:
                stack[sp - 2] = box_long(env, (jlong) (((uint64_t) as_long(env, stack[sp - 2])) >> (as_int(env, stack[sp - 1]) & 63)));
                sp--;
                break;
            case FADD:
                stack[sp - 2] = box_float(env, as_float(env, stack[sp - 2]) + as_float(env, stack[sp - 1]));
                sp--;
                break;
            case FSUB:
                stack[sp - 2] = box_float(env, as_float(env, stack[sp - 2]) - as_float(env, stack[sp - 1]));
                sp--;
                break;
            case FMUL:
                stack[sp - 2] = box_float(env, as_float(env, stack[sp - 2]) * as_float(env, stack[sp - 1]));
                sp--;
                break;
            case FDIV:
                stack[sp - 2] = box_float(env, as_float(env, stack[sp - 2]) / as_float(env, stack[sp - 1]));
                sp--;
                break;
            case FREM:
                stack[sp - 2] = box_float(env, (jfloat) ((double) as_float(env, stack[sp - 2]) - (double) as_float(env, stack[sp - 1]) * (long) ((double) as_float(env, stack[sp - 2]) / (double) as_float(env, stack[sp - 1]))));
                sp--;
                break;
            case FNEG:
                stack[sp - 1] = box_float(env, -as_float(env, stack[sp - 1]));
                break;
            case DADD:
                stack[sp - 2] = box_double(env, as_double(env, stack[sp - 2]) + as_double(env, stack[sp - 1]));
                sp--;
                break;
            case DSUB:
                stack[sp - 2] = box_double(env, as_double(env, stack[sp - 2]) - as_double(env, stack[sp - 1]));
                sp--;
                break;
            case DMUL:
                stack[sp - 2] = box_double(env, as_double(env, stack[sp - 2]) * as_double(env, stack[sp - 1]));
                sp--;
                break;
            case DDIV:
                stack[sp - 2] = box_double(env, as_double(env, stack[sp - 2]) / as_double(env, stack[sp - 1]));
                sp--;
                break;
            case DREM:
                stack[sp - 2] = box_double(env, as_double(env, stack[sp - 2]) - as_double(env, stack[sp - 1]) * (long) (as_double(env, stack[sp - 2]) / as_double(env, stack[sp - 1])));
                sp--;
                break;
            case DNEG:
                stack[sp - 1] = box_double(env, -as_double(env, stack[sp - 1]));
                break;
            case I2L:
                stack[sp - 1] = box_long(env, (jlong) as_int(env, stack[sp - 1]));
                break;
            case I2F:
                stack[sp - 1] = box_float(env, (jfloat) as_int(env, stack[sp - 1]));
                break;
            case I2D:
                stack[sp - 1] = box_double(env, (jdouble) as_int(env, stack[sp - 1]));
                break;
            case L2I:
                stack[sp - 1] = box_int(env, (jint) as_long(env, stack[sp - 1]));
                break;
            case L2F:
                stack[sp - 1] = box_float(env, (jfloat) as_long(env, stack[sp - 1]));
                break;
            case L2D:
                stack[sp - 1] = box_double(env, (jdouble) as_long(env, stack[sp - 1]));
                break;
            case F2I:
                stack[sp - 1] = box_int(env, (jint) as_float(env, stack[sp - 1]));
                break;
            case F2L:
                stack[sp - 1] = box_long(env, (jlong) as_float(env, stack[sp - 1]));
                break;
            case F2D:
                stack[sp - 1] = box_double(env, (jdouble) as_float(env, stack[sp - 1]));
                break;
            case D2I:
                stack[sp - 1] = box_int(env, (jint) as_double(env, stack[sp - 1]));
                break;
            case D2L:
                stack[sp - 1] = box_long(env, (jlong) as_double(env, stack[sp - 1]));
                break;
            case D2F:
                stack[sp - 1] = box_float(env, (jfloat) as_double(env, stack[sp - 1]));
                break;
            case I2B:
                stack[sp - 1] = box_int(env, (jint) (int8_t) as_int(env, stack[sp - 1]));
                break;
            case I2C:
                stack[sp - 1] = box_int(env, (jint) (uint16_t) as_int(env, stack[sp - 1]));
                break;
            case I2S:
                stack[sp - 1] = box_int(env, (jint) (int16_t) as_int(env, stack[sp - 1]));
                break;
            case LCMP: {
                jlong left = as_long(env, stack[sp - 2]);
                jlong right = as_long(env, stack[sp - 1]);
                stack[sp - 2] = box_int(env, left > right ? 1 : (left < right ? -1 : 0));
                sp--;
                break;
            }
            case FCMPL:
                stack[sp - 2] = box_int(env, compare_float(as_float(env, stack[sp - 2]), as_float(env, stack[sp - 1]), -1));
                sp--;
                break;
            case FCMPG:
                stack[sp - 2] = box_int(env, compare_float(as_float(env, stack[sp - 2]), as_float(env, stack[sp - 1]), 1));
                sp--;
                break;
            case DCMPL:
                stack[sp - 2] = box_int(env, compare_double(as_double(env, stack[sp - 2]), as_double(env, stack[sp - 1]), -1));
                sp--;
                break;
            case DCMPG:
                stack[sp - 2] = box_int(env, compare_double(as_double(env, stack[sp - 2]), as_double(env, stack[sp - 1]), 1));
                sp--;
                break;
            case IINC: {
                jint local = VM_NEXT();
                jint increment = VM_NEXT();
                locals[local] = box_int(env, as_int(env, locals[local]) + increment);
                break;
            }
            case INVOKE_STATIC: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                jint argc = VM_NEXT();
                jobjectArray call_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, "java/lang/Object"), NULL);
                for (jint i = argc - 1; i >= 0; i--) {
                    (*env)->SetObjectArrayElement(env, call_args, i, stack[--sp]);
                }
                jint push_result = 0;
                jobject value = invoke_reflect(env, constants, owner_index, name_index, descriptor_index,
                        call_args, argc, NULL, 184, &push_result);
                if (push_result && !(*env)->ExceptionCheck(env)) {
                    stack[sp++] = value;
                }
                break;
            }
            case GET_STATIC: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                jobject value = access_field(env, constants, owner_index, name_index, descriptor_index,
                        NULL, NULL, JNI_FALSE);
                if (!(*env)->ExceptionCheck(env)) {
                    stack[sp++] = value;
                }
                break;
            }
            case PUT_STATIC: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                access_field(env, constants, owner_index, name_index, descriptor_index,
                        NULL, stack[--sp], JNI_TRUE);
                break;
            }
            case GET_FIELD: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                jobject target = stack[--sp];
                jobject value = access_field(env, constants, owner_index, name_index, descriptor_index,
                        target, NULL, JNI_FALSE);
                if (!(*env)->ExceptionCheck(env)) {
                    stack[sp++] = value;
                }
                break;
            }
            case PUT_FIELD: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                jobject value = stack[--sp];
                jobject target = stack[--sp];
                access_field(env, constants, owner_index, name_index, descriptor_index,
                        target, value, JNI_TRUE);
                break;
            }
            case INVOKE: {
                jint owner_index = VM_NEXT();
                jint name_index = VM_NEXT();
                jint descriptor_index = VM_NEXT();
                jint argc = VM_NEXT();
                jint invoke_opcode = VM_NEXT();
                jobjectArray call_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, "java/lang/Object"), NULL);
                for (jint i = argc - 1; i >= 0; i--) {
                    (*env)->SetObjectArrayElement(env, call_args, i, stack[--sp]);
                }
                jobject target = stack[--sp];
                jint push_result = 0;
                jobject value = invoke_reflect(env, constants, owner_index, name_index, descriptor_index,
                        call_args, argc, target, invoke_opcode, &push_result);
                if (push_result && !(*env)->ExceptionCheck(env)) {
                    stack[sp++] = value;
                }
                break;
            }
            case CHECKCAST: {
                jint type_index = VM_NEXT();
                if (stack[sp - 1] != NULL) {
                    jclass type = type_from_constant(env, constants, type_index);
                    if (type != NULL && !(*env)->ExceptionCheck(env)) {
                        if (!(*env)->IsInstanceOf(env, stack[sp - 1], type)) {
                            jclass cast_cls = (*env)->FindClass(env, "java/lang/ClassCastException");
                            if (cast_cls != NULL) {
                                (*env)->ThrowNew(env, cast_cls, "native VM checkcast");
                            }
                        }
                    }
                }
                break;
            }
            case INSTANCEOF: {
                jint type_index = VM_NEXT();
                jobject value = stack[--sp];
                jclass type = type_from_constant(env, constants, type_index);
                if (type != NULL && !(*env)->ExceptionCheck(env)) {
                    stack[sp++] = box_int(env, value != NULL && (*env)->IsInstanceOf(env, value, type) ? 1 : 0);
                }
                break;
            }
            case GOTO:
                pc = VM_AT(pc);
                break;
            case IFEQ: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) == 0) {
                    pc = target;
                }
                break;
            }
            case IFNE: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) != 0) {
                    pc = target;
                }
                break;
            }
            case IFLT: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) < 0) {
                    pc = target;
                }
                break;
            }
            case IFGE: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) >= 0) {
                    pc = target;
                }
                break;
            }
            case IFGT: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) > 0) {
                    pc = target;
                }
                break;
            }
            case IFLE: {
                jint target = VM_NEXT();
                if (as_int(env, stack[--sp]) <= 0) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPEQ: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left == right) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPNE: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left != right) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPLT: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left < right) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPGE: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left >= right) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPGT: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left > right) {
                    pc = target;
                }
                break;
            }
            case IF_ICMPLE: {
                jint target = VM_NEXT();
                jint right = as_int(env, stack[--sp]);
                jint left = as_int(env, stack[--sp]);
                if (left <= right) {
                    pc = target;
                }
                break;
            }
            case IF_ACMPEQ: {
                jint target = VM_NEXT();
                jobject right = stack[--sp];
                jobject left = stack[--sp];
                if ((*env)->IsSameObject(env, left, right)) {
                    pc = target;
                }
                break;
            }
            case IF_ACMPNE: {
                jint target = VM_NEXT();
                jobject right = stack[--sp];
                jobject left = stack[--sp];
                if (!(*env)->IsSameObject(env, left, right)) {
                    pc = target;
                }
                break;
            }
            case IFNULL: {
                jint target = VM_NEXT();
                if (stack[--sp] == NULL) {
                    pc = target;
                }
                break;
            }
            case IFNONNULL: {
                jint target = VM_NEXT();
                if (stack[--sp] != NULL) {
                    pc = target;
                }
                break;
            }
            case RETURN_OP:
                result = sp == 0 ? NULL : stack[--sp];
                pc = code_len;
                break;
            default:
                throw_unsupported(env);
                result = NULL;
                pc = code_len;
                break;
        }
        if ((*env)->ExceptionCheck(env)) {
            result = NULL;
            break;
        }
    }

#undef VM_NEXT
#undef VM_AT
    free(locals);
    free(stack);
    free(owned_code);
    free(owned_reverse);
    release_reader_resource(env, &reader);
    if (!encoded && !packed && code_array != NULL && code != NULL) {
        (*env)->ReleaseIntArrayElements(env, code_array, code, JNI_ABORT);
    }
    return result;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_8) != JNI_OK || env == NULL) {
        return JNI_ERR;
    }
    char bridge_name[] = {
            's','u','s','h','u','o','1','3','3','7','/',
            's','u','s','h','u','o','p','r','o','t','e','c','t','/',
            'l','i','b','/','N','a','t','i','v','e','B','r','i','d','g','e','\0'
    };
    jclass bridge = (*env)->FindClass(env, bridge_name);
    if (bridge == NULL) {
        return JNI_ERR;
    }
    JNINativeMethod methods[] = {
            {"_n", "([Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", (void *) sushuo_native_vm}
    };
    if ((*env)->RegisterNatives(env, bridge, methods, 1) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_8;
}
