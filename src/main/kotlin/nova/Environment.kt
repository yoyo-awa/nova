package nova

class Environment(private val enclosing: Environment? = null) {

    private val values = mutableMapOf<String, Any?>()
    private val constants = mutableSetOf<String>()

    fun define(name: String, value: Any?, isConst: Boolean = false) {
        values[name] = value
        if (isConst) constants.add(name)
    }

    fun get(name: String, line: Int = 0): Any? {
        if (values.containsKey(name)) return values[name]
        enclosing?.let { return it.get(name, line) }
        throw NovaRuntimeError("Undefined variable '$name'")
    }

    fun assign(name: String, value: Any?, line: Int = 0) {
        if (values.containsKey(name)) {
            if (constants.contains(name)) {
                throw NovaRuntimeError("Cannot assign to constant '$name'")
            }
            values[name] = value
            return
        }
        enclosing?.let { it.assign(name, value, line); return }
        throw NovaRuntimeError("Undefined variable '$name'")
    }

    fun getOrNull(name: String): Any? {
        if (values.containsKey(name)) return values[name]
        return enclosing?.getOrNull(name)
    }

    fun snapshot(): Map<String, Any?> = values.toMap()

}