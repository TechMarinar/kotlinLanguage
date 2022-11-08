// IGNORE_BACKEND_FIR: JVM_IR
//   Disabling of StrictOnlyInputTypesChecks is not supported by FIR
// WITH_STDLIB
// !LANGUAGE: -StrictOnlyInputTypesChecks

fun box(): String {
    val set = setOf<Int>(1, 2, 3, 4, 5)
    val x = 0 in set
    val y = 1 in set
    val z = null in set
    return "OK"
}
