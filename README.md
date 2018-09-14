# OKM

A programming language that has a builtin crappy optimizer!

Apart from that, it has modules which are like static classes.

Currently, it lacks a lot of usual stuff such as strings, arrays, classes / closures.

There are structs though, and I plan to make them kind of like classes as well!

The sample code runner is also very slow. Tail calls are optimized however,
so it will not blow the stack if you write your returns properly.

## How to build?

Written in java 8, build with gradle wrapper.

To run directly, you could do `./gradlew run --args './sample/demo.okm'`.

## How to use?

Pass `-h` or `--help` as argument for the first time to see what OKM supports.

NOTE: If you want to get help with the `--args` option, supply a dummy argument before the help argument.
Otherwise, gradle thinks you are trying to get help from it!