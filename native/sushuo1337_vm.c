#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <windows.h>
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

static volatile unsigned char sushuo_bridge_name_obf[] = {
        0x8F, 0x4A, 0x01, 0xDD, 0x9D, 0x44, 0x5F, 0x92, 0xD7, 0x10, 0x35, 0x2E, 0xE5, 0xA0, 0x7E, 0x3C, 0xE3, 0xBF, 0x70, 0x2A, 0xCC, 0x9E, 0x5D, 0x05, 0x9B, 0x9B, 0x43, 0x0F, 0x8F, 0xAD, 0x47, 0x6D, 0x35, 0xE9, 0xB7, 0x57, 0x3A, 0xE2, 0xAA, 0x66, 0x21, 0x00
};
static char sushuo_bridge_name_buf[sizeof(sushuo_bridge_name_obf)];

static const char *sushuo_bridge_name_plain(void) {
    size_t len = sizeof(sushuo_bridge_name_obf) - 1U;
    for (size_t i = 0; i < len; i++) {
        unsigned char mask = (unsigned char) (0xA7U ^ (unsigned char) (i * 0x3DU + 0x5BU));
        sushuo_bridge_name_buf[i] = (char) (sushuo_bridge_name_obf[i] ^ mask);
    }
    sushuo_bridge_name_buf[len] = '\0';
    return sushuo_bridge_name_buf;
}

/* sushuo encoded native literals */
static const char *ss_dec(int id);
#define SS(ID) ss_dec(ID)
static char *ss_decode(volatile unsigned char *input, unsigned int length, unsigned char key, char *output) {
    for (unsigned int i = 0; i < length; i++) {
        unsigned char mask = (unsigned char) (key + (unsigned char) (i * 73U) + (unsigned char) ((i >> 1U) * 17U) + 0xA5U);
        output[i] = (char) (input[i] ^ mask);
    }
    output[length] = '\0';
    return output;
}
static volatile unsigned char ss_lit_0[] = {0xD1};
static char ss_buf_0[sizeof(ss_lit_0) + 1U];
static volatile unsigned char ss_lit_1[] = {0x54, 0x1A, 0xB9, 0x6D, 0x1D};
static char ss_buf_1[sizeof(ss_lit_1) + 1U];
static volatile unsigned char ss_lit_2[] = {0x11, 0xE1, 0xB6, 0x10, 0xD8};
static char ss_buf_2[sizeof(ss_lit_2) + 1U];
static volatile unsigned char ss_lit_3[] = {0x09, 0xC9, 0x6F};
static char ss_buf_3[sizeof(ss_lit_3) + 1U];
static volatile unsigned char ss_lit_4[] = {0xC4, 0x92, 0x68};
static char ss_buf_4[sizeof(ss_lit_4) + 1U];
static volatile unsigned char ss_lit_5[] = {0x8A, 0x77, 0x2F, 0x8D, 0x4A, 0x27, 0x9D, 0x45, 0x24, 0xFA, 0x41, 0x07, 0xEE, 0xAB, 0x0A, 0xD2, 0xB3, 0x6F, 0xD3, 0x9A, 0x72, 0xD2, 0x85, 0x73, 0x2F, 0x83, 0x73, 0x2C, 0x8D, 0x58};
static char ss_buf_5[sizeof(ss_lit_5) + 1U];
static volatile unsigned char ss_lit_6[] = {0x63, 0x33, 0xDA, 0x94, 0x60, 0xF4, 0x93, 0x55, 0xF2, 0xF1, 0x71, 0xED, 0xB7, 0x41, 0x19, 0xA6, 0x4D, 0x39, 0xB0, 0x6C, 0x13, 0xD5, 0x4F, 0x2B, 0xCE, 0x93, 0x20, 0xED, 0x9A, 0x53, 0xF8};
static char ss_buf_6[sizeof(ss_lit_6) + 1U];
static volatile unsigned char ss_lit_7[] = {0x9C};
static char ss_buf_7[sizeof(ss_lit_7) + 1U];
static volatile unsigned char ss_lit_8[] = {0xC9, 0x8D, 0x30, 0xEE, 0xC6, 0x5E, 0xED, 0xBB, 0x48, 0x57, 0x91, 0x77, 0x14, 0xCD, 0x6B};
static char ss_buf_8[sizeof(ss_lit_8) + 1U];
static volatile unsigned char ss_lit_9[] = {0x93, 0x58, 0xE3, 0xB2, 0x5F, 0xF0, 0xB2, 0x53, 0xF2, 0xAA, 0x46, 0x2D, 0xB5, 0x5C, 0x1D, 0xC0, 0x69, 0x34, 0xC2};
static char ss_buf_9[sizeof(ss_lit_9) + 1U];
static volatile unsigned char ss_lit_10[] = {0x23, 0x18, 0xC4, 0x96, 0x27, 0xFB, 0xDB, 0x51, 0xF6, 0x8E, 0x5D, 0xAC, 0x8E, 0x52, 0xF2, 0xA0, 0x4D, 0x0B, 0xFD, 0x26, 0x25, 0xD8, 0x6D, 0x23, 0xCE, 0xD7, 0x3B, 0xF4, 0xDA, 0x77, 0xF6, 0x91, 0x4E, 0xF0, 0x8D, 0x53, 0xF3, 0xAF, 0x45, 0x00, 0xFC};
static char ss_buf_10[sizeof(ss_lit_10) + 1U];
static volatile unsigned char ss_lit_11[] = {0x54, 0xE6, 0x97, 0x4B, 0xAB, 0xA4, 0x48, 0x5F, 0x83, 0x7D, 0x1D, 0xC3, 0x64, 0x0A, 0xC7, 0x8E, 0x33, 0xFE, 0x94};
static char ss_buf_11[sizeof(ss_lit_11) + 1U];
static volatile unsigned char ss_lit_12[] = {0xCC, 0x62, 0x00, 0xCE};
static char ss_buf_12[sizeof(ss_lit_12) + 1U];
static volatile unsigned char ss_lit_13[] = {0xC6, 0x6C, 0xD3, 0xF3, 0x7D};
static char ss_buf_13[sizeof(ss_lit_13) + 1U];
static volatile unsigned char ss_lit_14[] = {0x70, 0x30, 0xD9, 0x8C, 0x3C};
static char ss_buf_14[sizeof(ss_lit_14) + 1U];
static volatile unsigned char ss_lit_15[] = {0x0A, 0x42, 0x93};
static char ss_buf_15[sizeof(ss_lit_15) + 1U];
static volatile unsigned char ss_lit_16[] = {0x0F, 0xCF, 0x7E, 0x30, 0x84, 0x9D, 0x21, 0xB8, 0xB3, 0x43, 0xE0, 0xB8, 0x76, 0xF2, 0xA8, 0x42, 0x04, 0x89, 0x55, 0x1D, 0xB3, 0x79, 0x12, 0xFC, 0x7D, 0x20, 0xC9, 0x94, 0x22};
static char ss_buf_16[sizeof(ss_lit_16) + 1U];
static volatile unsigned char ss_lit_17[] = {0xB1, 0xBF, 0x5E, 0x10, 0xA7, 0x22};
static char ss_buf_17[sizeof(ss_lit_17) + 1U];
static volatile unsigned char ss_lit_18[] = {0xD0, 0x82, 0x23, 0xE7, 0x88};
static char ss_buf_18[sizeof(ss_lit_18) + 1U];
static volatile unsigned char ss_lit_19[] = {0xCA, 0x70, 0xC7, 0x87, 0x61, 0x58, 0x9D};
static char ss_buf_19[sizeof(ss_lit_19) + 1U];
static volatile unsigned char ss_lit_20[] = {0x47, 0x13, 0x94, 0x66, 0x0D, 0xA7, 0x5D, 0x17, 0xCD, 0x69, 0x1B};
static char ss_buf_20[sizeof(ss_lit_20) + 1U];
static volatile unsigned char ss_lit_21[] = {0x23, 0x7D, 0xF5, 0xB5};
static char ss_buf_21[sizeof(ss_lit_21) + 1U];
static volatile unsigned char ss_lit_22[] = {0xD6, 0x64, 0x29, 0xC9, 0x2D, 0x27, 0xC4, 0x80, 0x2F, 0xBE, 0xAA, 0x46, 0xFC, 0xB6, 0x48, 0x33, 0xBA, 0x79, 0x12, 0xB8, 0x55, 0x16, 0xC9, 0x49, 0x06, 0xEB, 0x6C, 0x39, 0xC8, 0x8B, 0x3A, 0xD7, 0x94, 0x56, 0xEA, 0xA8, 0x46, 0x12, 0xBA, 0x70};
static char ss_buf_22[sizeof(ss_lit_22) + 1U];
static volatile unsigned char ss_lit_23[] = {0xB3, 0x43, 0x0A, 0xA4, 0x30, 0x04, 0xA3, 0x65, 0x02, 0x81, 0x46, 0x24, 0xC6, 0x96, 0x2B, 0xE5};
static char ss_buf_23[sizeof(ss_lit_23) + 1U];
static volatile unsigned char ss_lit_24[] = {0xCF, 0x81, 0x3D, 0xC4, 0x8D, 0x59, 0xFA, 0xBD};
static char ss_buf_24[sizeof(ss_lit_24) + 1U];
static volatile unsigned char ss_lit_25[] = {0xBC, 0xF4, 0x7E};
static char ss_buf_25[sizeof(ss_lit_25) + 1U];
static volatile unsigned char ss_lit_26[] = {0x97, 0x2B, 0xF0, 0x80, 0x17, 0xEB, 0x88, 0x58, 0xE2};
static char ss_buf_26[sizeof(ss_lit_26) + 1U];
static volatile unsigned char ss_lit_27[] = {0xF6, 0x0E, 0xCB};
static char ss_buf_27[sizeof(ss_lit_27) + 1U];
static volatile unsigned char ss_lit_28[] = {0x36, 0xF5, 0x9C, 0x5D, 0xE2, 0x89, 0x58, 0xEE, 0xA9, 0x40};
static char ss_buf_28[sizeof(ss_lit_28) + 1U];
static volatile unsigned char ss_lit_29[] = {0x00, 0x58, 0x8D};
static char ss_buf_29[sizeof(ss_lit_29) + 1U];
static volatile unsigned char ss_lit_30[] = {0xC1, 0x81, 0x3D, 0xF3, 0x87, 0x51, 0xD8, 0xB6, 0x5D, 0x0F, 0xB1};
static char ss_buf_30[sizeof(ss_lit_30) + 1U];
static volatile unsigned char ss_lit_31[] = {0x5A, 0x92, 0x51};
static char ss_buf_31[sizeof(ss_lit_31) + 1U];
static volatile unsigned char ss_lit_32[] = {0x5B, 0x1B, 0xA2, 0x7C, 0x58, 0xAC, 0x7B, 0x0D, 0xDA, 0x29, 0x22, 0xC6, 0x6C, 0x20, 0xC3, 0x8E, 0x27};
static char ss_buf_32[sizeof(ss_lit_32) + 1U];
static volatile unsigned char ss_lit_33[] = {0x7D, 0x07, 0xAD, 0x67, 0x00, 0xCF, 0x66, 0x07, 0xCA, 0x98, 0x3B, 0xF2};
static char ss_buf_33[sizeof(ss_lit_33) + 1U];
static volatile unsigned char ss_lit_34[] = {0xC9, 0x03, 0xDE};
static char ss_buf_34[sizeof(ss_lit_34) + 1U];
static volatile unsigned char ss_lit_35[] = {0xDC, 0x9E, 0x2F, 0xC3, 0xD3, 0x29, 0xFE, 0x86, 0x25, 0xA4, 0xA6, 0x46, 0xE9, 0xA3, 0x4A, 0x17, 0xBA, 0x72, 0x03};
static char ss_buf_35[sizeof(ss_lit_35) + 1U];
static volatile unsigned char ss_lit_36[] = {0x0E, 0xDE, 0x71, 0x2B, 0xE5, 0x9D, 0x3A, 0xEA, 0x9C};
static char ss_buf_36[sizeof(ss_lit_36) + 1U];
static volatile unsigned char ss_lit_37[] = {0x78, 0xB0, 0xB0};
static char ss_buf_37[sizeof(ss_lit_37) + 1U];
static volatile unsigned char ss_lit_38[] = {0x65, 0x39, 0xC4, 0x9A, 0x7A, 0xF2, 0x99, 0x2F, 0xFC, 0xCB, 0x77, 0xE9, 0x95, 0x4F, 0xE3, 0xA8, 0x55};
static char ss_buf_38[sizeof(ss_lit_38) + 1U];
static volatile unsigned char ss_lit_39[] = {0xB0, 0x6E, 0x05, 0xC7, 0x69, 0x1A, 0xC9};
static char ss_buf_39[sizeof(ss_lit_39) + 1U];
static volatile unsigned char ss_lit_40[] = {0xB8, 0x90, 0x1A, 0x30, 0xBC, 0x7E, 0x0F, 0xA3, 0x33, 0x09, 0xDE, 0x66, 0x05, 0x84, 0x4C, 0x20, 0xDC, 0x94, 0x2C, 0xF1, 0x9C, 0x0C};
static char ss_buf_40[sizeof(ss_lit_40) + 1U];
static volatile unsigned char ss_lit_41[] = {0x9D, 0xA4, 0x71, 0xED, 0x91, 0x25, 0xE8, 0x86, 0x6E, 0xE6, 0x85, 0x43, 0xE0, 0xFF, 0x68, 0x1C, 0xA2, 0x7A, 0x15, 0xD8, 0x7D, 0x67};
static char ss_buf_41[sizeof(ss_lit_41) + 1U];
static volatile unsigned char ss_lit_42[] = {0xE8, 0xAA, 0x53, 0x0F, 0xE7, 0x7D, 0x0A, 0xDA, 0x69, 0x78, 0xF3, 0x83, 0x20, 0xF8};
static char ss_buf_42[sizeof(ss_lit_42) + 1U];
static volatile unsigned char ss_lit_43[] = {0xF6, 0x65, 0xA8, 0x86, 0x4E, 0x0C, 0xB1, 0x71, 0x45, 0xDF, 0x6C, 0x38, 0xD7, 0xD6, 0x11, 0xE5, 0x82, 0x5A, 0xA2};
static char ss_buf_43[sizeof(ss_lit_43) + 1U];
static volatile unsigned char ss_lit_44[] = {0xBD, 0x41, 0x0C, 0xA2, 0x32, 0x0A, 0xA1, 0x67, 0x04, 0x83, 0x55, 0x27, 0xC6, 0x80, 0x38};
static char ss_buf_44[sizeof(ss_lit_44) + 1U];
static volatile unsigned char ss_lit_45[] = {0x1B, 0x2F, 0xFF, 0x53, 0x13, 0xA3, 0x6A, 0x04, 0x90, 0x64, 0x03, 0xC5, 0x62, 0x61, 0xFB, 0x99, 0x24, 0xE6, 0x9A, 0x0C};
static char ss_buf_45[sizeof(ss_lit_45) + 1U];
static volatile unsigned char ss_lit_46[] = {0xAC, 0x8E, 0x0E, 0x3C, 0xA0, 0x72, 0x1B, 0xD7, 0x3F, 0x35, 0xD2, 0x92, 0x31, 0xB0, 0xBA, 0x2A, 0xFD, 0x97, 0x5E, 0xEB, 0x96, 0x4E, 0xF7, 0xF5};
static char ss_buf_46[sizeof(ss_lit_46) + 1U];
static volatile unsigned char ss_lit_47[] = {0x51, 0xE5, 0xA8, 0x46, 0xAE, 0xA6, 0x45, 0x03, 0xA0, 0x3F, 0x26, 0xDC, 0x63, 0x31};
static char ss_buf_47[sizeof(ss_lit_47) + 1U];
static volatile unsigned char ss_lit_48[] = {0xBF, 0xAA, 0x13, 0xCF, 0xB7, 0x47, 0xF6, 0xA8, 0x0C, 0x00, 0xA7, 0x61, 0x0E, 0x9D, 0x40, 0x3A, 0xC1, 0x9F, 0x69};
static char ss_buf_48[sizeof(ss_lit_48) + 1U];
static volatile unsigned char ss_lit_49[] = {0xFA, 0xB8, 0x45, 0x1D, 0xF9, 0x73, 0x18, 0xAC, 0x7B, 0x4A, 0xF9, 0x64, 0x0D, 0xCA, 0x71};
static char ss_buf_49[sizeof(ss_lit_49) + 1U];
static volatile unsigned char ss_lit_50[] = {0xC4, 0x73, 0xA6, 0x94, 0x58, 0x1A, 0xA3, 0x7F, 0x57, 0xAD, 0x7A, 0x0A, 0xD9, 0x28, 0x27, 0xC6, 0x6B, 0x2C, 0xD3, 0xCB};
static char ss_buf_50[sizeof(ss_lit_50) + 1U];
static volatile unsigned char ss_lit_51[] = {0x8F, 0x4F, 0xFE, 0xB0, 0x04, 0x18, 0xAF, 0x79, 0x16, 0x95, 0x50, 0x32, 0xC2, 0x62, 0x36, 0xC6};
static char ss_buf_51[sizeof(ss_lit_51) + 1U];
static volatile unsigned char ss_lit_52[] = {0x69, 0xCE, 0xCD, 0x61, 0xED, 0xB1, 0x5C, 0x12, 0xE2, 0x7A, 0x11, 0xD7, 0x74, 0x73, 0xF2, 0x90, 0x2C, 0xC0, 0x90, 0x20, 0xA4};
static char ss_buf_52[sizeof(ss_lit_52) + 1U];
static volatile unsigned char ss_lit_53[] = {0x46, 0x14, 0xB9, 0x79, 0x5D, 0xD7, 0x74, 0x30, 0xDF, 0x2E, 0x0E, 0xCA, 0x8D, 0x32, 0xD1, 0x9A, 0x2B, 0xFF, 0x93, 0x55, 0xEE, 0x9C, 0x5D, 0x13, 0xA2, 0x78, 0x07, 0xD5, 0x79, 0x31, 0xFC, 0x7A, 0x3F, 0xC0, 0x8F, 0x3C, 0xCB, 0x84, 0x2B};
static char ss_buf_53[sizeof(ss_lit_53) + 1U];
static volatile unsigned char ss_lit_54[] = {0x13, 0xC2, 0x63, 0x3D, 0xC4, 0xC1, 0x24, 0xF0, 0x85, 0x53, 0xA1, 0x94, 0x5D, 0x1B, 0xA7, 0x6E, 0x4C};
static char ss_buf_54[sizeof(ss_lit_54) + 1U];
static volatile unsigned char ss_lit_55[] = {0x09, 0xCD, 0x70, 0x2E, 0x86, 0x9E, 0x2D, 0xFB, 0x88, 0x17, 0xC4, 0xB4, 0x5C, 0x1A};
static char ss_buf_55[sizeof(ss_lit_55) + 1U];
static volatile unsigned char ss_lit_56[] = {0x4E, 0x3A, 0xED, 0x43};
static char ss_buf_56[sizeof(ss_lit_56) + 1U];
static volatile unsigned char ss_lit_57[] = {0x06, 0xC6, 0x71, 0x02, 0xC7, 0x82, 0x2C};
static char ss_buf_57[sizeof(ss_lit_57) + 1U];
static volatile unsigned char ss_lit_58[] = {0xE7, 0x54, 0x18, 0xDA, 0x63, 0x3F, 0x97, 0x6D, 0x3A, 0xCA, 0x99, 0x68, 0xF2, 0x9E, 0x36, 0xE4, 0x89, 0x57, 0xB1, 0xFA, 0x61, 0x1C, 0xB1, 0x6F, 0x12, 0x93, 0x7A, 0x3E, 0xD7, 0x65, 0x73, 0xE6, 0x93, 0x29, 0xD1, 0x98, 0x7E};
static char ss_buf_58[sizeof(ss_lit_58) + 1U];
static volatile unsigned char ss_lit_59[] = {0x8B, 0x50, 0xFB, 0x8B, 0x47, 0x0B, 0xB0, 0x6C, 0x1B, 0xAD, 0x7A, 0x17, 0xCD};
static char ss_buf_59[sizeof(ss_lit_59) + 1U];
static volatile unsigned char ss_lit_60[] = {0x7B, 0xB5, 0xBA, 0x55, 0xF8, 0x94, 0x5D, 0xAA, 0xB3, 0x49, 0xEC, 0xAC, 0x0A, 0x2D, 0xA4, 0x70, 0x18, 0xC7, 0x35};
static char ss_buf_60[sizeof(ss_lit_60) + 1U];
static volatile unsigned char ss_lit_61[] = {0x05, 0xCE, 0x71, 0x0A, 0xCD, 0x92, 0x27, 0xF5, 0x9C, 0x52, 0xF5, 0x97, 0x51, 0x09, 0xBF, 0x4F, 0x1E};
static char ss_buf_61[sizeof(ss_lit_61) + 1U];
static volatile unsigned char ss_lit_62[] = {0xA4, 0x99, 0x45, 0x19, 0xA4, 0x7A, 0x5A, 0xD2, 0x79, 0x0F, 0xDC, 0x2B, 0x0D, 0xD3, 0x73, 0x23, 0xCA, 0x8A, 0x7C, 0xCB, 0xA6, 0x59, 0xEC, 0xA0, 0x51, 0x56, 0xBF, 0x7D, 0x18, 0xD8, 0x36, 0x21, 0xD0, 0x64, 0x2C, 0xDB, 0x39, 0x62, 0xE9, 0x84, 0x29, 0xE7, 0x8A, 0x1B, 0xE2, 0xB6, 0x5F, 0x1D, 0xFB, 0x6F, 0x12, 0xA6, 0x76, 0x06, 0xDE, 0x72, 0x4F, 0xE4, 0x66, 0x38, 0xCE, 0x80, 0x2D, 0xA9};
static char ss_buf_62[sizeof(ss_lit_62) + 1U];
static volatile unsigned char ss_lit_63[] = {0xC6, 0x8F, 0x30, 0xC9, 0x82, 0x53, 0xE6, 0xB2, 0x5F, 0x13, 0xB4, 0x5F, 0x1A, 0xD9, 0x7A, 0x3B};
static char ss_buf_63[sizeof(ss_lit_63) + 1U];
static volatile unsigned char ss_lit_64[] = {0x2D, 0x02, 0xC2, 0x90, 0x3D, 0xF5, 0xC1, 0x5B, 0xF0, 0xB4, 0x53, 0x52, 0x84, 0x54, 0x08, 0xAA, 0x73, 0x01, 0xFB, 0x20, 0x2F, 0xC6, 0x67, 0x39, 0xC8, 0xDD, 0x20, 0xF4, 0x81, 0x5F, 0xBD, 0xA9, 0x50, 0x18, 0xB4, 0x44, 0x18, 0xB0, 0x31, 0x21, 0xA8, 0x6F, 0x08, 0xC9, 0x3C};
static char ss_buf_64[sizeof(ss_lit_64) + 1U];
static volatile unsigned char ss_lit_65[] = {0xFA, 0xB8, 0x45, 0x1D, 0xF9, 0x73, 0x18, 0xAC, 0x7B, 0x4A, 0xF1, 0x67, 0x31, 0xDE, 0x66, 0x26, 0xE5, 0x94, 0x3F, 0xFC, 0x81, 0x53, 0xD4, 0xA2, 0x57, 0x18, 0xA7, 0x54, 0x13, 0xAC, 0x73};
static char ss_buf_65[sizeof(ss_lit_65) + 1U];
static volatile unsigned char ss_lit_66[] = {0xC0, 0x92, 0x3B, 0xF7, 0xDF, 0x55, 0xF2, 0xB2, 0x51, 0x50, 0x97, 0x4D, 0x2F, 0xB0, 0x7C, 0x00, 0x84, 0x62, 0x00, 0xC2, 0x6C, 0x14, 0xD3, 0x97, 0x2B, 0xE7, 0x85, 0x53, 0xFB, 0xB3};
static char ss_buf_66[sizeof(ss_lit_66) + 1U];
static volatile unsigned char ss_lit_67[] = {0xE7, 0xB7, 0x46, 0x18, 0xFC, 0x70, 0x17, 0xD1, 0x7E, 0x4D, 0xCE, 0x60, 0x39, 0xC4, 0x67, 0x28, 0xD1, 0xC1, 0x05, 0xF4, 0x9F, 0x5C, 0xE1, 0xB3};
static char ss_buf_67[sizeof(ss_lit_67) + 1U];
static volatile unsigned char ss_lit_68[] = {0x4A, 0xE7, 0xA8, 0x64, 0x1C, 0xAB, 0x47, 0x18, 0xB6, 0x67, 0x0A, 0xDD, 0x6E};
static char ss_buf_68[sizeof(ss_lit_68) + 1U];
static volatile unsigned char ss_lit_69[] = {0xD3, 0x1E, 0xB7, 0xB1};
static char ss_buf_69[sizeof(ss_lit_69) + 1U];
static volatile unsigned char ss_lit_70[] = {0x5F, 0x11, 0xAF, 0x4D, 0x17, 0xA0};
static char ss_buf_70[sizeof(ss_lit_70) + 1U];
static volatile unsigned char ss_lit_71[] = {0xA4, 0x99, 0x45, 0x19, 0xA4, 0x7A, 0x5A, 0xD2, 0x79, 0x0F, 0xDC, 0x2B, 0x11, 0xC5, 0x6B, 0x2F, 0xC7, 0x99, 0x7C, 0xCB, 0xA6, 0x59, 0xEC, 0xA0, 0x51, 0x56, 0xBF, 0x7D, 0x18, 0xD8, 0x36, 0x2D, 0xDE, 0x6F, 0x3A, 0xCB, 0x76, 0x70, 0x8C, 0xA2, 0x22, 0xF0, 0x9D, 0x55, 0xA1, 0xBB, 0x50, 0x14, 0xB3, 0x32, 0x38, 0xA2, 0x70, 0x06, 0xDE, 0x72, 0x5B};
static char ss_buf_71[sizeof(ss_lit_71) + 1U];
static volatile unsigned char ss_lit_72[] = {0x84, 0x56, 0xE7, 0xBB, 0x1B, 0x11, 0xB6, 0x4E, 0x1D, 0xEC, 0x52, 0x04, 0xAA, 0x6C, 0x00, 0xD8};
static char ss_buf_72[sizeof(ss_lit_72) + 1U];
static volatile unsigned char ss_lit_73[] = {0x0A, 0xC8, 0x75, 0x2D, 0x89, 0x83, 0x28, 0xFC, 0x8B, 0x1A, 0xFD, 0xBD, 0x54, 0x17, 0xB0, 0x7D, 0x0C, 0xEE, 0x5D, 0x0D, 0xDB, 0x6B, 0x05};
static char ss_buf_73[sizeof(ss_lit_73) + 1U];
static volatile unsigned char ss_lit_74[] = {0xDA, 0x97, 0x38};
static char ss_buf_74[sizeof(ss_lit_74) + 1U];
static volatile unsigned char ss_lit_75[] = {0x72, 0xEF, 0x97, 0x27, 0xD6, 0x88, 0x6C, 0xE0, 0x87, 0x41, 0xEE, 0xFD, 0x63, 0x17, 0xA5, 0x7D, 0x11, 0xCF, 0x2E, 0x12, 0xD2, 0x60, 0x2D, 0xC5, 0xD1, 0x2B, 0xC0, 0x84, 0x23, 0xA2, 0xA8, 0x52, 0xE0, 0xB6, 0x4E, 0x02, 0xEB, 0x30, 0x25};
static char ss_buf_75[sizeof(ss_lit_75) + 1U];
static volatile unsigned char ss_lit_76[] = {0x94, 0x59, 0xE2};
static char ss_buf_76[sizeof(ss_lit_76) + 1U];
static volatile unsigned char ss_lit_77[] = {0xB1, 0xAE, 0x56, 0xE4, 0xA9, 0x49, 0xAD, 0xA7, 0x44, 0x00, 0xAF, 0x3E, 0x24, 0xD6, 0x64, 0x32, 0xD2, 0x8E, 0x6F, 0xB4, 0xBB, 0x2A, 0xFB, 0x95, 0x5C, 0xA9, 0x8C, 0x48, 0xED, 0xAB, 0x09, 0x20, 0xAB, 0x78, 0x09, 0xD6, 0x7B, 0x63};
static char ss_buf_77[sizeof(ss_lit_77) + 1U];
static volatile unsigned char ss_lit_78[] = {0xA6, 0x74, 0x19, 0xD9, 0x3D, 0x37, 0xD4, 0x90, 0x3F, 0x8E, 0xA8, 0x30, 0xEC, 0x8E, 0x2F, 0xED};
static char ss_buf_78[sizeof(ss_lit_78) + 1U];
static volatile unsigned char ss_lit_79[] = {0xC6, 0x94, 0x39, 0xF9, 0xDD, 0x55, 0xFC, 0xB1, 0x17, 0xE2, 0xB3, 0x45, 0x0C, 0xB4, 0x44, 0x1E, 0xEB, 0x5E, 0x13, 0xD1, 0x64, 0x37, 0xCC, 0x84, 0x34, 0xDA, 0x9B, 0x5D, 0xE4, 0xAC, 0x5C, 0xF6, 0xAF};
static char ss_buf_79[sizeof(ss_lit_79) + 1U];
static volatile unsigned char ss_lit_80[] = {0xC8, 0xB2, 0x06, 0xD6, 0xDB};
static char ss_buf_80[sizeof(ss_lit_80) + 1U];
static volatile unsigned char ss_lit_81[] = {0xE5, 0x98, 0x2D, 0xE3, 0x8E, 0x17, 0xFC, 0xB2, 0x5A, 0x51, 0xBB, 0x49, 0x1A, 0xB6, 0x6D, 0x02, 0xB5, 0x25, 0x27, 0xC5, 0x66, 0x22, 0xD9, 0x96, 0x39, 0xAD};
static char ss_buf_81[sizeof(ss_lit_81) + 1U];
static volatile unsigned char ss_lit_82[] = {0x2D, 0x15, 0xEA, 0xBD, 0x21, 0xF5, 0x98, 0x56, 0xBE, 0xB4, 0x5D, 0x12, 0xF8, 0x43, 0x12, 0xA2, 0x6F, 0x15, 0xA5, 0x7D, 0x4C, 0xEF, 0x6E, 0x2E, 0xDB, 0x81, 0x29, 0xE1, 0xD4, 0x11, 0xC4};
static char ss_buf_82[sizeof(ss_lit_82) + 1U];
static volatile unsigned char ss_lit_83[] = {0xEF, 0xAF, 0x5E, 0x10, 0xE5, 0x78, 0x0F, 0xD9, 0x76, 0x74, 0xE0, 0x95, 0x25, 0xC5, 0x9B, 0x27};
static char ss_buf_83[sizeof(ss_lit_83) + 1U];
static volatile unsigned char ss_lit_84[] = {0xEE, 0xB7, 0x58, 0x26, 0xBB, 0x79, 0x11, 0xD0, 0x41, 0x2C, 0xD9, 0x62, 0x3E};
static char ss_buf_84[sizeof(ss_lit_84) + 1U];
static volatile unsigned char ss_lit_85[] = {0x6A, 0x08};
static char ss_buf_85[sizeof(ss_lit_85) + 1U];
static volatile unsigned char ss_lit_86[] = {0x05, 0xCD};
static char ss_buf_86[sizeof(ss_lit_86) + 1U];
static volatile unsigned char ss_lit_87[] = {0x45, 0x19, 0xA4, 0x7A, 0x5B, 0xD2, 0x79, 0x0F, 0xDC, 0x2A, 0x2C, 0xC2, 0x67, 0x26, 0xC1, 0x8E, 0x33, 0xBE};
static char ss_buf_87[sizeof(ss_lit_87) + 1U];
static volatile unsigned char ss_lit_88[] = {0x1F, 0xDA, 0x73, 0x4F, 0xD2, 0x6A, 0x2A, 0xC2, 0x73, 0x24, 0xC5, 0x81, 0x69, 0xE2, 0x8F, 0x55, 0xE1, 0xB3, 0x53, 0x0D, 0xFD};
static char ss_buf_88[sizeof(ss_lit_88) + 1U];
static volatile unsigned char ss_lit_89[] = {0x44, 0xF5, 0xB4, 0x0D, 0x0F, 0xA3, 0x46, 0x05, 0xA6, 0x6F, 0x12, 0x81};
static char ss_buf_89[sizeof(ss_lit_89) + 1U];
static volatile unsigned char ss_lit_90[] = {0xF9, 0xBD, 0x40, 0x1E, 0xF7, 0x4E, 0x1D, 0xAB, 0x78, 0x46, 0xAB, 0x65, 0x13, 0xC1, 0x63, 0x34, 0x85};
static char ss_buf_90[sizeof(ss_lit_90) + 1U];
static volatile unsigned char ss_lit_91[] = {0xC7, 0x97, 0x26, 0xF8, 0xDC, 0x50, 0xF7, 0xB1, 0x5E, 0xAD, 0x88, 0x4D, 0x0D, 0xAD, 0x43, 0x0F};
static char ss_buf_91[sizeof(ss_lit_91) + 1U];
static volatile unsigned char ss_lit_92[] = {0x21, 0xF5, 0x98, 0x56, 0xBE, 0xB6, 0x55, 0x13, 0xB0, 0x0F, 0x29, 0xB7, 0x7C, 0x05, 0xAB, 0x5D, 0x11, 0xCD, 0x65, 0x2A, 0xEC, 0x9E, 0x29, 0xF8, 0x8A, 0x56, 0xE6};
static char ss_buf_92[sizeof(ss_lit_92) + 1U];
static volatile unsigned char ss_lit_93[] = {0xB5, 0x6A, 0x0B, 0xB0, 0x79, 0x0B, 0xCB, 0x5C, 0x0A, 0xD9, 0x60, 0x2F, 0xCC};
static char ss_buf_93[sizeof(ss_lit_93) + 1U];
static volatile unsigned char ss_lit_94[] = {0x60, 0xB8, 0xA7, 0x5E, 0xEF, 0xA1, 0x50, 0x55, 0xB8, 0x7C, 0x19, 0xA7, 0x35, 0x37, 0xD5, 0x74, 0x05, 0xC8, 0x67, 0x77};
static char ss_buf_94[sizeof(ss_lit_94) + 1U];
static volatile unsigned char ss_lit_95[] = {0xD9, 0x13, 0xCF, 0x91, 0x5D, 0xE1, 0xAC, 0x42, 0x52, 0xAA, 0x41, 0x07, 0xA4, 0x23, 0x35, 0xDB, 0x68, 0x31, 0xC7, 0xA1, 0x3D, 0xF9, 0x91, 0x5E, 0xD0, 0xB2, 0x5D, 0xEC, 0xBE, 0x4A, 0x0A, 0xFC};
static char ss_buf_95[sizeof(ss_lit_95) + 1U];
static volatile unsigned char ss_lit_96[] = {0x5D, 0xE6, 0xA9, 0x65, 0xEC, 0xA8, 0x50, 0x1F, 0x88, 0x6E, 0x04, 0xD7};
static char ss_buf_96[sizeof(ss_lit_96) + 1U];
static volatile unsigned char ss_lit_97[] = {0x9F, 0x29, 0x16, 0xC9, 0x9C, 0x30, 0xC1, 0xC6, 0x2F, 0xED, 0x88, 0x48, 0xA6, 0x81, 0x58, 0x07, 0xA6, 0x76, 0x15, 0x80};
static char ss_buf_97[sizeof(ss_lit_97) + 1U];
static volatile unsigned char ss_lit_98[] = {0xE8, 0xBD, 0x46, 0x36, 0xB0, 0x6A, 0x10, 0xAE, 0x7F, 0x2A, 0xDF, 0x6A, 0x04};
static char ss_buf_98[sizeof(ss_lit_98) + 1U];
static volatile unsigned char ss_lit_99[] = {0x15, 0xDE, 0x61, 0x10, 0xD9, 0x6C, 0x3E};
static char ss_buf_99[sizeof(ss_lit_99) + 1U];
static volatile unsigned char ss_lit_100[] = {0x14, 0xA6, 0x57, 0x0B, 0xEB, 0x61, 0x06, 0xDE, 0x6D, 0x7C, 0xEE, 0x9A, 0x31, 0xEA, 0x80, 0x7F, 0xF7, 0xAC, 0x4D, 0xC7, 0xA4, 0x46, 0x1A, 0xB8, 0x56, 0x02, 0xAA, 0x60};
static char ss_buf_100[sizeof(ss_lit_100) + 1U];
static volatile unsigned char ss_lit_101[] = {0x1B};
static char ss_buf_101[sizeof(ss_lit_101) + 1U];
static volatile unsigned char ss_lit_102[] = {0x16, 0xC9, 0x9C, 0x30, 0xC1, 0xC6, 0x2F, 0xED, 0x88, 0x48, 0xA6, 0x81, 0x58, 0x07, 0xA6, 0x76, 0x15, 0x80};
static char ss_buf_102[sizeof(ss_lit_102) + 1U];
static volatile unsigned char ss_lit_103[] = {0xA6};
static char ss_buf_103[sizeof(ss_lit_103) + 1U];
static volatile unsigned char ss_lit_104[] = {0x8A};
static char ss_buf_104[sizeof(ss_lit_104) + 1U];
static volatile unsigned char ss_lit_105[] = {0xA9, 0x86, 0x4E, 0x0C, 0xB1, 0x71, 0x45, 0xDF, 0x6C, 0x38, 0xD7, 0xD6, 0x1C, 0xFE, 0x9C, 0x5A, 0xFA, 0x96, 0x07, 0xC9, 0xB5, 0x49, 0xF4, 0xAA, 0x0A, 0x02, 0xA9, 0x7F, 0x0C, 0x9B, 0x41, 0x35, 0xDB, 0x9F, 0x37, 0xE9, 0xCC, 0x09, 0xB3, 0xAF, 0x57, 0xE7, 0x96, 0x48, 0xAC, 0xA0, 0x47, 0x01, 0xAE, 0x3D, 0x23, 0xD7, 0x65, 0x3D, 0xD1, 0x8F, 0x6E};
static char ss_buf_105[sizeof(ss_lit_105) + 1U];
static volatile unsigned char ss_lit_106[] = {0xDE, 0x76, 0xD5, 0x88, 0x5D, 0xF3, 0xBE, 0x07, 0xEE, 0xAA, 0x4B, 0x09, 0xE7, 0x52, 0x07, 0xD5, 0x7D, 0x24, 0x8A, 0xB6, 0x3E, 0xFC, 0x81, 0x21, 0xB5, 0x8F, 0x5C, 0xE8, 0x87, 0x06, 0xD0, 0xB8, 0x54, 0x06, 0xA7, 0x75, 0x57, 0xFC, 0x46, 0x11, 0x9B, 0xB2};
static char ss_buf_106[sizeof(ss_lit_106) + 1U];
static volatile unsigned char ss_lit_107[] = {0x33, 0x2D, 0xF2, 0x6D, 0x00, 0xDC, 0x65, 0x62, 0xCB, 0x91, 0x24, 0xF4, 0xC2, 0x75, 0xFC, 0xB8, 0x40, 0x0F, 0xED, 0x53, 0x13, 0xA3, 0x6A, 0x04, 0x90, 0x64, 0x03, 0xC5, 0x62, 0x61, 0xFB, 0x85, 0x39, 0xFD, 0x80, 0x50, 0xAA, 0x90, 0x7D, 0x34, 0xFE, 0x6A};
static char ss_buf_107[sizeof(ss_lit_107) + 1U];
static const char *ss_dec(int id) {
    switch (id) {
        case 0: return ss_decode(ss_lit_0, (unsigned int) sizeof(ss_lit_0), 0x3CU, ss_buf_0);
        case 1: return ss_decode(ss_lit_1, (unsigned int) sizeof(ss_lit_1), 0x8DU, ss_buf_1);
        case 2: return ss_decode(ss_lit_2, (unsigned int) sizeof(ss_lit_2), 0xB2U, ss_buf_2);
        case 3: return ss_decode(ss_lit_3, (unsigned int) sizeof(ss_lit_3), 0xC1U, ss_buf_3);
        case 4: return ss_decode(ss_lit_4, (unsigned int) sizeof(ss_lit_4), 0xE6U, ss_buf_4);
        case 5: return ss_decode(ss_lit_5, (unsigned int) sizeof(ss_lit_5), 0x34U, ss_buf_5);
        case 6: return ss_decode(ss_lit_6, (unsigned int) sizeof(ss_lit_6), 0x64U, ss_buf_6);
        case 7: return ss_decode(ss_lit_7, (unsigned int) sizeof(ss_lit_7), 0x3FU, ss_buf_7);
        case 8: return ss_decode(ss_lit_8, (unsigned int) sizeof(ss_lit_8), 0xFEU, ss_buf_8);
        case 9: return ss_decode(ss_lit_9, (unsigned int) sizeof(ss_lit_9), 0x4FU, ss_buf_9);
        case 10: return ss_decode(ss_lit_10, (unsigned int) sizeof(ss_lit_10), 0x66U, ss_buf_10);
        case 11: return ss_decode(ss_lit_11, (unsigned int) sizeof(ss_lit_11), 0x99U, ss_buf_11);
        case 12: return ss_decode(ss_lit_12, (unsigned int) sizeof(ss_lit_12), 0x19U, ss_buf_12);
        case 13: return ss_decode(ss_lit_13, (unsigned int) sizeof(ss_lit_13), 0x49U, ss_buf_13);
        case 14: return ss_decode(ss_lit_14, (unsigned int) sizeof(ss_lit_14), 0x6EU, ss_buf_14);
        case 15: return ss_decode(ss_lit_15, (unsigned int) sizeof(ss_lit_15), 0x7DU, ss_buf_15);
        case 16: return ss_decode(ss_lit_16, (unsigned int) sizeof(ss_lit_16), 0xC0U, ss_buf_16);
        case 17: return ss_decode(ss_lit_17, (unsigned int) sizeof(ss_lit_17), 0xE8U, ss_buf_17);
        case 18: return ss_decode(ss_lit_18, (unsigned int) sizeof(ss_lit_18), 0x02U, ss_buf_18);
        case 19: return ss_decode(ss_lit_19, (unsigned int) sizeof(ss_lit_19), 0x3DU, ss_buf_19);
        case 20: return ss_decode(ss_lit_20, (unsigned int) sizeof(ss_lit_20), 0x8EU, ss_buf_20);
        case 21: return ss_decode(ss_lit_21, (unsigned int) sizeof(ss_lit_21), 0x66U, ss_buf_21);
        case 22: return ss_decode(ss_lit_22, (unsigned int) sizeof(ss_lit_22), 0x17U, ss_buf_22);
        case 23: return ss_decode(ss_lit_23, (unsigned int) sizeof(ss_lit_23), 0x34U, ss_buf_23);
        case 24: return ss_decode(ss_lit_24, (unsigned int) sizeof(ss_lit_24), 0x01U, ss_buf_24);
        case 25: return ss_decode(ss_lit_25, (unsigned int) sizeof(ss_lit_25), 0xEFU, ss_buf_25);
        case 26: return ss_decode(ss_lit_26, (unsigned int) sizeof(ss_lit_26), 0x56U, ss_buf_26);
        case 27: return ss_decode(ss_lit_27, (unsigned int) sizeof(ss_lit_27), 0x39U, ss_buf_27);
        case 28: return ss_decode(ss_lit_28, (unsigned int) sizeof(ss_lit_28), 0xABU, ss_buf_28);
        case 29: return ss_decode(ss_lit_29, (unsigned int) sizeof(ss_lit_29), 0x83U, ss_buf_29);
        case 30: return ss_decode(ss_lit_30, (unsigned int) sizeof(ss_lit_30), 0x00U, ss_buf_30);
        case 31: return ss_decode(ss_lit_31, (unsigned int) sizeof(ss_lit_31), 0xCDU, ss_buf_31);
        case 32: return ss_decode(ss_lit_32, (unsigned int) sizeof(ss_lit_32), 0x8CU, ss_buf_32);
        case 33: return ss_decode(ss_lit_33, (unsigned int) sizeof(ss_lit_33), 0x7AU, ss_buf_33);
        case 34: return ss_decode(ss_lit_34, (unsigned int) sizeof(ss_lit_34), 0x3CU, ss_buf_34);
        case 35: return ss_decode(ss_lit_35, (unsigned int) sizeof(ss_lit_35), 0x11U, ss_buf_35);
        case 36: return ss_decode(ss_lit_36, (unsigned int) sizeof(ss_lit_36), 0xC8U, ss_buf_36);
        case 37: return ss_decode(ss_lit_37, (unsigned int) sizeof(ss_lit_37), 0xABU, ss_buf_37);
        case 38: return ss_decode(ss_lit_38, (unsigned int) sizeof(ss_lit_38), 0x6AU, ss_buf_38);
        case 39: return ss_decode(ss_lit_39, (unsigned int) sizeof(ss_lit_39), 0x21U, ss_buf_39);
        case 40: return ss_decode(ss_lit_40, (unsigned int) sizeof(ss_lit_40), 0xEBU, ss_buf_40);
        case 41: return ss_decode(ss_lit_41, (unsigned int) sizeof(ss_lit_41), 0x10U, ss_buf_41);
        case 42: return ss_decode(ss_lit_42, (unsigned int) sizeof(ss_lit_42), 0xDDU, ss_buf_42);
        case 43: return ss_decode(ss_lit_43, (unsigned int) sizeof(ss_lit_43), 0x39U, ss_buf_43);
        case 44: return ss_decode(ss_lit_44, (unsigned int) sizeof(ss_lit_44), 0x32U, ss_buf_44);
        case 45: return ss_decode(ss_lit_45, (unsigned int) sizeof(ss_lit_45), 0x8EU, ss_buf_45);
        case 46: return ss_decode(ss_lit_46, (unsigned int) sizeof(ss_lit_46), 0xDFU, ss_buf_46);
        case 47: return ss_decode(ss_lit_47, (unsigned int) sizeof(ss_lit_47), 0x96U, ss_buf_47);
        case 48: return ss_decode(ss_lit_48, (unsigned int) sizeof(ss_lit_48), 0xF2U, ss_buf_48);
        case 49: return ss_decode(ss_lit_49, (unsigned int) sizeof(ss_lit_49), 0xEBU, ss_buf_49);
        case 50: return ss_decode(ss_lit_50, (unsigned int) sizeof(ss_lit_50), 0x47U, ss_buf_50);
        case 51: return ss_decode(ss_lit_51, (unsigned int) sizeof(ss_lit_51), 0x40U, ss_buf_51);
        case 52: return ss_decode(ss_lit_52, (unsigned int) sizeof(ss_lit_52), 0x9CU, ss_buf_52);
        case 53: return ss_decode(ss_lit_53, (unsigned int) sizeof(ss_lit_53), 0x87U, ss_buf_53);
        case 54: return ss_decode(ss_lit_54, (unsigned int) sizeof(ss_lit_54), 0xBAU, ss_buf_54);
        case 55: return ss_decode(ss_lit_55, (unsigned int) sizeof(ss_lit_55), 0xBEU, ss_buf_55);
        case 56: return ss_decode(ss_lit_56, (unsigned int) sizeof(ss_lit_56), 0x75U, ss_buf_56);
        case 57: return ss_decode(ss_lit_57, (unsigned int) sizeof(ss_lit_57), 0xBBU, ss_buf_57);
        case 58: return ss_decode(ss_lit_58, (unsigned int) sizeof(ss_lit_58), 0x2AU, ss_buf_58);
        case 59: return ss_decode(ss_lit_59, (unsigned int) sizeof(ss_lit_59), 0x47U, ss_buf_59);
        case 60: return ss_decode(ss_lit_60, (unsigned int) sizeof(ss_lit_60), 0xAEU, ss_buf_60);
        case 61: return ss_decode(ss_lit_61, (unsigned int) sizeof(ss_lit_61), 0xBDU, ss_buf_61);
        case 62: return ss_decode(ss_lit_62, (unsigned int) sizeof(ss_lit_62), 0xE7U, ss_buf_62);
        case 63: return ss_decode(ss_lit_63, (unsigned int) sizeof(ss_lit_63), 0xFCU, ss_buf_63);
        case 64: return ss_decode(ss_lit_64, (unsigned int) sizeof(ss_lit_64), 0x60U, ss_buf_64);
        case 65: return ss_decode(ss_lit_65, (unsigned int) sizeof(ss_lit_65), 0xEBU, ss_buf_65);
        case 66: return ss_decode(ss_lit_66, (unsigned int) sizeof(ss_lit_66), 0x05U, ss_buf_66);
        case 67: return ss_decode(ss_lit_67, (unsigned int) sizeof(ss_lit_67), 0xE8U, ss_buf_67);
        case 68: return ss_decode(ss_lit_68, (unsigned int) sizeof(ss_lit_68), 0x94U, ss_buf_68);
        case 69: return ss_decode(ss_lit_69, (unsigned int) sizeof(ss_lit_69), 0x56U, ss_buf_69);
        case 70: return ss_decode(ss_lit_70, (unsigned int) sizeof(ss_lit_70), 0x91U, ss_buf_70);
        case 71: return ss_decode(ss_lit_71, (unsigned int) sizeof(ss_lit_71), 0xE7U, ss_buf_71);
        case 72: return ss_decode(ss_lit_72, (unsigned int) sizeof(ss_lit_72), 0x49U, ss_buf_72);
        case 73: return ss_decode(ss_lit_73, (unsigned int) sizeof(ss_lit_73), 0xBBU, ss_buf_73);
        case 74: return ss_decode(ss_lit_74, (unsigned int) sizeof(ss_lit_74), 0x04U, ss_buf_74);
        case 75: return ss_decode(ss_lit_75, (unsigned int) sizeof(ss_lit_75), 0xB5U, ss_buf_75);
        case 76: return ss_decode(ss_lit_76, (unsigned int) sizeof(ss_lit_76), 0x4EU, ss_buf_76);
        case 77: return ss_decode(ss_lit_77, (unsigned int) sizeof(ss_lit_77), 0xF4U, ss_buf_77);
        case 78: return ss_decode(ss_lit_78, (unsigned int) sizeof(ss_lit_78), 0x27U, ss_buf_78);
        case 79: return ss_decode(ss_lit_79, (unsigned int) sizeof(ss_lit_79), 0x07U, ss_buf_79);
        case 80: return ss_decode(ss_lit_80, (unsigned int) sizeof(ss_lit_80), 0xF8U, ss_buf_80);
        case 81: return ss_decode(ss_lit_81, (unsigned int) sizeof(ss_lit_81), 0x04U, ss_buf_81);
        case 82: return ss_decode(ss_lit_82, (unsigned int) sizeof(ss_lit_82), 0x60U, ss_buf_82);
        case 83: return ss_decode(ss_lit_83, (unsigned int) sizeof(ss_lit_83), 0xE0U, ss_buf_83);
        case 84: return ss_decode(ss_lit_84, (unsigned int) sizeof(ss_lit_84), 0xE4U, ss_buf_84);
        case 85: return ss_decode(ss_lit_85, (unsigned int) sizeof(ss_lit_85), 0x90U, ss_buf_85);
        case 86: return ss_decode(ss_lit_86, (unsigned int) sizeof(ss_lit_86), 0xB5U, ss_buf_86);
        case 87: return ss_decode(ss_lit_87, (unsigned int) sizeof(ss_lit_87), 0x8AU, ss_buf_87);
        case 88: return ss_decode(ss_lit_88, (unsigned int) sizeof(ss_lit_88), 0xD0U, ss_buf_88);
        case 89: return ss_decode(ss_lit_89, (unsigned int) sizeof(ss_lit_89), 0x92U, ss_buf_89);
        case 90: return ss_decode(ss_lit_90, (unsigned int) sizeof(ss_lit_90), 0xEEU, ss_buf_90);
        case 91: return ss_decode(ss_lit_91, (unsigned int) sizeof(ss_lit_91), 0x08U, ss_buf_91);
        case 92: return ss_decode(ss_lit_92, (unsigned int) sizeof(ss_lit_92), 0xA6U, ss_buf_92);
        case 93: return ss_decode(ss_lit_93, (unsigned int) sizeof(ss_lit_93), 0x31U, ss_buf_93);
        case 94: return ss_decode(ss_lit_94, (unsigned int) sizeof(ss_lit_94), 0xA3U, ss_buf_94);
        case 95: return ss_decode(ss_lit_95, (unsigned int) sizeof(ss_lit_95), 0x4CU, ss_buf_95);
        case 96: return ss_decode(ss_lit_96, (unsigned int) sizeof(ss_lit_96), 0x95U, ss_buf_96);
        case 97: return ss_decode(ss_lit_97, (unsigned int) sizeof(ss_lit_97), 0x12U, ss_buf_97);
        case 98: return ss_decode(ss_lit_98, (unsigned int) sizeof(ss_lit_98), 0xEAU, ss_buf_98);
        case 99: return ss_decode(ss_lit_99, (unsigned int) sizeof(ss_lit_99), 0xCDU, ss_buf_99);
        case 100: return ss_decode(ss_lit_100, (unsigned int) sizeof(ss_lit_100), 0xD9U, ss_buf_100);
        case 101: return ss_decode(ss_lit_101, (unsigned int) sizeof(ss_lit_101), 0xD5U, ss_buf_101);
        case 102: return ss_decode(ss_lit_102, (unsigned int) sizeof(ss_lit_102), 0xB5U, ss_buf_102);
        case 103: return ss_decode(ss_lit_103, (unsigned int) sizeof(ss_lit_103), 0x1FU, ss_buf_103);
        case 104: return ss_decode(ss_lit_104, (unsigned int) sizeof(ss_lit_104), 0x44U, ss_buf_104);
        case 105: return ss_decode(ss_lit_105, (unsigned int) sizeof(ss_lit_105), 0xDCU, ss_buf_105);
        case 106: return ss_decode(ss_lit_106, (unsigned int) sizeof(ss_lit_106), 0x51U, ss_buf_106);
        case 107: return ss_decode(ss_lit_107, (unsigned int) sizeof(ss_lit_107), 0x76U, ss_buf_107);
        default: return ss_decode(ss_lit_0, 0U, 0U, ss_buf_0);
    }
}

static int sushuo_env_truthy(const char *name) {
    const char *value = getenv(name);
    if (value == NULL || value[0] == '\0') {
        return 0;
    }
    return strcmp(value, SS(0)) != 0
            && strcmp(value, SS(1)) != 0
            && strcmp(value, SS(2)) != 0
            && strcmp(value, SS(3)) != 0
            && strcmp(value, SS(4)) != 0;
}

static int sushuo_native_debugger_present(void) {
    if (sushuo_env_truthy(SS(5))) {
        return 1;
    }
#ifdef _WIN32
    if (IsDebuggerPresent()) {
        return 1;
    }
    BOOL remote_debugger = FALSE;
    if (CheckRemoteDebuggerPresent(GetCurrentProcess(), &remote_debugger) && remote_debugger) {
        return 1;
    }
#endif
    return 0;
}

enum {
    ENCODED_MARKER = 0x53535632,
    PACKED_MARKER = 0x53535033,
    RESOURCE_MARKER = 0x53535234,
    RESOURCE_VERSION = 4,
    RESOURCE_SALT = 0x6A09E667,
    SEAL_SALT = 0x4B455931,
    RESOURCE_HEADER_BYTES = 28,
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

static uint64_t rotl64(uint64_t value, uint32_t shift) {
    shift &= 63U;
    return shift == 0U ? value : (value << shift) | (value >> (64U - shift));
}

static jint mix32(jint input);

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

static uint64_t mix64(uint64_t value) {
    value ^= value >> 30U;
    value *= UINT64_C(0xBF58476D1CE4E5B9);
    value ^= value >> 27U;
    value *= UINT64_C(0x94D049BB133111EB);
    value ^= value >> 31U;
    return value == 0U ? UINT64_C(0x13579BDF2468ACE1) : value;
}

static jint dynamic_string_key_native(jint key, jint site, jint salt, const char *owner, const char *method) {
    uint32_t mixed = (uint32_t) key ^ rotl32((uint32_t) site * 0x45D9F3BU, 7U) ^ (uint32_t) salt;
    mixed ^= (uint32_t) java_hash_utf(owner);
    mixed = rotl32(mixed + 0x7F4A7C15U, 11U);
    mixed ^= (uint32_t) java_hash_utf(method) * 0x5BD1E995U;
    mixed ^= mixed >> 16U;
    mixed *= 0x85EBCA6BU;
    mixed ^= mixed >> 13U;
    mixed *= 0xC2B2AE35U;
    return (jint) (mixed ^ (mixed >> 16U));
}

static jint dynamic_int_key_native(jint key, jint site, jint salt, const char *owner, const char *method) {
    uint32_t mixed = (uint32_t) key ^ rotl32((uint32_t) site * 0x27D4EB2DU, 9U) ^ (uint32_t) salt;
    mixed ^= (uint32_t) java_hash_utf(owner);
    mixed = rotl32(mixed + 0x165667B1U, 7U);
    mixed ^= (uint32_t) java_hash_utf(method) * 0x85EBCA6BU;
    mixed ^= mixed >> 15U;
    mixed *= 0xC2B2AE35U;
    return (jint) (mixed ^ (mixed >> 16U));
}

static jlong dynamic_long_key_native(jlong key, jint site, jint salt, const char *owner, const char *method) {
    uint64_t mixed = (uint64_t) key ^ ((uint64_t) (uint32_t) site << 32U) ^ (uint32_t) salt;
    mixed ^= (uint32_t) java_hash_utf(owner);
    mixed = rotl64(mixed + UINT64_C(0x9E3779B97F4A7C15), 17U);
    mixed ^= (uint64_t) (uint32_t) java_hash_utf(method) * UINT64_C(0xBF58476D1CE4E5B9);
    mixed ^= mixed >> 30U;
    mixed *= UINT64_C(0xBF58476D1CE4E5B9);
    mixed ^= mixed >> 27U;
    mixed *= UINT64_C(0x94D049BB133111EB);
    return (jlong) (mixed ^ (mixed >> 31U));
}

static jint constant_mask32_native(jint kind, const char *owner, const char *method,
                                   jint key, jint site, jint salt) {
    uint32_t state = 0x434B3332U ^ (uint32_t) kind ^ (uint32_t) key ^ (uint32_t) salt;
    state ^= rotl32((uint32_t) site * 0x45D9F3BU, 7U);
    state ^= (uint32_t) java_hash_utf(owner);
    state = (uint32_t) mix32((jint) (state ^ native_secret_word(0)));
    state ^= rotl32((uint32_t) java_hash_utf(method), 11U);
    state = (uint32_t) mix32((jint) (state ^ native_secret_word(1)));
    state ^= rotl32(native_secret_word(2), (uint32_t) site & 31U);
    state = (uint32_t) mix32((jint) (state + native_secret_word(3)
            + (uint32_t) strlen(owner) * 0x27D4EB2DU));
    return mix32((jint) (state ^ (uint32_t) strlen(method) * 0x9E3779B9U));
}

static jlong constant_mask64_native(jint kind, const char *owner, const char *method,
                                    jlong key, jint site, jint salt) {
    uint64_t state = UINT64_C(0x434B36344A4E494C) ^ (uint64_t) key
            ^ ((uint64_t) (uint32_t) kind << 48U)
            ^ (uint32_t) salt ^ ((uint64_t) (uint32_t) site << 32U);
    state ^= (uint64_t) (uint32_t) java_hash_utf(owner) * UINT64_C(0x9E3779B97F4A7C15);
    state ^= (uint64_t) (uint32_t) java_hash_utf(method) * UINT64_C(0xBF58476D1CE4E5B9);
    state ^= ((uint64_t) native_secret_word(0) << 32U) ^ native_secret_word(1);
    state = mix64(state);
    state ^= rotl64(((uint64_t) native_secret_word(2) << 32U) ^ native_secret_word(3),
            (uint32_t) site & 63U);
    state ^= ((uint64_t) strlen(owner) << 17U) ^ (uint64_t) strlen(method) * UINT64_C(0x94D049BB133111EB);
    return (jlong) mix64(state);
}

static jint resource_format(jint resource_hash, jint total_length) {
    uint32_t value = 0x52464D34U ^ (uint32_t) resource_hash ^ (uint32_t) total_length;
    value ^= native_secret_word(0);
    value ^= rotl32(native_secret_word(1), 9U);
    value ^= rotl32(native_secret_word(2), (uint32_t) total_length & 31U);
    return mix32((jint) (value ^ native_secret_word(3)));
}

static jint resource_header_mask(jint resource_hash, jint total_length, jint slot) {
    uint32_t value = 0x56485244U ^ (uint32_t) resource_hash;
    value ^= rotl32((uint32_t) total_length * 0x45D9F3BU, ((uint32_t) slot + 5U) & 31U);
    value ^= native_secret_word(slot & 3);
    value ^= rotl32(native_secret_word((slot + 1) & 3), ((uint32_t) slot * 7U + 3U) & 31U);
    value ^= (uint32_t) slot * 0x9E3779B9U;
    value = rotl32(value + 0x7F4A7C15U, 9U);
    value ^= value >> 16U;
    value *= 0x85EBCA6BU;
    value ^= value >> 13U;
    value *= 0xC2B2AE35U;
    value ^= value >> 16U;
    return (jint) (value == 0U ? 0x13579BDFU : value);
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
    jclass cls = (*env)->FindClass(env, SS(6));
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static int sushuo_native_security_check(JNIEnv *env) {
    if (sushuo_native_debugger_present()) {
        if (env != NULL) {
            throw_illegal_state(env, SS(7));
        }
        return 0;
    }
    return 1;
}

static jbyteArray load_resource_bytes(JNIEnv *env, jstring resource_name) {
    jclass bridge = (*env)->FindClass(env, sushuo_bridge_name_plain());
    jclass class_cls = (*env)->FindClass(env, SS(8));
    if (bridge == NULL || class_cls == NULL) {
        return NULL;
    }
    jmethodID get_resource = (*env)->GetMethodID(env, class_cls, SS(9),
            SS(10));
    if (get_resource == NULL) {
        return NULL;
    }
    jobject stream = (*env)->CallObjectMethod(env, bridge, get_resource, resource_name);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    if (stream == NULL) {
        throw_illegal_state(env, SS(7));
        return NULL;
    }

    jclass input_cls = (*env)->FindClass(env, SS(11));
    jmethodID read_mid = input_cls == NULL ? NULL : (*env)->GetMethodID(env, input_cls, SS(12), SS(13));
    jmethodID close_mid = input_cls == NULL ? NULL : (*env)->GetMethodID(env, input_cls, SS(14), SS(15));
    jclass baos_cls = (*env)->FindClass(env, SS(16));
    jmethodID baos_ctor = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, SS(17), SS(15));
    jmethodID write_mid = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, SS(18), SS(19));
    jmethodID to_byte_array_mid = baos_cls == NULL ? NULL : (*env)->GetMethodID(env, baos_cls, SS(20), SS(21));
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
            jclass cls = (*env)->FindClass(env, SS(22));
            if (cls != NULL) {
                (*env)->ThrowNew(env, cls, SS(7));
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
        jclass cls = (*env)->FindClass(env, SS(22));
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, SS(7));
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

static int ensure_value_type(JNIEnv *env, jobject value, const char *class_name) {
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    if (value == NULL) {
        throw_illegal_state(env, SS(7));
        return 0;
    }
    jclass cls = (*env)->FindClass(env, class_name);
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    if (!(*env)->IsInstanceOf(env, value, cls)) {
        throw_illegal_state(env, SS(7));
        return 0;
    }
    return 1;
}

static jint as_int(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(23))) {
        return 0;
    }
    jclass cls = (*env)->FindClass(env, SS(23));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(24), SS(25));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jint result = (*env)->CallIntMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? 0 : result;
}

static jlong as_long(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(23))) {
        return 0;
    }
    jclass cls = (*env)->FindClass(env, SS(23));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(26), SS(27));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jlong result = (*env)->CallLongMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? 0 : result;
}

static jfloat as_float(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(23))) {
        return 0.0f;
    }
    jclass cls = (*env)->FindClass(env, SS(23));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0.0f;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(28), SS(29));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return 0.0f;
    }
    jfloat result = (*env)->CallFloatMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? 0.0f : result;
}

static jdouble as_double(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(23))) {
        return 0.0;
    }
    jclass cls = (*env)->FindClass(env, SS(23));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0.0;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(30), SS(31));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return 0.0;
    }
    jdouble result = (*env)->CallDoubleMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? 0.0 : result;
}

static jboolean as_boolean(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(32))) {
        return JNI_FALSE;
    }
    jclass cls = (*env)->FindClass(env, SS(32));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return JNI_FALSE;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(33), SS(34));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return JNI_FALSE;
    }
    jboolean result = (*env)->CallBooleanMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : result;
}

static jchar as_char(JNIEnv *env, jobject value) {
    if (!ensure_value_type(env, value, SS(35))) {
        return 0;
    }
    jclass cls = (*env)->FindClass(env, SS(35));
    if (cls == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jmethodID mid = (*env)->GetMethodID(env, cls, SS(36), SS(37));
    if (mid == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    jchar result = (*env)->CallCharMethod(env, value, mid);
    return (*env)->ExceptionCheck(env) ? 0 : result;
}

static jobject box_int(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, SS(38));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(40));
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_boolean(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, SS(32));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(41));
    return (*env)->CallStaticObjectMethod(env, cls, mid, value != 0 ? JNI_TRUE : JNI_FALSE);
}

static jobject box_byte(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, SS(42));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(43));
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jbyte) value);
}

static jobject box_short(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, SS(44));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(45));
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jshort) value);
}

static jobject box_char(JNIEnv *env, jint value) {
    jclass cls = (*env)->FindClass(env, SS(35));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(46));
    return (*env)->CallStaticObjectMethod(env, cls, mid, (jchar) value);
}

static jobject box_long(JNIEnv *env, jlong value) {
    jclass cls = (*env)->FindClass(env, SS(47));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(48));
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_float(JNIEnv *env, jfloat value) {
    jclass cls = (*env)->FindClass(env, SS(49));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(50));
    return (*env)->CallStaticObjectMethod(env, cls, mid, value);
}

static jobject box_double(JNIEnv *env, jdouble value) {
    jclass cls = (*env)->FindClass(env, SS(51));
    jmethodID mid = (*env)->GetStaticMethodID(env, cls, SS(39), SS(52));
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
    jclass cls = (*env)->FindClass(env, SS(53));
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, SS(7));
    }
}

static jclass primitive_class(JNIEnv *env, char kind) {
    const char *class_name = NULL;
    const char *field_sig = SS(54);
    switch (kind) {
        case 'V':
            class_name = SS(55);
            break;
        case 'Z':
            class_name = SS(32);
            break;
        case 'C':
            class_name = SS(35);
            break;
        case 'B':
            class_name = SS(42);
            break;
        case 'S':
            class_name = SS(44);
            break;
        case 'I':
            class_name = SS(38);
            break;
        case 'J':
            class_name = SS(47);
            break;
        case 'F':
            class_name = SS(49);
            break;
        case 'D':
            class_name = SS(51);
            break;
        default:
            return NULL;
    }
    jclass wrapper = (*env)->FindClass(env, class_name);
    if (wrapper == NULL) {
        return NULL;
    }
    jfieldID type_field = (*env)->GetStaticFieldID(env, wrapper, SS(56), field_sig);
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
    jclass cls_class = (*env)->FindClass(env, SS(8));
    if (cls_class == NULL) {
        return NULL;
    }
    jmethodID for_name = (*env)->GetStaticMethodID(env, cls_class, SS(57), SS(58));
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
    jclass class_cls = (*env)->FindClass(env, SS(8));
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
    jclass class_cls = (*env)->FindClass(env, SS(8));
    jmethodID get_superclass = (*env)->GetMethodID(env, class_cls, SS(59), SS(60));
    jmethodID getter = method
            ? (*env)->GetMethodID(env, class_cls, SS(61),
                    SS(62))
            : (*env)->GetMethodID(env, class_cls, SS(63),
                    SS(64));
    jobject cursor = start_class;
    jobject member = NULL;
    jclass no_such_method = (*env)->FindClass(env, SS(65));
    jclass no_such_field = (*env)->FindClass(env, SS(66));

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
            jclass method_cls = (*env)->FindClass(env, SS(67));
            jmethodID set_accessible = (*env)->GetMethodID(env, method_cls, SS(68), SS(69));
            jmethodID invoke = (*env)->GetMethodID(env, method_cls, SS(70),
                    SS(71));
            jobjectArray coerced_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, SS(72)), NULL);
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
            jclass field_cls = (*env)->FindClass(env, SS(73));
            jmethodID set_accessible = (*env)->GetMethodID(env, field_cls, SS(68), SS(69));
            if (!(*env)->ExceptionCheck(env)) {
                (*env)->CallVoidMethod(env, field, set_accessible, JNI_TRUE);
            }
            if (write) {
                jmethodID set = (*env)->GetMethodID(env, field_cls, SS(74),
                        SS(75));
                jobject coerced = coerce_value(env, descriptor_utf[0], value);
                if (!(*env)->ExceptionCheck(env)) {
                    (*env)->CallVoidMethod(env, field, set, target, coerced);
                }
            } else {
                jmethodID get = (*env)->GetMethodID(env, field_cls, SS(76),
                        SS(77));
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
        throw_illegal_state(env, SS(7));
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
        throw_illegal_state(env, SS(7));
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
    jclass string_cls = (*env)->FindClass(env, SS(78));
    jclass charset_cls = (*env)->FindClass(env, SS(79));
    if (string_cls == NULL || charset_cls == NULL) {
        return NULL;
    }
    jfieldID utf8_field = (*env)->GetStaticFieldID(env, charset_cls, SS(80), SS(81));
    jmethodID ctor = (*env)->GetMethodID(env, string_cls, SS(17), SS(82));
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

static jint mix32(jint input) {
    uint32_t value = (uint32_t) input;
    value ^= value >> 16U;
    value *= 0x7FEB352DU;
    value ^= value >> 15U;
    value *= 0x846CA68BU;
    value ^= value >> 16U;
    return value == 0U ? (jint) 0x13579BDFU : (jint) value;
}

static jint context_tag(jint key, jint resource_hash, jint site, jint owner_hash,
                        jint method_hash, jint code_length, jint constant_count,
                        jint return_kind) {
    uint32_t value = (uint32_t) key ^ (uint32_t) resource_hash ^ RESOURCE_SALT;
    value ^= rotl32((uint32_t) site * 0x45D9F3BU, 7U);
    value ^= rotl32((uint32_t) owner_hash, 11U);
    value ^= rotl32((uint32_t) method_hash, 17U);
    value ^= rotl32((uint32_t) code_length * 0x27D4EB2DU, 5U);
    value ^= rotl32((uint32_t) constant_count * 0x9E3779B9U, 13U);
    value ^= (uint32_t) return_kind * 0x5BD1E995U;
    return mix32((jint) value);
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
    if (strcmp(owner, SS(83)) == 0) {
        return 1;
    }
    if (strcmp(method, SS(84)) == 0 || strcmp(method, SS(85)) == 0 || strcmp(method, SS(86)) == 0) {
        return 1;
    }
    if (method[0] == '_' && method[1] == 'v' && method[2] == 'p' && method[3] == '$') {
        return 1;
    }
    return 0;
}

static int starts_with_utf(const char *value, const char *prefix) {
    return strncmp(value, prefix, strlen(prefix)) == 0;
}

static int reflective_owner(const char *owner) {
    return starts_with_utf(owner, SS(87))
            || starts_with_utf(owner, SS(88))
            || starts_with_utf(owner, SS(89))
            || starts_with_utf(owner, SS(90));
}

static int frame_owner_is_reflective(JNIEnv *env, jobjectArray trace, jsize index, jsize count,
                                     jmethodID get_class_name) {
    if (index < 0 || index >= count) {
        return 0;
    }
    jobject element = (*env)->GetObjectArrayElement(env, trace, index);
    if (element == NULL) {
        return 0;
    }
    jstring owner_string = (jstring) (*env)->CallObjectMethod(env, element, get_class_name);
    if (owner_string == NULL || (*env)->ExceptionCheck(env)) {
        return 1;
    }
    const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
    if (owner == NULL) {
        return 1;
    }
    int reflective = reflective_owner(owner);
    (*env)->ReleaseStringUTFChars(env, owner_string, owner);
    return reflective;
}

static int same_package_context_after_reflection(JNIEnv *env, jobjectArray trace, jsize index, jsize count,
                                                jmethodID get_class_name, const char *expected_owner) {
    if (!frame_owner_is_reflective(env, trace, index + 1, count, get_class_name)) {
        return 1;
    }
    const char *last_dot = strrchr(expected_owner, '.');
    if (last_dot == NULL || last_dot == expected_owner) {
        return 0;
    }
    size_t prefix_len = (size_t) (last_dot - expected_owner + 1);
    for (jsize i = index + 2; i < count; i++) {
        jobject element = (*env)->GetObjectArrayElement(env, trace, i);
        if (element == NULL) {
            continue;
        }
        jstring owner_string = (jstring) (*env)->CallObjectMethod(env, element, get_class_name);
        if (owner_string == NULL || (*env)->ExceptionCheck(env)) {
            return 0;
        }
        const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
        if (owner == NULL) {
            return 0;
        }
        int allowed = strncmp(owner, expected_owner, prefix_len) == 0
                && !reflective_owner(owner)
                && strcmp(owner, SS(83)) != 0;
        (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        if (allowed) {
            return 1;
        }
    }
    return 0;
}

static jint current_call_binding_match(JNIEnv *env, jint site,
                                       jint expected_owner_hash, jint expected_method_hash,
                                       int require_match,
                                       jint *owner_hash, jint *method_hash) {
    jclass thread_cls = (*env)->FindClass(env, SS(91));
    jclass ste_cls = (*env)->FindClass(env, SS(92));
    if (thread_cls == NULL || ste_cls == NULL) {
        return 0;
    }
    jmethodID current_thread = (*env)->GetStaticMethodID(env, thread_cls, SS(93), SS(94));
    jmethodID get_stack = (*env)->GetMethodID(env, thread_cls, SS(84), SS(95));
    jmethodID get_class_name = (*env)->GetMethodID(env, ste_cls, SS(96), SS(97));
    jmethodID get_method_name = (*env)->GetMethodID(env, ste_cls, SS(98), SS(97));
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
        jint current_owner_hash = java_hash_utf(owner);
        jint current_method_hash = java_hash_utf(method);
        jint token = 0;
        if (require_match) {
            if (current_owner_hash == expected_owner_hash
                    && current_method_hash == expected_method_hash
                    && same_package_context_after_reflection(env, trace, i, count, get_class_name, owner)) {
                if (owner_hash != NULL) {
                    *owner_hash = current_owner_hash;
                }
                if (method_hash != NULL) {
                    *method_hash = current_method_hash;
                }
                token = guard_token_utf(owner, method, site);
            }
        } else if (!skip) {
            if (owner_hash != NULL) {
                *owner_hash = current_owner_hash;
            }
            if (method_hash != NULL) {
                *method_hash = current_method_hash;
            }
            token = guard_token_utf(owner, method, site);
        }
        (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        (*env)->ReleaseStringUTFChars(env, method_string, method);
        if (token != 0) {
            return token;
        }
    }
    return 0;
}

static jint current_call_binding(JNIEnv *env, jint site, jint *owner_hash, jint *method_hash) {
    return current_call_binding_match(env, site, 0, 0, 0, owner_hash, method_hash);
}

static jint current_expected_call_binding(JNIEnv *env, jint site,
                                          jint expected_owner_hash, jint expected_method_hash,
                                          jint *owner_hash, jint *method_hash) {
    return current_call_binding_match(env, site, expected_owner_hash, expected_method_hash, 1,
            owner_hash, method_hash);
}

static jint current_call_token(JNIEnv *env, jint site) {
    return current_call_binding(env, site, NULL, NULL);
}

static int stack_contains_frame(JNIEnv *env, const char *expected_owner, const char *expected_method) {
    jclass thread_cls = (*env)->FindClass(env, SS(91));
    jclass ste_cls = (*env)->FindClass(env, SS(92));
    if (thread_cls == NULL || ste_cls == NULL) {
        return 0;
    }
    jmethodID current_thread = (*env)->GetStaticMethodID(env, thread_cls, SS(93), SS(94));
    jmethodID get_stack = (*env)->GetMethodID(env, thread_cls, SS(84), SS(95));
    jmethodID get_class_name = (*env)->GetMethodID(env, ste_cls, SS(96), SS(97));
    jmethodID get_method_name = (*env)->GetMethodID(env, ste_cls, SS(98), SS(97));
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
        int matched = strcmp(owner, expected_owner) == 0 && strcmp(method, expected_method) == 0;
        (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        (*env)->ReleaseStringUTFChars(env, method_string, method);
        if (matched) {
            return 1;
        }
    }
    return 0;
}

static int stack_contains_owner(JNIEnv *env, const char *expected_owner) {
    jclass thread_cls = (*env)->FindClass(env, SS(91));
    jclass ste_cls = (*env)->FindClass(env, SS(92));
    if (thread_cls == NULL || ste_cls == NULL || expected_owner == NULL) {
        return 0;
    }
    jmethodID current_thread = (*env)->GetStaticMethodID(env, thread_cls, SS(93), SS(94));
    jmethodID get_stack = (*env)->GetMethodID(env, thread_cls, SS(84), SS(95));
    jmethodID get_class_name = (*env)->GetMethodID(env, ste_cls, SS(96), SS(97));
    if (current_thread == NULL || get_stack == NULL || get_class_name == NULL) {
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
        if (owner_string == NULL || (*env)->ExceptionCheck(env)) {
            return 0;
        }
        const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
        if (owner == NULL) {
            return 0;
        }
        int matched = strcmp(owner, expected_owner) == 0;
        (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        if (matched) {
            return 1;
        }
    }
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
        throw_illegal_state(env, SS(7));
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
        throw_illegal_state(env, SS(7));
        return NULL;
    }
    jclass object_cls = (*env)->FindClass(env, SS(72));
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
                    throw_illegal_state(env, SS(7));
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
                throw_illegal_state(env, SS(7));
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

static jstring class_name_string(JNIEnv *env, jclass owner_class) {
    jclass class_cls = (*env)->FindClass(env, SS(8));
    if (class_cls == NULL || owner_class == NULL) {
        return NULL;
    }
    jmethodID get_name = (*env)->GetMethodID(env, class_cls, SS(99), SS(97));
    if (get_name == NULL) {
        return NULL;
    }
    return (jstring) (*env)->CallObjectMethod(env, owner_class, get_name);
}

static jint SUSHUO_CALL sushuo_native_key_i(
        JNIEnv *env, jclass ignored, jint kind, jclass owner_class,
        jstring indy_name, jint key, jint site, jint salt) {
    (void) ignored;
    if (!sushuo_native_security_check(env)) {
        return 0;
    }
    jstring owner_string = class_name_string(env, owner_class);
    if (owner_string == NULL || indy_name == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
    const char *name = (*env)->GetStringUTFChars(env, indy_name, NULL);
    if (owner == NULL || name == NULL) {
        if (owner != NULL) {
            (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        }
        if (name != NULL) {
            (*env)->ReleaseStringUTFChars(env, indy_name, name);
        }
        return 0;
    }
    jint dynamic = kind == 1
            ? dynamic_string_key_native(key, site, salt, owner, name)
            : dynamic_int_key_native(key, site, salt, owner, name);
    jint result = dynamic ^ constant_mask32_native(kind, owner, name, key, site, salt);
    int valid_context = stack_contains_owner(env, owner);
    (*env)->ReleaseStringUTFChars(env, owner_string, owner);
    (*env)->ReleaseStringUTFChars(env, indy_name, name);
    if (!valid_context) {
        throw_illegal_state(env, SS(7));
        return 0;
    }
    return result;
}

static jlong SUSHUO_CALL sushuo_native_key_l(
        JNIEnv *env, jclass ignored, jint kind, jclass owner_class,
        jstring indy_name, jlong key, jint site, jint salt) {
    (void) ignored;
    if (!sushuo_native_security_check(env)) {
        return 0;
    }
    jstring owner_string = class_name_string(env, owner_class);
    if (owner_string == NULL || indy_name == NULL || (*env)->ExceptionCheck(env)) {
        return 0;
    }
    const char *owner = (*env)->GetStringUTFChars(env, owner_string, NULL);
    const char *name = (*env)->GetStringUTFChars(env, indy_name, NULL);
    if (owner == NULL || name == NULL) {
        if (owner != NULL) {
            (*env)->ReleaseStringUTFChars(env, owner_string, owner);
        }
        if (name != NULL) {
            (*env)->ReleaseStringUTFChars(env, indy_name, name);
        }
        return 0;
    }
    jlong result = dynamic_long_key_native(key, site, salt, owner, name)
            ^ constant_mask64_native(kind, owner, name, key, site, salt);
    int valid_context = stack_contains_owner(env, owner);
    (*env)->ReleaseStringUTFChars(env, owner_string, owner);
    (*env)->ReleaseStringUTFChars(env, indy_name, name);
    if (!valid_context) {
        throw_illegal_state(env, SS(7));
        return 0;
    }
    return result;
}

static jobject SUSHUO_CALL sushuo_native_vm(
        JNIEnv *env, jclass ignored, jobjectArray program, jobjectArray args) {
    (void) ignored;
    if (!sushuo_native_security_check(env)) {
        return NULL;
    }

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
            jobject return_kind_obj = (*env)->GetObjectArrayElement(env, program, 3);
            jobject site_obj = (*env)->GetObjectArrayElement(env, program, 8);
            jobject constant_count_obj = (*env)->GetObjectArrayElement(env, program, 9);
            jobject expected_owner_hash_obj = (*env)->GetObjectArrayElement(env, program, 10);
            jobject expected_method_hash_obj = (*env)->GetObjectArrayElement(env, program, 11);
            jint return_kind_value = as_int(env, return_kind_obj);
            jint site = as_int(env, site_obj);
            jint constant_count = as_int(env, constant_count_obj);
            jint expected_owner_hash = as_int(env, expected_owner_hash_obj);
            jint expected_method_hash = as_int(env, expected_method_hash_obj);
            jint owner_hash = 0;
            jint method_hash = 0;
            if ((*env)->ExceptionCheck(env)) {
                return NULL;
            }
            jint token = current_expected_call_binding(env, site, expected_owner_hash, expected_method_hash,
                    &owner_hash, &method_hash);
            if ((*env)->ExceptionCheck(env)) {
                return NULL;
            }
            if (token == 0 || owner_hash != expected_owner_hash || method_hash != expected_method_hash) {
                throw_illegal_state(env, SS(7));
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
                throw_illegal_state(env, SS(7));
                return NULL;
            }
            jint expected_format = resource_format(resource_hash, resource_len);
            jint header_format = read_be32(resource_bytes, 0) ^ resource_header_mask(resource_hash, resource_len, 0);
            jint version = read_be32(resource_bytes, 4) ^ resource_header_mask(resource_hash, resource_len, 1);
            jint nonce = read_be32(resource_bytes, 8) ^ resource_header_mask(resource_hash, resource_len, 2);
            jint key_tag = read_be32(resource_bytes, 12) ^ resource_header_mask(resource_hash, resource_len, 3);
            jint resource_code_len = read_be32(resource_bytes, 16) ^ resource_header_mask(resource_hash, resource_len, 4);
            jint payload_len = read_be32(resource_bytes, 20) ^ resource_header_mask(resource_hash, resource_len, 5);
            jint expected_context_tag = read_be32(resource_bytes, 24) ^ resource_header_mask(resource_hash, resource_len, 6);
            jint resource_key = key_tag ^ resource_hash ^ expected_format ^ nonce;
            jint actual_context_tag = context_tag(key, resource_hash, site, owner_hash, method_hash,
                    (jint) code_len, constant_count, return_kind_value);
            jint code_bytes_len = code_len * 4;
            if (header_format != expected_format || version != RESOURCE_VERSION
                    || resource_key != key || resource_code_len != code_len
                    || expected_context_tag != actual_context_tag
                    || payload_len < code_bytes_len
                    || payload_len < 0 || payload_len > resource_len - RESOURCE_HEADER_BYTES) {
                (*env)->ReleaseByteArrayElements(env, resource_array, resource_bytes, JNI_ABORT);
                throw_illegal_state(env, SS(7));
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
                jobjectArray call_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, SS(72)), NULL);
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
                jobjectArray call_args = (*env)->NewObjectArray(env, argc, (*env)->FindClass(env, SS(72)), NULL);
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
                            jclass cast_cls = (*env)->FindClass(env, SS(100));
                            if (cast_cls != NULL) {
                                (*env)->ThrowNew(env, cast_cls, SS(7));
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

static jint sushuo_native_vm_program_id(JNIEnv *env, jobjectArray program) {
    if (program == NULL) {
        return 0;
    }
    jsize program_len = (*env)->GetArrayLength(env, program);
    if ((*env)->ExceptionCheck(env) || program_len < 7) {
        return 0;
    }
    jobject first = (*env)->GetObjectArrayElement(env, program, 0);
    jint marker = first != NULL ? as_int(env, first) : 0;
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    jint index = marker == RESOURCE_MARKER ? 8 : marker == ENCODED_MARKER ? 6 : -1;
    if (index < 0 || index >= program_len) {
        return 0;
    }
    jobject id_obj = (*env)->GetObjectArrayElement(env, program, index);
    jint id = id_obj != NULL ? as_int(env, id_obj) : 0;
    return (*env)->ExceptionCheck(env) ? 0 : id;
}

static jint sushuo_native_vm_call_token(JNIEnv *env, jobjectArray program, jobjectArray args) {
    jsize arg_len = args == NULL ? 0 : (*env)->GetArrayLength(env, args);
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    uint32_t value = 0x56584D31U;
    value ^= (uint32_t) sushuo_native_vm_program_id(env, program) * 0x45D9F3BU;
    if ((*env)->ExceptionCheck(env)) {
        return 0;
    }
    value ^= rotl32((uint32_t) ((jint) arg_len * 0x27D4EB2D), 7U);
    return mix32((jint) value);
}

static jobject SUSHUO_CALL sushuo_native_vm_x(
        JNIEnv *env, jclass ignored, jobject program, jobject args, jint token) {
    if (program == NULL || args == NULL) {
        throw_illegal_state(env, SS(7));
        return NULL;
    }
    jobjectArray program_array = (jobjectArray) program;
    jobjectArray args_array = (jobjectArray) args;
    jint expected = sushuo_native_vm_call_token(env, program_array, args_array);
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    if (expected != token) {
        throw_illegal_state(env, SS(7));
        return NULL;
    }
    return sushuo_native_vm(env, ignored, program_array, args_array);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_8) != JNI_OK || env == NULL) {
        return JNI_ERR;
    }
    if (!sushuo_native_security_check(env)) {
        return JNI_ERR;
    }
    jclass bridge = (*env)->FindClass(env, sushuo_bridge_name_plain());
    if (bridge == NULL) {
        return JNI_ERR;
    }
    const char *native_method_name = NULL;
    const char *native_method_chars = NULL;
    const char *native_key_i_name = NULL;
    const char *native_key_i_chars = NULL;
    const char *native_key_l_name = NULL;
    const char *native_key_l_chars = NULL;
    jstring native_method_string = NULL;
    jstring native_key_i_string = NULL;
    jstring native_key_l_string = NULL;
    jfieldID native_method_field = (*env)->GetStaticFieldID(env, bridge, SS(101), SS(102));
    jfieldID native_key_i_field = (*env)->GetStaticFieldID(env, bridge, SS(103), SS(102));
    jfieldID native_key_l_field = (*env)->GetStaticFieldID(env, bridge, SS(104), SS(102));
    if (native_method_field != NULL) {
        native_method_string = (jstring) (*env)->GetStaticObjectField(env, bridge, native_method_field);
        if (native_method_string != NULL) {
            native_method_chars = (*env)->GetStringUTFChars(env, native_method_string, NULL);
            if (native_method_chars != NULL && native_method_chars[0] != '\0') {
                native_method_name = native_method_chars;
            }
        }
        if (native_key_i_field != NULL) {
            native_key_i_string = (jstring) (*env)->GetStaticObjectField(env, bridge, native_key_i_field);
            if (native_key_i_string != NULL) {
                native_key_i_chars = (*env)->GetStringUTFChars(env, native_key_i_string, NULL);
                if (native_key_i_chars != NULL && native_key_i_chars[0] != '\0') {
                    native_key_i_name = native_key_i_chars;
                }
            }
        }
        if (native_key_l_field != NULL) {
            native_key_l_string = (jstring) (*env)->GetStaticObjectField(env, bridge, native_key_l_field);
            if (native_key_l_string != NULL) {
                native_key_l_chars = (*env)->GetStringUTFChars(env, native_key_l_string, NULL);
                if (native_key_l_chars != NULL && native_key_l_chars[0] != '\0') {
                    native_key_l_name = native_key_l_chars;
                }
            }
        }
    } else {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return JNI_ERR;
    }
    if (native_method_name == NULL || native_key_i_name == NULL || native_key_l_name == NULL) {
        if (native_method_string != NULL && native_method_chars != NULL) {
            (*env)->ReleaseStringUTFChars(env, native_method_string, native_method_chars);
        }
        if (native_key_i_string != NULL && native_key_i_chars != NULL) {
            (*env)->ReleaseStringUTFChars(env, native_key_i_string, native_key_i_chars);
        }
        if (native_key_l_string != NULL && native_key_l_chars != NULL) {
            (*env)->ReleaseStringUTFChars(env, native_key_l_string, native_key_l_chars);
        }
        return JNI_ERR;
    }
    JNINativeMethod methods[] = {
            {(char *) native_method_name, (char *) SS(105), (void *) sushuo_native_vm_x},
            {(char *) native_key_i_name, (char *) SS(106), (void *) sushuo_native_key_i},
            {(char *) native_key_l_name, (char *) SS(107), (void *) sushuo_native_key_l}
    };
    jint registered = (*env)->RegisterNatives(env, bridge, methods, 3);
    if (native_method_string != NULL && native_method_chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, native_method_string, native_method_chars);
    }
    if (native_key_i_string != NULL && native_key_i_chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, native_key_i_string, native_key_i_chars);
    }
    if (native_key_l_string != NULL && native_key_l_chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, native_key_l_string, native_key_l_chars);
    }
    if (registered != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_8;
}
