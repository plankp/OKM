#include <math.h>
#include <time.h>
#include <stdlib.h>

float math_power(float base, float exp) {
    return powf(base, exp);
}

int math_random(void) {
    static _Bool call_srand = 1;
    if (call_srand) {
        srand(time(NULL));
        call_srand = 0;
    }
    return rand();
}

float math_sin(float rad) {
    return sinf(rad);
}

float math_cos(float rad) {
    return cosf(rad);
}

float math_tan(float rad) {
    return tanf(rad);
}

float math_asin(float r) {
    return asinf(r);
}

float math_acos(float r) {
    return acosf(r);
}

float math_atan(float r) {
    return atanf(r);
}

float math_atan2(float y, float x) {
    return atan2f(y, x);
}

float math_sinh(float x) {
    return sinhf(x);
}

float math_cosh(float x) {
    return coshf(x);
}

float math_tanh(float x) {
    return tanhf(x);
}

float math_asinh(float x) {
    return asinhf(x);
}

float math_acosh(float x) {
    return acoshf(x);
}

float math_atanh(float x) {
    return atanhf(x);
}
