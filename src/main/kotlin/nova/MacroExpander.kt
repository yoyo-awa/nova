package nova

/**
 * 编译期宏展开器。
 *
 * 在 Parser 之后、Interpreter 之前运行：
 * 1. 收集所有 MacroDecl
 * 2. 遍历 AST，把宏调用替换成宏体（递归展开）
 * 3. 移除 MacroDecl 节点
 */
class MacroExpander {
    private val macros = mutableMapOf<String, Stmt.MacroDecl>()

    fun expand(statements: List<Stmt>): List<Stmt> {
        // 第一遍：收集宏定义
        for (s in statements) {
            if (s is Stmt.MacroDecl) macros[s.name] = s
        }

        // 第二遍：展开所有非宏语句
        return statements
            .filter { it !is Stmt.MacroDecl }
            .map { expandStmt(it) }
    }

    private fun expandStmt(stmt: Stmt): Stmt = when (stmt) {
        is Stmt.Expression -> Stmt.Expression(expandExpr(stmt.expr))
        is Stmt.Var -> Stmt.Var(stmt.name, expandExpr(stmt.initializer), stmt.isConst)
        is Stmt.Return -> Stmt.Return(stmt.value?.let { expandExpr(it) })
        is Stmt.If -> Stmt.If(
            expandExpr(stmt.condition),
            stmt.thenBranch.map { expandStmt(it) },
            stmt.elseBranch?.map { expandStmt(it) }
        )
        is Stmt.While -> Stmt.While(expandExpr(stmt.condition), stmt.body.map { expandStmt(it) })
        is Stmt.For -> Stmt.For(
            stmt.init?.let { expandStmt(it) },
            stmt.condition?.let { expandExpr(it) },
            stmt.update?.let { expandExpr(it) },
            stmt.body.map { expandStmt(it) }
        )
        is Stmt.ForEachIndex -> Stmt.ForEachIndex(
            stmt.varName, stmt.indexName,
            expandExpr(stmt.collectionExpr),
            stmt.sizeExpr?.let { expandExpr(it) },
            stmt.body.map { expandStmt(it) }
        )
        is Stmt.Block -> Stmt.Block(stmt.statements.map { expandStmt(it) })
        is Stmt.FuncDecl -> Stmt.FuncDecl(stmt.name, stmt.params, stmt.body.map { expandStmt(it) })
        is Stmt.ClassDecl -> stmt
        is Stmt.NamespaceDecl -> Stmt.NamespaceDecl(stmt.name, stmt.body.map { expandStmt(it) })
        is Stmt.ModuleDecl -> Stmt.ModuleDecl(stmt.name, stmt.body.map { expandStmt(it) })
        is Stmt.Import -> stmt
        is Stmt.TryCatch -> Stmt.TryCatch(
            stmt.tryBlock.map { expandStmt(it) },
            stmt.catchVar,
            stmt.catchBlock.map { expandStmt(it) }
        )
        is Stmt.IndexAssign -> Stmt.IndexAssign(
            expandExpr(stmt.target), expandExpr(stmt.index), expandExpr(stmt.value)
        )
        is Stmt.MacroDecl -> stmt
        else -> stmt
    }

    private fun expandExpr(expr: Expr): Expr = when (expr) {
        is Expr.Variable -> {
            val macro = macros[expr.name]
            if (macro != null && macro.params == null) {
                expandExpr(macro.body)
            } else expr
        }
        is Expr.Call -> {
            if (expr.callee is Expr.Variable) {
                val name = expr.callee.name
                val macro = macros[name]
                if (macro != null && macro.params != null) {
                    if (expr.args.size != macro.params.size) {
                        throw NovaRuntimeError(
                            "Macro '$name' expects ${macro.params.size} args, got ${expr.args.size}")
                    }
                    val argMap = macro.params.zip(expr.args.map { expandExpr(it) }).toMap()
                    return substitute(macro.body, argMap)
                }
            }
            Expr.Call(expandExpr(expr.callee), expr.args.map { expandExpr(it) })
        }
        is Expr.Binary -> Expr.Binary(expandExpr(expr.left), expr.op, expandExpr(expr.right))
        is Expr.Unary -> Expr.Unary(expr.op, expandExpr(expr.operand))
        is Expr.Assign -> Expr.Assign(expr.name, expandExpr(expr.value))
        is Expr.ArrayLit -> Expr.ArrayLit(expr.elements.map { expandExpr(it) })
        is Expr.Index -> Expr.Index(expandExpr(expr.target), expandExpr(expr.index))
        is Expr.IndexSet -> Expr.IndexSet(
            expandExpr(expr.target), expandExpr(expr.index), expandExpr(expr.value))
        is Expr.MemberAccess -> Expr.MemberAccess(expandExpr(expr.target), expr.name)
        is Expr.MemberSet -> Expr.MemberSet(expandExpr(expr.target), expr.name, expandExpr(expr.value))
        is Expr.Pipe -> Expr.Pipe(expandExpr(expr.left), expandExpr(expr.right))
        is Expr.PairLit -> Expr.PairLit(expandExpr(expr.key), expandExpr(expr.value))
        is Expr.FuncExpr -> Expr.FuncExpr(expr.params, expr.body.map { expandStmt(it) })
        else -> expr
    }

    /** 把宏体里的参数变量替换成实际参数 */
    private fun substitute(expr: Expr, args: Map<String, Expr>): Expr = when (expr) {
        is Expr.Variable -> args[expr.name] ?: expr
        is Expr.Binary -> Expr.Binary(substitute(expr.left, args), expr.op, substitute(expr.right, args))
        is Expr.Unary -> Expr.Unary(expr.op, substitute(expr.operand, args))
        is Expr.Call -> Expr.Call(substitute(expr.callee, args), expr.args.map { substitute(it, args) })
        is Expr.ArrayLit -> Expr.ArrayLit(expr.elements.map { substitute(it, args) })
        is Expr.Index -> Expr.Index(substitute(expr.target, args), substitute(expr.index, args))
        is Expr.MemberAccess -> Expr.MemberAccess(substitute(expr.target, args), expr.name)
        is Expr.PairLit -> Expr.PairLit(substitute(expr.key, args), substitute(expr.value, args))
        is Expr.Pipe -> Expr.Pipe(substitute(expr.left, args), substitute(expr.right, args))
        else -> expr
    }
}