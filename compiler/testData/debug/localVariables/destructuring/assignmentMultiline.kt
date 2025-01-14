// WITH_STDLIB
// IGNORE_BACKEND_FIR: JVM_IR

// FILE: test.kt
fun box(): String {
    val p = "O" to "K"

    val
            (
        o
            ,
        k
    )
            =
        p

    return o + k
}

// EXPECTATIONS JVM JVM_IR
// test.kt:6 box:
// test.kt:15 box: p:kotlin.Pair=kotlin.Pair
// test.kt:10 box: p:kotlin.Pair=kotlin.Pair
// EXPECTATIONS JVM
// test.kt:12 box: p:kotlin.Pair=kotlin.Pair
// EXPECTATIONS JVM_IR
// test.kt:12 box: p:kotlin.Pair=kotlin.Pair, o:java.lang.String="O":java.lang.String
// EXPECTATIONS JVM JVM_IR
// test.kt:17 box: p:kotlin.Pair=kotlin.Pair, o:java.lang.String="O":java.lang.String, k:java.lang.String="K":java.lang.String

// EXPECTATIONS JS_IR
// test.kt:6 box:
// test.kt:15 box: p=kotlin.Pair
// test.kt:10 box: p=kotlin.Pair
// test.kt:12 box: p=kotlin.Pair, o="O":kotlin.String
// test.kt:17 box: p=kotlin.Pair, o="O":kotlin.String, k="K":kotlin.String
