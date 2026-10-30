package nova

/**
 * 把任意 Nova 值转成字符串。
 * 顶层函数，同一包内任何文件都能直接调用。
 */
fun stringify(v: Any?): String = when (v) {
    null -> "null"
    is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    is MutableList<*> -> v.joinToString(", ", "[", "]") { stringify(it) }
    is NovaMap -> v.entries.joinToString(", ", "{", "}") {
        stringify(it.key) + ": " + stringify(it.value)
    }
    is NovaPair -> "(" + stringify(v.key) + ":" + stringify(v.value) + ")"
    is NovaInstance -> "<instance of ${v.klass.name}>"
    is NovaClass -> "<class ${v.name}>"
    is NovaError -> v.type + ": " + v.message
    else -> v.toString()
}