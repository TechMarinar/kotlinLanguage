class ALE<T> : java.util.ArrayList<T>() {
    fun getOrValue(index: Int, value : T) : T = value
}

fun main(args: Array<String>) {
    val ale = <expr>ALE<String></expr>
}