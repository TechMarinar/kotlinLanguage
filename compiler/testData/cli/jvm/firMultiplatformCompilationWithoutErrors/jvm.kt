actual interface A {
    actual fun foo(): String
    fun bar(): String
}

fun nullIfEmpty(list: List<String>): List<String>? {
    return if (list.isNotEmpty()) {
        list
    } else {
        null
    }
}

fun test(): String {
    val a = getA()
    return a.foo() + a.bar()
}
