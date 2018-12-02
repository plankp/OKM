# OKM

A programming language that has a builtin crappy optimizer!

Apart from that, it has modules which are like static classes.

Currently, it lacks a lot of usual stuff such as strings, arrays, closures.

There are structs and classes though!

The sample code runner is also very slow. Tail calls are optimized however,
so it will not blow the stack if you write your returns properly.

## How to build?

Written in java 8, build with gradle wrapper.

To run directly, you could do `./gradlew run --args './sample/demo.okm'`.

## How to use?

Pass `-h` or `--help` as argument for the first time to see what OKM supports.

NOTE: If you want to get help with the `--args` option, supply a dummy argument before the help argument.
Otherwise, gradle thinks you are trying to get help from it!

## x86-64 bit as target?

If you run the compiler with `--to-amd64`, it will spill out assembly that works with the NASM assembler.
The assembly code does generate a main method.

The assembly code uses an ABI similar to System V AMD64.

The following is tested on Mac OS with clang and on Debian linux with gcc:

If you add the appropriate `global` directives in the output assembly,
you can call them from C. *Structs and classes require extra care (see below)*

For structs, if they fit under a long (64 bits),
if they just contain integer types, they should work directly.
If there are float or double entries, you will need to wrap it in a `union`:

```C
typedef union {
    char _;     // hidden entry
    struct {
        float x, y;
    }
} Vector2f;
```

For bigger structs that do not fit under a long,
the `union` hack described above does not apply,
but functions that pass or return such a struct will be a pointer instead:

```C
typedef struct {
    // No char _ field involved;
    float x, y, z;
} Vector3f;

// Vector3f from_origin_f (x, y, z :float)
extern Vector3f* F17_M0_from_origin_f_x_y_z_(float x, float y, float z);

// unit mut_add (u :&Vector3f, v :Vector3f)
extern void F11_M0_mut_add_u_v_(Vector3f *u, Vector3f *const v);

void foo(void) {
    // Stack allocated, no need to call free
    Vector3f v = *F17_M0_from_origin_f_x_y_z_(1.2, 5.2, 0.9);
}
```

Classes are structs with a virtual method table tacked on the end.
Given a class like the following:

```
class ExprInt(op :int, lhs, rhs :int) {
    int compute(self) =
        if self.op == 0       self.lhs + selfrhs
        else if self.op == 1  self.lhs - selfrhs
        else if self.op == 2  self.lhs * selfrhs
        else                  self.lhs / selfrhs
}
```

The equivalent in C will be (notice the pointer to the data):

```C
typedef struct ExprInt {
    int op;
    int lhs, rhs;

    struct {
        int (*compute)(struct ExprInt *const self);
    } *vtable;
} ExprInt;
```