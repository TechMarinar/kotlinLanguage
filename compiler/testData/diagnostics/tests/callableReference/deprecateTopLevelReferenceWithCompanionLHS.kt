// SKIP_TXT

class A {
    companion object {
        fun foo(): Int = 43
    }

    fun baz(): Int = 1
}

object Obj {
    fun foo(): Int = 43
}

fun main() {
    <!INCORRECT_CALLABLE_REFERENCE_RESOLUTION_FOR_COMPANION_LHS!>A::foo<!>.invoke(A())
    <!INCORRECT_CALLABLE_REFERENCE_RESOLUTION_FOR_COMPANION_LHS!>A::foo<!>.invoke<!NO_VALUE_FOR_PARAMETER!>()<!>
    val x = <!INCORRECT_CALLABLE_REFERENCE_RESOLUTION_FOR_COMPANION_LHS!>A::foo<!>
    x.invoke(A())
    x.invoke<!NO_VALUE_FOR_PARAMETER!>()<!>

    A.Companion::foo.invoke()
    val x0 = A.Companion::foo
    x0.invoke()

    bar(A::foo)

    val y = id(A::foo)
    y.invoke()

    A::baz.invoke(A())

    val z = A::baz
    z.invoke(A())
    bam(A::baz)
}

fun <E> id(e: E): E = e

fun bar(x: () -> Int) {}
fun bam(x: A.() -> Int) {}
