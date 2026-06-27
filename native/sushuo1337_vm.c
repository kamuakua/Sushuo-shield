#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef _WIN32
#define SUSHUO_EXPORT __declspec(dllexport)
#define SUSHUO_CALL __stdcall
#else
#define SUSHUO_EXPORT __attribute__((visibility("default")))
#define SUSHUO_CALL
#endif

enum {
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
    LADD = 20,
    LSUB = 21,
    LMUL = 22,
    LDIV = 23,
    LREM = 24,
    LNEG = 25,
    LXOR = 26,
    RETURN_OP = 90
};

static jobject box_int(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Integer");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(I)Ljava/lang/Integer;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_long(JNIEnv *env, jlong value) {
    jclass cls = (*env)->FindClass(env, "java/lang/Long");
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, "valueOf", "(J)Ljava/lang/Long;");
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
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

SUSHUO_EXPORT jobject SUSHUO_CALL Java_sushuo1337_sushuoprotect_lib_NativeBridge__1n(
        JNIEnv *env, jclass ignored, jobjectArray program, jobjectArray args) {
    (void) ignored;

    jobject max_locals_obj = (*env)->GetObjectArrayElement(env, program, 0);
    jint max_locals = as_int(env, max_locals_obj);
    jintArray code_array = (jintArray) (*env)->GetObjectArrayElement(env, program, 3);
    jobjectArray constants = (jobjectArray) (*env)->GetObjectArrayElement(env, program, 4);

    jsize code_len = (*env)->GetArrayLength(env, code_array);
    jint *code = (*env)->GetIntArrayElements(env, code_array, NULL);
    jsize arg_len = (*env)->GetArrayLength(env, args);
    jsize local_len = max_locals > arg_len ? max_locals : arg_len;

    jobject *locals = (jobject *) calloc((size_t) local_len, sizeof(jobject));
    jobject *stack = (jobject *) calloc((size_t) (code_len + 8), sizeof(jobject));
    if (locals == NULL || stack == NULL) {
        free(locals);
        free(stack);
        (*env)->ReleaseIntArrayElements(env, code_array, code, JNI_ABORT);
        return NULL;
    }

    for (jsize i = 0; i < arg_len; i++) {
        locals[i] = (*env)->GetObjectArrayElement(env, args, i);
    }

    jint pc = 0;
    jint sp = 0;
    jobject result = NULL;
    while (pc < code_len) {
        jint op = code[pc++];
        switch (op) {
            case PUSH_CONST:
                stack[sp++] = (*env)->GetObjectArrayElement(env, constants, code[pc++]);
                break;
            case LOAD:
                stack[sp++] = locals[code[pc++]];
                break;
            case STORE:
                locals[code[pc++]] = stack[--sp];
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
            case RETURN_OP:
                result = sp == 0 ? NULL : stack[--sp];
                pc = code_len;
                break;
            default:
                pc = code_len;
                break;
        }
        if ((*env)->ExceptionCheck(env)) {
            result = NULL;
            break;
        }
    }

    free(locals);
    free(stack);
    (*env)->ReleaseIntArrayElements(env, code_array, code, JNI_ABORT);
    return result;
}
