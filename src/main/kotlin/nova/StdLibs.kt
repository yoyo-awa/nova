package nova
import org.json.JSONArray
import org.json.JSONObject
import org.tomlj.Toml
import org.tomlj.TomlTable
import org.tomlj.TomlArray

object StdLibs {

    private val modules: Map<String, (Environment, Interpreter) -> Unit> = mapOf(
        "json" to { g, _ -> registerJson(g) },
        "toml" to { g, _ -> registerToml(g) },
    )

    fun importModule(name: String, globals: Environment, interpreter: Interpreter): Boolean {
        val register = modules[name] ?: return false
        register(globals, interpreter)
        return true
    }

    private fun registerJson(globals: Environment) {
        val json = NovaNamespace("json")

        json.define("parse", BuiltinFunction("parse") { args ->
            val text = StdReg.requireString(args, 0, "parse")
            try {
                val element = org.json.JSONTokener(text).nextValue()
                jsonToNova(element)
            } catch (e: Exception) {
                throw NovaRuntimeError("json.parse: ${e.message}")
            }
        })

        json.define("stringify", BuiltinFunction("stringify") { args ->
            val value = args.getOrNull(0)
            novaToJson(value).toString()
        })

        globals.define("json", json)
    }

    private fun jsonToNova(value: Any?): Any? = when (value) {
        is JSONObject -> {
            val map = NovaMap()
            for (key in value.keySet()) {
                map[key] = jsonToNova(value.get(key))
            }
            map
        }
        is JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) {
                list.add(jsonToNova(value.get(i)))
            }
            list
        }
        JSONObject.NULL -> null
        else -> value
    }

    private fun novaToJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is NovaMap -> {
            val obj = JSONObject()
            for ((k, v) in value) {
                obj.put(k.toString(), novaToJson(v))
            }
            obj
        }
        is MutableList<*> -> {
            val arr = JSONArray()
            for (item in value) {
                arr.put(novaToJson(item))
            }
            arr
        }
        else -> value
    }
    private fun registerToml(globals: Environment) {
        val toml = NovaNamespace("toml")

        toml.define("parse", BuiltinFunction("parse") { args ->
            val text = requireString(args, 0, "parse")
            val result = Toml.parse(text)
            if (result.hasErrors()) {
                throw NovaRuntimeError("toml.parse: ${result.errors().joinToString("; ")}")
            }
            tomlToNova(result)
        })

        globals.define("toml", toml)
    }

    private fun tomlToNova(value: Any?): Any? = when (value) {
        is TomlTable -> {
            val map = NovaMap()
            for (key in value.keySet()) {
                map[key] = tomlToNova(value.get(key))
            }
            map
        }
        is TomlArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.size()) {
                list.add(tomlToNova(value.get(i)))
            }
            list
        }
        is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value.toDouble()
        is java.time.temporal.Temporal -> value.toString()
        else -> value
    }
}