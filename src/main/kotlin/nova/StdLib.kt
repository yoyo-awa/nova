package nova

import java.awt.event.WindowEvent
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random as KRandom

/**
 * Nova 标准库。
 *
 * 所有内置命名空间都在这里注册：
 *   Console.Out / Console.Error / Console.In
 *   Math
 *   String
 *   Array
 *
 * Interpreter 初始化时调用 StdLib.register(globals)。
 */
object StdLib {

    /**
     * 把全部标准库注册到给定的全局环境。
     */
    fun register(globals: Environment, interpreter: Interpreter) {

        registerConsole(globals)
        registerMath(globals)
        registerString(globals)
        registerArray(globals)
        registerMap(globals)
        registerGUI(globals, interpreter)
        registerColors(globals)
        registerColorize(globals)
        registerFile(globals, interpreter)
        val console = globals.get("Console") as NovaNamespace
        val out = console.get("Out") as NovaNamespace
        globals.define("println", out.get("println"))
        globals.define("print", out.get("print"))
    }

    // ============================================================
    //                        Console
    // ============================================================

    private fun registerConsole(globals: Environment) {
        // ---- Out ----
        val out = NovaNamespace("Out")
        out.define("println", BuiltinFunction("println") { args ->
            if (args.isEmpty()) println() else println(stringify(args[0]))
            null
        })
        out.define("print", BuiltinFunction("print") { args ->
            if (args.isNotEmpty()) kotlin.io.print(stringify(args[0]))
            null
        })

        // ---- Error ----
        val err = NovaNamespace("Error")
        err.define("println", BuiltinFunction("println") { args ->
            if (args.isEmpty()) System.err.println() else System.err.println(stringify(args[0]))
            null
        })
        err.define("print", BuiltinFunction("print") { args ->
            if (args.isNotEmpty()) System.err.print(stringify(args[0]))
            null
        })

        // ---- In ----
        val input = NovaNamespace("In")
        input.define("readLine", BuiltinFunction("readLine") {
            readlnOrNull()
        })
        input.define("read", BuiltinFunction("read") {
            val c = System.`in`.read()
            if (c == -1) null else c.toChar().toString()
        })


        // ---- Console 组装 ----
        val console = NovaNamespace("Console")
        console.define("Out", out)
        console.define("Error", err)
        console.define("In", input)

        console.define("clear", BuiltinFunction("clear") {
            print("\u001B[2J\u001B[H")
            System.out.flush()
            null
        })

        globals.define("Console", console)
    }

    // ============================================================
    //                        Math
    // ============================================================

    private fun registerMath(globals: Environment) {
        val math = NovaNamespace("Math")



        math.define("getPi", BuiltinFunction("getPi") { args ->
            val pi200 = BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651328230664709384460955058223172535940812848111745028410270193852110555964462294895493038196442881097566593344612847564823378678316527120190914564856692346034861045432664821339360726024914127372458700660631558817488152092096282925409171536436789259036001133053054882046652138414695194151160943305727036575959195309218611738193261179310511854807446237996274956735188575272489122793818301194912")
            val places = (args.getOrElse(0) { 25 }) as? Int ?: 25
            if (places !in 0..200) {
                throw NovaRuntimeError("Math.getPi: places must be 0..200, got $places")
            }
            pi200.setScale(places, RoundingMode.HALF_UP)
        })
        math.define("getE", BuiltinFunction("getE") { args ->
            val e200 = BigDecimal("2.71828182845904523536028747135266249775724709369995957496696762772407663035354759457138217852516642742746639193200305992181741359662904357290033429526059563073813232862794349076323382988075319525101901")
            val places = (args.getOrElse(0) { 25 }) as? Int ?: 25
            if (places !in 0..200) {
                throw NovaRuntimeError("Math.getPi: places must be 0..200, got $places")
            }
            e200.setScale(places, RoundingMode.HALF_UP)
        })
        math.define("toNum", BuiltinFunction("toNum") { args ->
            when (val v = args.getOrNull(0)) {
                is Int -> v
                is Double -> v
                is String -> {
                    val trimmed = v.trim()
                    trimmed.toIntOrNull()
                        ?: trimmed.toDoubleOrNull()
                        ?: throw NovaRuntimeError("Math.toNum: cannot convert '$v' to number")
                }

                is Boolean -> if (v) 1 else 0
                else -> throw NovaRuntimeError("Math.toNum: cannot convert this value")
            }
        })

        math.define("abs", BuiltinFunction("abs") { a ->
            abs(numOf(a, 0, "abs"))
        })
        math.define("sqrt", BuiltinFunction("sqrt") { a ->
            sqrt(numOf(a, 0, "sqrt"))
        })
        math.define("pow", BuiltinFunction("pow") { a ->
            numOf(a, 0, "pow").pow(numOf(a, 1, "pow"))
        })
        math.define("floor", BuiltinFunction("floor") { a ->
            floor(numOf(a, 0, "floor"))
        })
        math.define("ceil", BuiltinFunction("ceil") { a ->
            ceil(numOf(a, 0, "ceil"))
        })
        math.define("round", BuiltinFunction("round") { a ->
            numOf(a, 0, "round").roundToInt()
        })
        math.define("min", BuiltinFunction("min") { a ->
            Math.min(numOf(a, 0, "min"), numOf(a, 1, "min"))
        })
        math.define("max", BuiltinFunction("max") { a ->
            Math.max(numOf(a, 0, "max"), numOf(a, 1, "max"))
        })

        val random = NovaNamespace("Random")

        // Math.Random.int(min, max)
        random.define("int", BuiltinFunction("int") { args ->
            val min = requireInt(args, 0, "int")
            val max = requireInt(args, 1, "int")
            if (min >= max) {
                throw NovaRuntimeError("Math.Random.int: min must be less than max")
            }
            KRandom.nextInt(min, max)
        })

        // Math.Random.float()
        random.define("float", BuiltinFunction("float") {
            KRandom.nextDouble()
        })

        // Math.Random.bool()
        random.define("bool", BuiltinFunction("bool") {
            KRandom.nextBoolean()
        })

        // Math.Random.choice(array)
        random.define("choice", BuiltinFunction("choice") { args ->
            val arr = requireArray(args, 0, "choice")
            if (arr.isEmpty()) {
                throw NovaRuntimeError("Math.Random.choice: array is empty")
            }
            arr[KRandom.nextInt(arr.size)]
        })

        // Math.Random.shuffle(array)
        random.define("shuffle", BuiltinFunction("shuffle") { args ->
            @Suppress("UNCHECKED_CAST")
            val arr = requireArray(args, 0, "shuffle") as MutableList<Any?>
            for (i in arr.size - 1 downTo 1) {
                val j = KRandom.nextInt(i + 1)
                val tmp = arr[i]
                arr[i] = arr[j]
                arr[j] = tmp
            }
            arr
        })

        // Math.Random.string(length)
        random.define("string", BuiltinFunction("string") { args ->
            val len = requireInt(args, 0, "string")
            if (len !in 0..10000) {
                throw NovaRuntimeError("Math.Random.string: length must be 0..10000")
            }

            // 可选字符集
            val chars = if (args.size >= 2) {
                requireString(args, 1, "string")
            } else {
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            }

            if (chars.isEmpty()) {
                throw NovaRuntimeError("Math.Random.string: charset cannot be empty")
            }

            val sb = StringBuilder()
            for (i in 0 until len) {
                sb.append(chars[KRandom.nextInt(chars.length)])
            }
            sb.toString()
        })

        math.define("Random", random)

        globals.define("Math", math)
    }

    // ============================================================
    //                        String
    // ============================================================

    private fun registerString(globals: Environment) {
        val str = NovaNamespace("String")



        str.define("of", BuiltinFunction("of") { args ->
            stringify(args.getOrNull(0))
        })
        str.define("upper", BuiltinFunction("upper") { args ->
            requireString(args, 0, "upper").uppercase()
        })
        str.define("lower", BuiltinFunction("lower") { args ->
            requireString(args, 0, "lower").lowercase()
        })
        str.define("strip", BuiltinFunction("strip") { args ->
            requireString(args, 0, "strip").trim()
        })
        str.define("size", BuiltinFunction("size") { args ->
            requireString(args, 0, "size").length
        })
        str.define("contains", BuiltinFunction("contains") { args ->
            val s = requireString(args, 0, "contains")
            val sub = requireString(args, 1, "contains")
            s.contains(sub)
        })
        str.define("replace", BuiltinFunction("replace") { args ->
            val s = requireString(args, 0, "replace")
            val a = requireString(args, 1, "replace")
            val b = requireString(args, 2, "replace")
            s.replace(a, b)
        })
        str.define("start", BuiltinFunction("start") { args ->
            val s = requireString(args, 0, "start")
            val p = requireString(args, 1, "start")
            s.startsWith(p)
        })
        str.define("end", BuiltinFunction("end") { args ->
            val s = requireString(args, 0, "end")
            val p = requireString(args, 1, "end")
            s.endsWith(p)
        })
        str.define("split", BuiltinFunction("split") { args ->
            val s = requireString(args, 0, "split")
            val sep = requireString(args, 1, "split")
            if (sep.isEmpty())
                throw NovaRuntimeError("String.split: separator cannot be empty")
            s.split(sep).toMutableList()
        })
        str.define("slice", BuiltinFunction("slice") { args ->
            val s = requireString(args, 0, "slice")
            val a = requireInt(args, 1, "slice")
            val b = requireInt(args, 2, "slice")
            if (a < 0 || b > s.length || a > b)
                throw NovaRuntimeError("String.slice: index out of bounds [$a, $b), length ${s.length}")
            s.substring(a, b)
        })
        str.define("at", BuiltinFunction("at") { args ->
            val s = requireString(args, 0, "at")
            val i = requireInt(args, 1, "at")
            if (i < 0 || i >= s.length)
                throw NovaRuntimeError("String.at: index out of bounds $i, length ${s.length}")
            s[i].toString()
        })
        str.define("reverse", BuiltinFunction("reverse") { args ->
            requireString(args, 0, "reverse").reversed()
        })
        str.define("repeat", BuiltinFunction("repeat") { args ->
            val s = requireString(args, 0, "repeat")
            val n = requireInt(args, 1, "repeat")
            if (n < 0) throw NovaRuntimeError("String.repeat: n cannot be negative")
            s.repeat(n)
        })
        str.define("isEmpty", BuiltinFunction("isEmpty") { args ->
            requireString(args, 0, "isEmpty").isEmpty()
        })

        globals.define("String", str)
    }

    // ============================================================
    //                        Array
    // ============================================================

    private fun registerArray(globals: Environment) {
        val arr = NovaNamespace("Array")

        arr.define("size", BuiltinFunction("size") { args ->
            requireArray(args, 0, "size").size
        })
        arr.define("push", BuiltinFunction("push") { args ->
            val a = requireArray(args, 0, "push")
            for (i in 1 until args.size) a.add(args[i])
            a.size
        })
        arr.define("pop", BuiltinFunction("pop") { args ->
            val a = requireArray(args, 0, "pop")
            if (a.isEmpty()) throw NovaRuntimeError("Array.pop: array is empty")
            a.removeAt(a.size - 1)
        })
        arr.define("get", BuiltinFunction("get") { args ->
            val a = requireArray(args, 0, "get")
            val i = requireInt(args, 1, "get")
            if (i < 0 || i >= a.size)
                throw NovaRuntimeError("Array.get: index out of bounds $i, length ${a.size}")
            a[i]
        })
        arr.define("set", BuiltinFunction("set") { args ->
            val a = requireArray(args, 0, "set")
            val i = requireInt(args, 1, "set")
            val v = args.getOrNull(2)
            if (i < 0 || i >= a.size)
                throw NovaRuntimeError("Array.set: index out of bounds $i, length ${a.size}")
            a[i] = v
            v
        })
        arr.define("at", BuiltinFunction("at") { args ->
            val a = requireArray(args, 0, "at")
            val i = requireInt(args, 1, "at")
            if (i < 0 || i >= a.size)
                throw NovaRuntimeError("Array.at: index out of bounds $i, length ${a.size}")
            a[i]
        })
        arr.define("reverse", BuiltinFunction("reverse") { args ->
            val a = requireArray(args, 0, "reverse")
            a.reverse()
            a
        })
        arr.define("slice", BuiltinFunction("slice") { args ->
            val a = requireArray(args, 0, "slice")
            val x = requireInt(args, 1, "slice")
            val y = requireInt(args, 2, "slice")
            if (x < 0 || y > a.size || x > y)
                throw NovaRuntimeError("Array.slice: index out of bounds [$x, $y), length ${a.size}")
            a.subList(x, y).toMutableList()
        })
        arr.define("contains", BuiltinFunction("contains") { args ->
            val a = requireArray(args, 0, "contains")
            val v = args.getOrNull(1)
            a.any { it == v }
        })
        arr.define("indexOf", BuiltinFunction("indexOf") { args ->
            val a = requireArray(args, 0, "indexOf")
            val v = args.getOrNull(1)
            a.indexOfFirst { it == v }
        })
        arr.define("join", BuiltinFunction("join") { args ->
            val a = requireArray(args, 0, "join")
            val sep = if (args.size >= 2) stringify(args[1]) else ","
            a.joinToString(sep) { stringify(it) }
        })
        arr.define("isEmpty", BuiltinFunction("isEmpty") { args ->
            requireArray(args, 0, "isEmpty").isEmpty()
        })
        arr.define("sort", BuiltinFunction("sort") { args ->
            val a = requireArray(args, 0, "sort")
            val allNumeric = a.all { it is Int || it is Double }
            val allString = a.all { it is String }
            when {
                allNumeric -> a.sortBy { (it as Number).toDouble() }
                allString -> a.sortBy { it as String }
                else -> throw NovaRuntimeError("Array.sort: mixed element types")
            }
            a
        })
        arr.define("clear", BuiltinFunction("clear") { args ->
            requireArray(args, 0, "clear").clear()
            null
        })

        globals.define("Array", arr)
    }


    private fun registerMap(globals: Environment) {
        val map = NovaNamespace("Map")

        // Map.of(k:v, ...)：已有
        map.define("of", BuiltinFunction("of") { args ->
            val m = NovaMap()
            for (arg in args) {
                if (arg !is NovaPair) {
                    throw NovaRuntimeError("Map.of: every argument must be a Pair (key:value)")
                }
                m[arg.key] = arg.value
            }
            m
        })

        // Map.size(m)：键值对个数
        map.define("size", BuiltinFunction("size") { args ->
            requireMap(args, 0, "size").size
        })

        // Map.get(m, key)：读值（不存在返回 null）
        map.define("get", BuiltinFunction("get") { args ->
            val m = requireMap(args, 0, "get")
            m[args.getOrNull(1)]
        })

        // Map.set(m, key, value)：写值
        map.define("set", BuiltinFunction("set") { args ->
            val m = requireMap(args, 0, "set")
            val k = args.getOrNull(1)
            val v = args.getOrNull(2)
            m[k] = v
            v
        })

        // Map.has(m, key)：是否存在 ← 你缺的这个
        map.define("has", BuiltinFunction("has") { args ->
            val m = requireMap(args, 0, "has")
            m.containsKey(args.getOrNull(1))
        })

        // Map.remove(m, key)：删除
        map.define("remove", BuiltinFunction("remove") { args ->
            val m = requireMap(args, 0, "remove")
            m.remove(args.getOrNull(1))
        })

        // Map.keys(m)：所有键
        map.define("keys", BuiltinFunction("keys") { args ->
            requireMap(args, 0, "keys").keys.toMutableList()
        })

        // Map.values(m)：所有值
        map.define("values", BuiltinFunction("values") { args ->
            requireMap(args, 0, "values").values.toMutableList()
        })

        // Map.isEmpty(m)
        map.define("isEmpty", BuiltinFunction("isEmpty") { args ->
            requireMap(args, 0, "isEmpty").isEmpty()
        })

        // Map.clear(m)
        map.define("clear", BuiltinFunction("clear") { args ->
            requireMap(args, 0, "clear").clear()
            null
        })

        globals.define("Map", map)
    }

    private fun registerGUI(globals: Environment, interpreter: Interpreter) {
        val gui = NovaNamespace("GUI")

        // GUI.create(title, w, h, setupFn)
        gui.define("create", BuiltinFunction("create") { args ->
            val title = requireString(args, 0, "create")
            val w = requireInt(args, 1, "create")
            val h = requireInt(args, 2, "create")
            val setupFn = args.getOrNull(3)

            val window = NovaWindow(title, w, h)

            // 调 setup 函数，传 window 作为参数
            if (setupFn is NovaFunction) {
                interpreter.callFunction(setupFn, listOf(window))
            } else {
                throw NovaRuntimeError("GUI.create: 第 4 个参数必须是函数")
            }

            // 显示窗口
            val latch = CountDownLatch(1)

            window.frame.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    latch.countDown()
                }
            })

            window.show()
            latch.await()
            null
        })

        globals.define("GUI", gui)
    }

    private fun registerColors(globals: Environment) {
        val colors = NovaNamespace("Colors")

        // 前景色
        colors.define("BLACK",   "\u001B[30m")
        colors.define("RED",     "\u001B[31m")
        colors.define("GREEN",   "\u001B[32m")
        colors.define("YELLOW",  "\u001B[33m")
        colors.define("BLUE",    "\u001B[34m")
        colors.define("MAGENTA", "\u001B[35m")
        colors.define("CYAN",    "\u001B[36m")
        colors.define("WHITE",   "\u001B[37m")

        // 高亮前景色
        colors.define("BRIGHT_BLACK",   "\u001B[90m")
        colors.define("BRIGHT_RED",     "\u001B[91m")
        colors.define("BRIGHT_GREEN",   "\u001B[92m")
        colors.define("BRIGHT_YELLOW",  "\u001B[93m")
        colors.define("BRIGHT_BLUE",    "\u001B[94m")
        colors.define("BRIGHT_MAGENTA", "\u001B[95m")
        colors.define("BRIGHT_CYAN",    "\u001B[96m")
        colors.define("BRIGHT_WHITE",   "\u001B[97m")

        // 背景色
        colors.define("BG_BLACK",   "\u001B[40m")
        colors.define("BG_RED",     "\u001B[41m")
        colors.define("BG_GREEN",   "\u001B[42m")
        colors.define("BG_YELLOW",  "\u001B[43m")
        colors.define("BG_BLUE",    "\u001B[44m")
        colors.define("BG_MAGENTA", "\u001B[45m")
        colors.define("BG_CYAN",    "\u001B[46m")
        colors.define("BG_WHITE",   "\u001B[47m")

        // 样式
        colors.define("BOLD",          "\u001B[1m")
        colors.define("DIM",           "\u001B[2m")
        colors.define("ITALIC",        "\u001B[3m")
        colors.define("UNDERLINE",     "\u001B[4m")
        colors.define("BLINK",         "\u001B[5m")
        colors.define("REVERSE",       "\u001B[7m")
        colors.define("STRIKETHROUGH", "\u001B[9m")

        // 重置
        colors.define("RESET", "\u001B[0m")

        globals.define("Colors", colors)
    }

    private fun registerColorize(globals: Environment) {
        globals.define("colorize", BuiltinFunction("colorize") { args ->
            if (args.isEmpty()) return@BuiltinFunction ""

            val text = stringify(args[0])
            val codes = StringBuilder()
            for (i in 1 until args.size) {
                codes.append(stringify(args[i]))
            }
            "$codes$text\u001B[0m"
        })
    }

    private fun registerFile(globals: Environment, interpreter: Interpreter) {
        val file = NovaNamespace("File")

        // File.open(path, mode, callback)：打开文件，调回调，自动关闭
        file.define("open", BuiltinFunction("open") { args ->
            val path = requireString(args, 0, "open")
            val mode = requireString(args, 1, "open")
            val callback = args.getOrNull(2)

            if (callback !is NovaFunction) {
                throw NovaRuntimeError("File.open: 3rd argument must be a function")
            }

            val f = try {
                NovaFile(Path.of(path), mode)
            } catch (e: NovaRuntimeError) {
                throw e
            } catch (e: Exception) {
                throw NovaRuntimeError("File.open: ${e.message}")
            }

            try {
                // 调回调，传 NovaFile
                interpreter.callFunction(callback, listOf(f))
            } finally {
                // 自动关闭
                f.close()
            }

            null
        })

        globals.define("File", file)
    }


    // ----------------------------------------------------------------------------------------

    private fun requireMap(args: List<Any?>, i: Int, fn: String): NovaMap {
        return args.getOrNull(i) as? NovaMap
            ?: throw NovaRuntimeError("Map.$fn: argument ${i + 1} must be a map")
    }

    private fun requireString(args: List<Any?>, i: Int, fn: String): String =
        args.getOrNull(i) as? String
            ?: throw NovaRuntimeError("$fn: argument ${i + 1} must be a string")

    private fun requireInt(args: List<Any?>, i: Int, fn: String): Int =
        when (val v = args.getOrNull(i)) {
            is Int -> v
            is Double -> v.toInt()
            else -> throw NovaRuntimeError("$fn: argument ${i + 1} must be an integer")
        }

    @Suppress("UNCHECKED_CAST")
    private fun requireArray(args: List<Any?>, i: Int, fn: String): MutableList<Any?> =
        args.getOrNull(i) as? MutableList<Any?>
            ?: throw NovaRuntimeError("$fn: argument ${i + 1} must be an array")

    private fun numOf(args: List<Any?>, i: Int, fn: String): Double =
        when (val v = args.getOrNull(i)) {
            is Int -> v.toDouble()
            is Double -> v
            else -> throw NovaRuntimeError("$fn: argument ${i + 1} must be a number")
        }
}