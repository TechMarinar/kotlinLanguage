import kotlinx.atomicfu.*
import kotlin.test.*

class DelegatedPropertiesPrivate {
    private val _a = atomic(42)
    private var a: Int by _a
    var b: Int by _a

    fun test() {
        a = 5
        b = 7
    }
}

fun box(): String {
    val testClass = DelegatedPropertiesPrivate()
    testClass.test()
    return "OK"
}
