package nova

/**
 * break 用异常实现——跳出最近的循环。
 * 禁用 stack trace 提升性能。
 */
class BreakSignal : RuntimeException(null, null, false, false)

/**
 * continue 用异常实现——跳过本次循环剩余部分。
 */
class ContinueSignal : RuntimeException(null, null, false, false)

class ReturnSignal(val value: Any?) : RuntimeException(null, null, false, false)

/**
 * 解释器：遍历 AST 执行。
 *
 * 变更点：
 *   - 执行 Stmt.New / Stmt.FuncDecl（改名）
 *   - 执行 Stmt.NamespaceDecl：创建 NovaNamespace，把 body 定义到其中
 *   - 求值 Expr.MemberAccess / Expr.MemberSet
 */
class Interpreter (private val args: List<String> = emptyList(), private var basePath: String = "." ) {

    private val globals = Environment()
    private val importedFiles = mutableSetOf<String>()

    init {

        globals.define("getArgs", BuiltinFunction("getArgs") {
            args.toMutableList()
        })

        StdLib.register(globals, this)
    }

    fun interpret(statements: List<Stmt>) {
        for (stmt in statements) execute(stmt, globals)
    }

    /**
     * 从 Kotlin 调 Nova 函数（用于 GUI 回调等）。
     */
    fun callFunction(fn: NovaFunction, args: List<Any?>): Any? {
        val fnEnv = Environment(fn.closure)
        if (args.size != fn.params.size) {
            throw NovaRuntimeError("Expected ${fn.params.size} args, got ${args.size}")
        }
        for ((p, a) in fn.params.zip(args)) fnEnv.define(p, a)
        return try {
            for (s in fn.body) execute(s, fnEnv)
            null
        } catch (e: ReturnSignal) {
            e.value
        }
    }


    // ============================================================
    //                       语句执行
    // ============================================================

    private fun execute(stmt: Stmt, env: Environment) {
        //println("DEBUG execute: ${stmt::class.simpleName}")
        when (stmt) {

            is Stmt.Expression -> evaluate(stmt.expr, env)

            is Stmt.Import -> {
                if (!stmt.isLocal) {
                    // 标准库 —— 已注册 —— 什么都不做
                    // 或者“检查”命名空间存在
                    val name = stmt.path.first().replaceFirstChar { it.uppercase() }
                    if (globals.getOrNull(name) == null) {
                        throw NovaRuntimeError("Unknown standard library module '${stmt.path.joinToString(".")}'")
                    }
                    return
                }

                // 本地文件
                val relPath = stmt.path.joinToString("/") + ".nv"
                val resolved = java.io.File(basePath, relPath)
                val absolutePath = resolved.canonicalPath

                if (importedFiles.contains(absolutePath)) return
                importedFiles.add(absolutePath)

                if (!resolved.exists()) {
                    throw NovaRuntimeError("Import: file not found: ${stmt.path.joinToString(".")}")
                }

                val source = try {
                    resolved.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    throw NovaRuntimeError("Import: cannot read '${relPath}': ${e.message}")
                }

                val tokens = Lexer(source).tokenize()
                val ast = Parser(tokens).parse()

                // 临时换 basePath
                val oldBasePath = basePath
                basePath = resolved.parent ?: basePath
                try {
                    for (s in ast) execute(s, globals)
                } finally {
                    basePath = oldBasePath
                }
            }

            // var name = init;
            is Stmt.Var -> env.define(stmt.name, evaluate(stmt.initializer, env), stmt.isConst)


            is Stmt.Block -> executeBlock(stmt.statements, Environment(env))

            is Stmt.Break -> throw BreakSignal()
            is Stmt.Continue -> throw ContinueSignal()

            is Stmt.If -> {
                if (isTruthy(evaluate(stmt.condition, env)))
                    executeBlock(stmt.thenBranch, Environment(env))
                else
                    stmt.elseBranch?.let { executeBlock(it, Environment(env)) }
            }

            is Stmt.While -> {
                try {
                    while (isTruthy(evaluate(stmt.condition, env))) {
                        try {
                            executeBlock(stmt.body, Environment(env))
                        } catch (e: ContinueSignal) {
                            // continue：跳过本次剩余，回到条件判断
                        }
                    }
                } catch (e: BreakSignal) {
                    // break：跳出整个 while
                }
            }

            is Stmt.For -> {
                val forEnv = Environment(env)
                stmt.init?.let { execute(it, forEnv) }
                try {
                    while (stmt.condition == null || isTruthy(evaluate(stmt.condition, forEnv))) {
                        try {
                            executeBlock(stmt.body, Environment(forEnv))
                        } catch (e: ContinueSignal) {
                            // continue：跳到 update
                        }
                        stmt.update?.let { evaluate(it, forEnv) }
                    }
                } catch (e: BreakSignal) {
                    // break：跳出整个 for
                }
            }

            is Stmt.Property -> {
                if (!stmt.isRuntime) return
                val fn = env.getOrNull("__fn__") as? NovaFunction
                    ?: throw NovaRuntimeError("'property' can only be used inside a function")
                fn.fields[stmt.name] = evaluate(stmt.initializer, env)
                if (stmt.isConst) fn.constFields.add(stmt.name)
            }

            is Stmt.ForEachIndex -> {
                val loopEnv = Environment(env)

                if (stmt.sizeExpr != null) {
                    // 显式长度
                    val size = evaluate(stmt.sizeExpr, env) as? Int
                        ?: throw NovaRuntimeError("for: size must be an integer")

                    for (index in 0 until size) {
                        loopEnv.define("$", index)
                        val value = evaluate(stmt.collectionExpr, loopEnv)

                        val iterEnv = Environment(loopEnv)
                        iterEnv.define(stmt.varName, value)
                        try {
                            executeBlock(stmt.body, iterEnv)
                        } catch (e: BreakSignal) {
                            break
                        } catch (e: ContinueSignal) {
                            continue
                        }
                    }
                } else {
                    // 自动长度：越界 / null 停
                    var index = 0
                    while (true) {
                        loopEnv.define("$", index)

                        val value = try {
                            evaluate(stmt.collectionExpr, loopEnv)
                        } catch (e: NovaRuntimeError) {
                            break
                        }

                        if (value == null) break

                        val iterEnv = Environment(loopEnv)
                        iterEnv.define(stmt.varName, value)
                        try {
                            executeBlock(stmt.body, iterEnv)
                        } catch (e: BreakSignal) {
                            break
                        } catch (e: ContinueSignal) {
                            index += 1
                            continue
                        }

                        index += 1
                    }
                }
            }

            is Stmt.Return -> {
                val v = stmt.value?.let { evaluate(it, env) }
                throw ReturnSignal(v)
            }

            is Stmt.TryCatch -> {
                try {
                    executeBlock(stmt.tryBlock, Environment(env))
                } catch (e: NovaRuntimeError) {
                    val catchEnv = Environment(env)
                    // 定义 e 为 NovaError 对象
                    catchEnv.define(stmt.catchVar, NovaError(e.message ?: "unknown error"))
                    executeBlock(stmt.catchBlock, catchEnv)
                }
            }



            is Stmt.ClassDecl -> {
                val methods = mutableMapOf<String, ClassMethodInfo>()
                for (m in stmt.methods) {
                    val fn = NovaFunction(m.params, m.body, env, m.name)
                    methods[m.name] = ClassMethodInfo(m.isPublic, fn)
                }
                val klass = NovaClass(stmt.name, stmt.parent, stmt.fields, methods, env)

                // 解析父类
                if (stmt.parent != null) {
                    val parentClass = env.get(stmt.parent)
                    if (parentClass !is NovaClass) {
                        throw NovaRuntimeError("Parent '${stmt.parent}' is not a class")
                    }
                    klass.parentClass = parentClass
                }

                env.define(stmt.name, klass)
            }

            // func name(...) { ... }
            is Stmt.FuncDecl -> {
                val fn = NovaFunction(stmt.params, stmt.body, env, stmt.name)
                for (s in stmt.body) {
                    if (s is Stmt.Property && !s.isRuntime) {
                        fn.fields[s.name] = evaluate(s.initializer, env)
                        if (s.isConst) fn.constFields.add(s.name)
                    }
                }

                env.define(stmt.name, fn)
            }

            // ------------------------------------------------------
            //  namespace Name { ... }   —— 新增
            // ------------------------------------------------------
            is Stmt.NamespaceDecl -> {
                // 1. 创建一个空的 NovaNamespace 对象
                val ns = NovaNamespace(stmt.name)

                // 2. 用一个"独立环境"来承载命名空间内部的变量，
                //    但这个环境仅供初始化期使用，最终结果要拷进 ns 里。
                val nsEnv = Environment(env)

                // 3. 逐个执行 body 中的声明，把它们登记到 nsEnv
                for (s in stmt.body) {
                    when (s) {
                        is Stmt.Var -> nsEnv.define(s.name, evaluate(s.initializer, nsEnv))
                        is Stmt.FuncDecl -> {
                            // 函数在创建时，闭包捕获的是 nsEnv，
                            // 这样函数体内部能直接访问同命名空间的其它成员。
                            val fn = NovaFunction(s.params, s.body, nsEnv, s.name)
                            nsEnv.define(s.name, fn)
                        }
                        is Stmt.NamespaceDecl -> {
                            // 嵌套命名空间：递归构造子 ns，并登记进 nsEnv
                            val child = buildNamespace(s, nsEnv)
                            nsEnv.define(s.name, child)
                        }
                        else -> execute(s, nsEnv)  // 其它语句（比如 print）允许执行
                    }
                }

                // 4. 把 nsEnv 里所有成员导出到 ns 中，
                //    让 ns 成为对外可见的"命名空间值"。
                for ((k, v) in nsEnv.snapshot()) ns.define(k, v)

                // 5. 在当前环境定义这个命名空间变量
                env.define(stmt.name, ns)
            }

            is Stmt.IndexAssign -> {
                val target = evaluate(stmt.target, env)
                val index = evaluate(stmt.index, env)
                val value = evaluate(stmt.value, env)
                when (target) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = target as MutableList<Any?>
                        val i = (index as? Int)
                            ?: throw NovaRuntimeError("Array index must be integer")
                        if (i < 0 || i >= list.size)
                            throw NovaRuntimeError("Array index out of bounds: $i")
                        list[i] = value
                    }
                    is NovaMap -> {
                        target[index] = value                // ← 新增
                    }
                    else -> throw NovaRuntimeError("Can only index arrays")
                }
            }
        }
    }

    /**
     * 递归构造命名空间（用于嵌套 namespace）。
     * 返回 NovaNamespace 对象，不往任何环境登记。
     */
    private fun buildNamespace(decl: Stmt.NamespaceDecl, parentEnv: Environment): NovaNamespace {
        val ns = NovaNamespace(decl.name)
        val nsEnv = Environment(parentEnv)
        for (s in decl.body) {
            when (s) {
                is Stmt.Var -> nsEnv.define(s.name, evaluate(s.initializer, nsEnv))
                is Stmt.FuncDecl -> {
                    val fn = NovaFunction(s.params, s.body, nsEnv, s.name)
                    nsEnv.define(s.name, fn)
                }
                is Stmt.NamespaceDecl -> {
                    val child = buildNamespace(s, nsEnv)
                    nsEnv.define(s.name, child)
                }
                else -> execute(s, nsEnv)
            }
        }
        for ((k, v) in nsEnv.snapshot()) ns.define(k, v)
        return ns
    }

    private fun executeBlock(statements: List<Stmt>, env: Environment) {
        for (s in statements) execute(s, env)
    }

    // ============================================================
    //                       表达式求值
    // ============================================================

    private fun evaluate(expr: Expr, env: Environment): Any? {
        return when (expr) {

            is Expr.Dollar -> env.get("$")

            is Expr.Literal -> {
                val v = expr.value
                if (v is String && v.contains("{{")) {
                    evalInterpol(v, env)
                } else {
                    v
                }
            }

            is Expr.Pipe -> {
                val left = evaluate(expr.left, env)

                // 用 "$" 的"当前值"
                val pipeEnv = Environment(env)
                pipeEnv.define("$", left)

                when (expr.right) {
                    is Expr.Call -> {
                        // 检查参数里有没有 $
                        val hasDollar = containsDollar(expr.right)
                        if (hasDollar) {
                            // 有 $ —— 求值时把 $ 替换
                            val callee = evaluate(expr.right.callee, pipeEnv)
                            val args = expr.right.args.map { evaluate(it, pipeEnv) }
                            callFunctionValue(callee, args, env)
                        } else {
                            // 没 $ —— 自动作为第一个参数
                            val callee = evaluate(expr.right.callee, env)
                            val args = mutableListOf(left)
                            args.addAll(expr.right.args.map { evaluate(it, env) })
                            callFunctionValue(callee, args, env)
                        }
                    }
                    else -> {
                        // 右是"函数名" —— 自动作为第一个参数
                        val callee = evaluate(expr.right, env)
                        callFunctionValue(callee, listOf(left), env)
                    }
                }
            }

            is Expr.Variable -> env.get(expr.name)

            is Expr.Assign -> {
                val v = evaluate(expr.value, env)
                env.assign(expr.name, v)
                v
            }

            is Expr.Super -> {
                val thisInstance = env.getOrNull("this") as? NovaInstance
                    ?: throw NovaRuntimeError("'super' can only be used in a method")
                val currentClass = env.getOrNull("currentClass") as? NovaClass
                    ?: throw NovaRuntimeError("'super' can only be used in a method")
                SuperRef(thisInstance, currentClass)
            }

            is Expr.ArrayLit -> expr.elements.map { evaluate(it, env) }.toMutableList()

            is Expr.This -> env.get("this")

            // --------------------------------------------------
            //  a.b —— 成员访问（命名空间）
            // --------------------------------------------------
            is Expr.MemberAccess -> {
                val target = evaluate(expr.target, env)
                when (target) {
                    is NovaNamespace -> target.get(expr.name)
                    is NovaPair -> when (expr.name) {
                        "key" -> target.key
                        "value" -> target.value
                        else -> throw NovaRuntimeError("Pair has only 'key' and 'value'")
                    }
                    is NovaError -> when (expr.name) {
                        "message" -> target.message          // 属性访问
                        "type" -> target.type
                        "getMessage" -> BoundBuiltinMethod ("getMessage") { target.message }    // 方法调用
                        "getType" -> BoundBuiltinMethod ("getType") { target.type }
                        else -> throw NovaRuntimeError(
                            "Error has no member '${expr.name}'")
                    }

                    is NovaInstance -> {
                        // 先查字段（沿继承链）
                        val fieldDef = findFieldInChain(target.klass, expr.name)
                        if (fieldDef != null) {
                            if (!fieldDef.isPublic) {
                                val currentThis = env.getOrNull("this")
                                if (currentThis !== target) {
                                    throw NovaRuntimeError(
                                        "Field '${expr.name}' is private in class '${target.klass.name}'")
                                }
                            }
                            return target.fields[expr.name]
                        }

                        // 再查方法（沿继承链）
                        val method = findMethodInChain(target.klass, expr.name)
                        if (method != null) {
                            if (!method.isPublic) {
                                val currentThis = env.getOrNull("this")
                                if (currentThis !== target) {
                                    throw NovaRuntimeError(
                                        "Method '${expr.name}' is private in class '${target.klass.name}'")
                                }
                            }
                            return BoundMethod(target, method.fn, findDefiningClass(target.klass, expr.name))
                        }
                        throw NovaRuntimeError(
                            "Instance of '${target.klass.name}' has no member '${expr.name}'")
                    }

                    is NovaFunction -> {
                        if (target.fields.containsKey(expr.name)) {
                            target.fields[expr.name]
                        } else {
                            throw NovaRuntimeError(
                                "Function '${target.name}' has no field '${expr.name}'")
                        }
                    }

                    is NovaFile -> when (expr.name) {
                        "read" -> BoundBuiltinMethod("read") {
                            target.readAll()
                        }
                        "readLine" -> BoundBuiltinMethod("readLine") {
                            target.readLine()
                        }
                        "readLines" -> BoundBuiltinMethod("readLines") {
                            target.readLines()
                        }
                        "write" -> BoundBuiltinMethodWithArgs("write") { a ->
                            val text = stringify(a.getOrNull(0))
                            target.write(text)
                            null
                        }
                        "writeLine" -> BoundBuiltinMethodWithArgs("writeLine") { a ->
                            val text = stringify(a.getOrNull(0))
                            target.writeLine(text)
                            null
                        }
                        "close" -> BoundBuiltinMethod("close") {
                            target.close()
                            null
                        }
                        "path" -> target.path.toString()
                        "mode" -> target.mode
                        else -> throw NovaRuntimeError("File has no member '${expr.name}'")
                    }

                    is SuperRef -> {
                        val parentClass = target.currentClass.parentClass     // ← 用 currentClass
                            ?: throw NovaRuntimeError("Class has no parent")
                        val method = findMethodInChain(parentClass, expr.name)
                        if (method != null) {
                            return BoundMethod(target.instance, method.fn, parentClass)
                        }
                        throw NovaRuntimeError(
                            "Parent class '${parentClass.name}' has no member '${expr.name}'")
                    }

                    is NovaWindow -> when (expr.name) {
                        "add" -> BoundBuiltinMethodWithArgs("add") { a ->
                            val comp = a.getOrNull(0)
                            when (comp) {
                                is NovaButton -> target.panel.add(comp.button)
                                is NovaLabel -> target.panel.add(comp.label)
                                is NovaTextField -> target.panel.add(comp.textField)
                                else -> throw NovaRuntimeError("GUI.add: unsupported component")
                            }
                            target.panel.revalidate()
                            target.panel.repaint()
                            null
                        }

                        // ---- 工厂方法 ----
                        "Button" -> BoundBuiltinMethodWithArgs("Button") { a ->
                            val text = a.getOrNull(0) as? String
                                ?: throw NovaRuntimeError("gui.Button: text must be a string")
                            NovaButton(text)
                        }

                        "Label" -> BoundBuiltinMethodWithArgs("Label") { a ->
                            val text = a.getOrNull(0) as? String
                                ?: throw NovaRuntimeError("gui.Label: text must be a string")
                            NovaLabel(text)
                        }

                        "TextField" -> BoundBuiltinMethodWithArgs("TextField") { a ->
                            val placeholder = if (a.isNotEmpty()) stringify(a[0]) else ""
                            NovaTextField(placeholder)
                        }

                        "close" -> BoundBuiltinMethod("close") {
                            target.frame.dispose()
                            null
                        }

                        "title" -> target.title

                        else -> throw NovaRuntimeError("Window has no member '${expr.name}'")
                    }
                    is NovaButton -> when (expr.name) {
                        "addEvent" -> BoundBuiltinMethodWithArgs("addEvent") { args ->
                            val callback = args.getOrNull(0)
                            if (callback is NovaFunction) {
                                target.button.addActionListener {
                                    try {
                                        callFunction(callback, emptyList())    // ← this.callFunction
                                    } catch (e: Exception) {
                                        System.err.println("按钮回调出错: ${e.message}")
                                    }
                                }
                            }
                            null
                        }
                        "text" -> target.text
                        else -> throw NovaRuntimeError("Button has no member '${expr.name}'")
                    }

                    is NovaTextField -> when (expr.name) {
                        "text" -> target.text
                        "clear" -> BoundBuiltinMethod("clear") {
                            target.text = ""
                            null
                        }
                        else -> throw NovaRuntimeError("TextField has no member '${expr.name}'")
                    }
                    else -> BoundReceiverMethod(target, expr.name)
                }
            }

            // --------------------------------------------------
            //  a.b = v —— 成员赋值
            // --------------------------------------------------
            is Expr.MemberSet -> {
                val target = evaluate(expr.target, env)
                val v = evaluate(expr.value, env)
                when (target) {
                    is NovaNamespace -> {
                        target.set(expr.name, v)
                        v
                    }
                    is NovaInstance -> {
                        val fieldDef = findFieldInChain(target.klass, expr.name) ?: throw NovaRuntimeError(
                            "Instance of '${target.klass.name}' has no field '${expr.name}'"
                        )
                        if (!fieldDef.isPublic) {
                            val currentThis = env.getOrNull("this")
                            if (currentThis !== target) {
                                throw NovaRuntimeError(
                                    "Field '${expr.name}' is private in class '${target.klass.name}'")
                            }
                        }
                        target.fields[expr.name] = v
                        v
                    }
                    is NovaFunction -> {

                        if (target.constFields.contains(expr.name)) {
                            throw NovaRuntimeError(
                                "Cannot assign to const property '${expr.name}' of function '${target.name}'")
                        }
                        target.fields[expr.name] = v
                        v
                    }
                    is NovaLabel -> when (expr.name) {
                        "text" -> {
                            target.text = stringify(v)
                            v
                        }
                        else -> throw NovaRuntimeError("Label has no member '${expr.name}'")
                    }

                    is NovaTextField -> when (expr.name) {
                        "text" -> target.text
                        "clear" -> BoundBuiltinMethod("clear") {
                            target.text = ""
                            null
                        }
                        else -> throw NovaRuntimeError("TextField has no member '${expr.name}'")
                    }

                    is NovaButton -> when (expr.name) {
                        "text" -> {
                            target.text = stringify(v)
                            v
                        }
                        else -> throw NovaRuntimeError("Button has no member '${expr.name}'")
                    }
                    else -> throw NovaRuntimeError("Cannot set member '${expr.name}'")
                }
            }

            is Expr.Index -> {
                val target = evaluate(expr.target, env)
                val index = evaluate(expr.index, env)
                when (target) {
                    is MutableList<*> -> {
                        val i = (index as? Int)
                            ?: throw NovaRuntimeError("Array index must be integer")
                        if (i < 0 || i >= target.size)
                            throw NovaRuntimeError("Array index out of bounds: $i")
                        target[i]
                    }
                    is String -> {
                        val i = (index as? Int)
                            ?: throw NovaRuntimeError("String index must be integer")
                        if (i < 0 || i >= target.length)
                            throw NovaRuntimeError("String index out of bounds: $i")
                        target[i].toString()
                    }
                    is NovaPair -> {
                        val i = (index as? Int)
                            ?: throw NovaRuntimeError("Pair index must be integer")
                        when (i) {
                            0 -> target.key
                            1 -> target.value
                            else -> throw NovaRuntimeError("Pair index must be 0 or 1")
                        }
                    }
                    is NovaMap -> target[index]
                    else -> throw NovaRuntimeError("Can only index arrays or strings")
                }
            }

            is Expr.IndexSet -> {
                val target = evaluate(expr.target, env)
                val index = evaluate(expr.index, env)
                val v = evaluate(expr.value, env)
                when (target) {
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = target as MutableList<Any?>
                        val i = (index as? Int)
                            ?: throw NovaRuntimeError("Array index must be integer")
                        if (i < 0 || i >= list.size)
                            throw NovaRuntimeError("Array index out of bounds: $i")
                        list[i] = v
                    }
                    is NovaMap -> {
                        target[index] = v
                    }
                    else -> throw NovaRuntimeError(
                        "Can only index arrays or maps, got ${target?.let { it::class.simpleName }}")
                }
                v
            }

            is Expr.Unary -> {
                val v = evaluate(expr.operand, env)
                when (expr.op) {
                    TokenType.MINUS -> when (v) {
                        is Int -> -v
                        is Double -> -v
                        else -> throw NovaRuntimeError("Operand must be a number")
                    }
                    TokenType.NOT -> !isTruthy(v)
                    else -> throw NovaRuntimeError("Unknown unary op ${expr.op}")
                }
            }

            is Expr.Binary -> {
                if (expr.op == TokenType.AND) {
                    val l = evaluate(expr.left, env)
                    if (!isTruthy(l)) return false
                    return isTruthy(evaluate(expr.right, env))
                }
                if (expr.op == TokenType.OR) {
                    val l = evaluate(expr.left, env)
                    if (isTruthy(l)) return true
                    return isTruthy(evaluate(expr.right, env))
                }
                val l = evaluate(expr.left, env)
                val r = evaluate(expr.right, env)
                binaryOp(expr.op, l, r)
            }

            is Expr.Call -> {
                val callee = evaluate(expr.callee, env)
                val args = expr.args.map { evaluate(it, env) }
                callFunctionValue(callee, args, env)
            }

            is Expr.PairLit -> NovaPair(
                evaluate(expr.key, env),
                evaluate(expr.value, env)
            )

            is Expr.FuncExpr -> NovaFunction(expr.params, expr.body, env)
        }
    }

    private fun evalInterpol(template: String, env: Environment): String {
        val sb = StringBuilder()
        var i = 0

        while (i < template.length) {
            // 检查是不是 {{
            if (i + 1 < template.length &&
                template[i] == '{' && template[i + 1] == '{') {

                i += 2
                val start = i
                var depth = 1

                while (i < template.length) {
                    if (i + 1 < template.length &&
                        template[i] == '}' && template[i + 1] == '}') {
                        depth--
                        if (depth == 0) break
                        i += 2
                    } else if (i + 1 < template.length &&
                        template[i] == '{' && template[i + 1] == '{') {
                        depth++
                        i += 2
                    } else {
                        i++
                    }
                }

                if (depth != 0) {
                    throw NovaRuntimeError("String interpolation: unmatched '{{'")
                }

                val exprSrc = template.substring(start, i)

                val value = try {
                    val tokens = Lexer(exprSrc).tokenize()
                    val expr = Parser(tokens).parseExpression()
                    evaluate(expr, env)
                } catch (e: Exception) {
                    throw NovaRuntimeError(
                        "String interpolation error in '{{$exprSrc}}': ${e.message}")
                }

                sb.append(stringify(value))
                i += 2

            } else {
                sb.append(template[i])
                i++
            }
        }

        return sb.toString()
    }

    // ============================================================
    //                       辅助逻辑
    // ============================================================

    private fun binaryOp(op: TokenType, l: Any?, r: Any?): Any? {
        if (op == TokenType.PLUS && (l is String || r is String)) {
            return stringify(l) + stringify(r)
        }
        if (l is Int && r is Int) {
            return when (op) {
                TokenType.PLUS    -> l + r
                TokenType.MINUS   -> l - r
                TokenType.STAR    -> l * r
                TokenType.SLASH   -> {
                    if (r == 0) throw NovaRuntimeError("Division by zero")
                    l / r
                }
                TokenType.PERCENT -> {
                    if (r == 0) throw NovaRuntimeError("Modulo by zero")
                    l % r
                }
                TokenType.EQ      -> l == r
                TokenType.NEQ     -> l != r
                TokenType.LT      -> l < r
                TokenType.GT      -> l > r
                TokenType.LTE     -> l <= r
                TokenType.GTE     -> l >= r
                else -> throw NovaRuntimeError("Unknown op $op")
            }
        }
        if ((l is Int || l is Double) && (r is Int || r is Double)) {
            val a = (l as Number).toDouble()
            val b = (r as Number).toDouble()
            return when (op) {
                TokenType.PLUS    -> a + b
                TokenType.MINUS   -> a - b
                TokenType.STAR    -> a * b
                TokenType.SLASH   -> {
                    if (b == 0.0) throw NovaRuntimeError("Division by zero")
                    a / b
                }
                TokenType.PERCENT -> a % b
                TokenType.EQ      -> a == b
                TokenType.NEQ     -> a != b
                TokenType.LT      -> a < b
                TokenType.GT      -> a > b
                TokenType.LTE     -> a <= b
                TokenType.GTE     -> a >= b
                else -> throw NovaRuntimeError("Unknown op $op")
            }
        }
        if (op == TokenType.EQ)  return l == r
        if (op == TokenType.NEQ) return l != r
        throw NovaRuntimeError("Cannot apply $op to $l and $r")
    }

    private fun containsDollar(expr: Expr): Boolean {
        return when (expr) {
            is Expr.Dollar -> true
            is Expr.Call -> containsDollar(expr.callee) ||
                    expr.args.any { containsDollar(it) }
            is Expr.Binary -> containsDollar(expr.left) || containsDollar(expr.right)
            // ... 递归检查其他 Expr
            else -> false
        }
    }

    private fun isTruthy(v: Any?): Boolean = when (v) {
        null -> false
        is Boolean -> v
        is Int -> v != 0
        is Double -> v != 0.0
        is String -> v.isNotEmpty()
        else -> true
    }

    private fun initClassHierarchy(instance: NovaInstance, klass: NovaClass) {
        // 先初始化父类
        klass.parentClass?.let { initClassHierarchy(instance, it) }

        // 再初始化"当前类"的字段
        for (field in klass.fields) {
            val fieldEnv = Environment(klass.closure)
            fieldEnv.define("this", instance)
            instance.fields[field.name] = evaluate(field.initializer, fieldEnv)
        }
    }

    private fun findMethodInChain(klass: NovaClass?, name: String): ClassMethodInfo? {
        var k = klass
        while (k != null) {
            if (k.methods.containsKey(name)) return k.methods[name]
            k = k.parentClass
        }
        return null
    }

    private fun findFieldInChain(klass: NovaClass?, name: String): Stmt.ClassField? {
        var k = klass
        while (k != null) {
            val f = k.fields.find { it.name == name }
            if (f != null) return f
            k = k.parentClass
        }
        return null
    }

    private fun findDefiningClass(klass: NovaClass?, name: String): NovaClass {
        var k = klass
        while (k != null) {
            if (k.methods.containsKey(name)) return k
            k = k.parentClass
        }
        throw NovaRuntimeError("Method '$name' not found")
    }

    private fun findBuiltinForReceiver(receiver: Any?, name: String): BuiltinFunction? {
        val nsName = when (receiver) {
            is String -> "String"
            is MutableList<*> -> "Array"
            is NovaMap -> "Map"
            else -> return null
        }

        val ns = globals.getOrNull(nsName) as? NovaNamespace ?: return null
        return if (ns.has(name)) ns.get(name) as? BuiltinFunction else null
    }

    private fun callFunctionValue(callee: Any?, args: List<Any?>, env: Environment): Any? {
        return when (callee) {
            is NovaFunction -> {
                val fnEnv = Environment(callee.closure)
                fnEnv.define("__fn__", callee)
                if (args.size != callee.params.size)
                    throw NovaRuntimeError("Expected ${callee.params.size} args, got ${args.size}")
                for ((p, a) in callee.params.zip(args)) fnEnv.define(p, a)
                try {
                    for (s in callee.body) execute(s, fnEnv)
                    null
                } catch (e: ReturnSignal) {
                    e.value
                }
            }
            is BoundMethod -> {
                val fn = callee.fn
                val fnEnv = Environment(fn.closure)
                fnEnv.define("this", callee.instance)
                fnEnv.define("__fn__", callee.fn)
                fnEnv.define("currentClass", callee.definingClass)
                if (args.size != fn.params.size)
                    throw NovaRuntimeError("Expected ${fn.params.size} args, got ${args.size}")
                for ((p, a) in fn.params.zip(args)) fnEnv.define(p, a)
                try {
                    for (s in fn.body) execute(s, fnEnv)
                    null
                } catch (e: ReturnSignal) {
                    e.value
                }
            }
            is NovaClass -> {
                val instance = NovaInstance(callee)

                // 1. 先初始化"父类"（递归）
                initClassHierarchy(instance, callee)

                // 2. 调"当前类"的构造
                val ctor = findMethodInChain(callee, callee.name)
                if (ctor != null) {
                    val initEnv = Environment(ctor.fn.closure)
                    initEnv.define("this", instance)
                    initEnv.define("currentClass", callee)
                    if (args.size != ctor.fn.params.size)
                        throw NovaRuntimeError("Constructor expects ${ctor.fn.params.size} args, got ${args.size}")
                    for ((p, a) in ctor.fn.params.zip(args)) initEnv.define(p, a)
                    try {
                        for (s in ctor.fn.body) execute(s, initEnv)
                    } catch (e: ReturnSignal) { }
                } else if (args.isNotEmpty()) {
                    throw NovaRuntimeError("Class ${callee.name} has no constructor")
                }

                instance
            }
            is SuperRef -> {
                val parentClass = callee.currentClass.parentClass     // ← 改这里
                    ?: throw NovaRuntimeError("Class has no parent")
                val parentCtor = findMethodInChain(parentClass, parentClass.name)
                if (parentCtor != null) {
                    val initEnv = Environment(parentCtor.fn.closure)
                    initEnv.define("this", callee.instance)
                    initEnv.define("currentClass", parentClass)        // ← 父类构造的"当前类"是父类
                    if (args.size != parentCtor.fn.params.size)
                        throw NovaRuntimeError(
                            "Parent constructor expects ${parentCtor.fn.params.size} args, got ${args.size}")
                    for ((p, a) in parentCtor.fn.params.zip(args)) initEnv.define(p, a)
                    try {
                        for (s in parentCtor.fn.body) execute(s, initEnv)
                    } catch (e: ReturnSignal) { }
                } else if (args.isNotEmpty()) {
                    throw NovaRuntimeError("Parent class has no constructor")
                }
                null
            }
            is BuiltinFunction -> callee.fn(args)
            is BoundBuiltinMethod -> callee.fn()
            is BoundBuiltinMethodWithArgs -> callee.fn(args)
            is BoundReceiverMethod -> {
                // 从全局命名空间里找 func
                // 比如 String.upper / Array.push / Map.has
                val builtin = findBuiltinForReceiver(callee.receiver, callee.name)
                    ?: throw NovaRuntimeError(
                        "No method '${callee.name}' for ${stringify(callee.receiver)}")

                // 把 receiver 作为第一个参数
                val allArgs = mutableListOf(callee.receiver)
                allArgs.addAll(args)
                builtin.fn(allArgs)
            }
            else -> throw NovaRuntimeError("Can only call functions or classes")
        }
    }
}

class BuiltinFunction(val name: String, val fn: (List<Any?>) -> Any?) {
    override fun toString() = "<builtin $name>"
}