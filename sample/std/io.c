#include <stdio.h>

void print_long(long value) {
    printf("%ld", value);
}

void println_long(long value) {
    printf("%ld\n", value);
}

void print_double(double value) {
    printf("%g", value);
}

void println_double(double value) {
    printf("%g\n", value);
}

void print_bool(_Bool value) {
    if (value)
        printf("true");
    else
        printf("false");
}

void println_bool(_Bool value) {
    if (value)
        printf("true\n");
    else
        printf("false\n");
}
