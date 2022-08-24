// !LANGUAGE: +EnumEntries
external enum class Foo : Enum<Foo> {
    A,
    B
}

fun manipulateWithEnum(x: Enum<*>): Int {
    return x.ordinal
}

fun main() {
    Foo.values()
    Foo.valueOf("A")
    Foo.<!UNRESOLVED_REFERENCE!>entries<!>

    enumValues<Foo>()
    enumValueOf<Foo>("A")

    Foo.A.name
    Foo.B.ordinal
    Foo.A.compareTo(Foo.B)

    manipulateWithEnum(Foo.A)
}