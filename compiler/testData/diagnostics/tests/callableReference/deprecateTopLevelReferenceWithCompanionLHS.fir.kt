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
    A::foo.invoke(<!TOO_MANY_ARGUMENTS!>A()<!>)
    A::foo.invoke()
    val x = A::foo
    x.invoke(<!TOO_MANY_ARGUMENTS!>A()<!>)
    x.invoke()

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
