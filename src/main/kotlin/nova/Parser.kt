package nova

/**
 * 语法分析器：Token 流 → AST
 *
 * 变更点：
 *   - declaration() 识别 NEW / FUNC / NAMESPACE
 *   - assignment() 的赋值目标扩展 MemberAccess → MemberSet
 *   - call() 中处理 '.' 成员访问（与 ()、[] 同级链式）
 */
class Parser(private val tokens: List<Token>) {

    private var pos = 0

    fun parse(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!isAtEnd()) stmts.add(declaration())
        return stmts
    }

    // ============================================================
    //                    声明与语句
    // ============================================================

    /** 声明：var 变量 / func 函数 / namespace 命名空间 / 普通语句 */
    private fun declaration(): Stmt {
        if (match(TokenType.CLASS))   return classDeclaration()
        if (match(TokenType.FUNC))      return funcDeclaration()
        if (match(TokenType.VAR))       return varDeclaration()
        if (match(TokenType.CONST))     return constVarDeclaration()
        if (match(TokenType.NAMESPACE)) return namespaceDeclaration()
        return statement()
    }


    private fun classDeclaration(): Stmt {
        val firstName = consume(TokenType.IDENT, "Expect class name").lexeme

        // 检查是否是 "Parent -> Child"
        var name = firstName
        var parent: String? = null
        if (match(TokenType.SINGLE_ARROW)) {
            // firstName 是父类，下一个是子类
            parent = firstName
            name = consume(TokenType.IDENT, "Expect child class name").lexeme
        }

        consume(TokenType.LBRACE, "Expect '{' after class name")

        val fields = mutableListOf<Stmt.ClassField>()
        val methods = mutableListOf<Stmt.ClassMethod>()

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            var isPublic = false
            if (match(TokenType.PUBLIC)) isPublic = true
            else if (match(TokenType.PRIVATE)) isPublic = false


            if (match(TokenType.VAR)) {
                val fieldName = consume(TokenType.IDENT, "Expect field name").lexeme
                consume(TokenType.ASSIGN, "Expect '=' after field name")
                val init = expression()
                consume(TokenType.SEMICOLON, "Expect ';' after field")
                fields.add(Stmt.ClassField(isPublic, fieldName, init))
            } else if (match(TokenType.CONST)) {
                // 类体里的常量字段
                consume(TokenType.VAR, "Expect 'var' after 'const'")
                val fieldName = consume(TokenType.IDENT, "Expect field name").lexeme
                consume(TokenType.ASSIGN, "Expect '=' after field name")
                val init = expression()
                consume(TokenType.SEMICOLON, "Expect ';' after field")
                fields.add(Stmt.ClassField(isPublic, fieldName, init, true))
            } else if (match(TokenType.FUNC)) {
                val methodName = consume(TokenType.IDENT, "Expect method name").lexeme
                consume(TokenType.LPAREN, "Expect '(' after method name")
                val params = mutableListOf<String>()
                if (!check(TokenType.RPAREN)) {
                    do {
                        params.add(consume(TokenType.IDENT, "Expect parameter name").lexeme)
                    } while (match(TokenType.COMMA))
                }
                consume(TokenType.RPAREN, "Expect ')' after parameters")
                val body = block()
                methods.add(Stmt.ClassMethod(isPublic, methodName, params, body))
            } else {
                throw NovaSyntaxError("Line ${peek().line}: Unexpected token in class body")
            }
        }

        consume(TokenType.RBRACE, "Expect '}' after class body")
        return Stmt.ClassDecl(name, parent, fields, methods)
    }

    /** func name(params) { body } */
    private fun funcDeclaration(): Stmt {
        val name = consume(TokenType.IDENT, "Expect function name").lexeme
        consume(TokenType.LPAREN, "Expect '(' after function name")
        val params = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENT, "Expect parameter name").lexeme)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expect ')' after parameters")
        val body = block()
        return Stmt.FuncDecl(name, params, body)
    }

    /** var name = init; */
    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENT, "Expect variable name").lexeme
        consume(TokenType.ASSIGN, "Expect '=' after variable name")
        val init = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after declaration")
        return Stmt.Var(name, init)
    }

    private fun constVarDeclaration(): Stmt {
        consume(TokenType.VAR, "Expect 'var' after 'const'")
        val name = consume(TokenType.IDENT, "Expect variable name").lexeme
        consume(TokenType.ASSIGN, "Expect '=' after variable name")
        val init = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after declaration")
        return Stmt.Var(name, init, isConst = true)
    }

    /** namespace Name { ... } —— 把 body 作为普通语句块解析 */
    private fun namespaceDeclaration(): Stmt {
        val name = consume(TokenType.IDENT, "Expect namespace name").lexeme
        // 命名空间的 body 内部只允许声明语句（var / func / namespace / 表达式语句）
        consume(TokenType.LBRACE, "Expect '{' after namespace name")
        val body = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            body.add(declaration())
        }
        consume(TokenType.RBRACE, "Expect '}' after namespace body")
        return Stmt.NamespaceDecl(name, body)
    }

    private fun statement(): Stmt {
        return when {
            match(TokenType.IF)     -> ifStatement()
            match(TokenType.WHILE)  -> whileStatement()
            match(TokenType.FOR)    -> forStatement()
            match(TokenType.RETURN) -> returnStatement()
            match(TokenType.LBRACE) -> Stmt.Block(blockAfterBrace())
            match(TokenType.BREAK)  -> breakStatement()
            match(TokenType.CONTINUE) -> continueStatement()
            match(TokenType.TRY)      -> tryCatchStatement()
            else -> expressionStatement()
        }
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LPAREN, "Expect '(' after 'if'")
        val cond = expression()
        consume(TokenType.RPAREN, "Expect ')' after condition")
        val thenBranch = block()
        var elseBranch: List<Stmt>? = null
        if (match(TokenType.ELSE)) {
            elseBranch = if (check(TokenType.IF)) listOf(statement()) else block()
        }
        return Stmt.If(cond, thenBranch, elseBranch)
    }

    private fun whileStatement(): Stmt {
        consume(TokenType.LPAREN, "Expect '(' after 'while'")
        val cond = expression()
        consume(TokenType.RPAREN, "Expect ')' after condition")
        return Stmt.While(cond, block())
    }

    private fun forStatement(): Stmt {
        if (check(TokenType.IDENT) && checkNext(TokenType.ASSIGN)) {
            val varName = consume(TokenType.IDENT, "Expect variable name").lexeme
            consume(TokenType.ASSIGN, "Expect '=' after variable")
            val collection = expression()

            var sizeExpr: Expr? = null
            if (match(TokenType.COMMA)) {
                sizeExpr = expression()
            }

            return Stmt.ForEachIndex(varName, collection, sizeExpr, block())
        }
        // 旧语法：for ( ... )
        consume(TokenType.LPAREN, "Expect '(' after 'for'")
        val init = if (match(TokenType.SEMICOLON)) null
        else if (match(TokenType.VAR)) varDeclaration()
        else expressionStatement()
        val cond = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition")
        val update = if (!check(TokenType.RPAREN)) expression() else null
        consume(TokenType.RPAREN, "Expect ')' after for clauses")
        return Stmt.For(init, cond, update, block())
    }
    private fun returnStatement(): Stmt {
        val value = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after return value")
        return Stmt.Return(value)
    }


    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression")
        return Stmt.Expression(expr)
    }

    private fun breakStatement(): Stmt {
        consume(TokenType.SEMICOLON, "Expect ';' after 'break'")
        return Stmt.Break
    }

    private fun continueStatement(): Stmt {
        consume(TokenType.SEMICOLON, "Expect ';' after 'continue'")
        return Stmt.Continue
    }

    private fun block(): List<Stmt> {
        consume(TokenType.LBRACE, "Expect '{'")
        return blockAfterBrace()
    }

    private fun blockAfterBrace(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) stmts.add(declaration())
        consume(TokenType.RBRACE, "Expect '}'")
        return stmts
    }

    private fun tryCatchStatement(): Stmt {
        // 已经消费了 'try'
        val tryBlock = block()

        consume(TokenType.CATCH, "Expect 'catch' after 'try' block")
        consume(TokenType.LPAREN, "Expect '(' after 'catch'")
        val catchVar = consume(TokenType.IDENT, "Expect variable name in catch").lexeme
        consume(TokenType.RPAREN, "Expect ')' after catch variable")
        val catchBlock = block()

        return Stmt.TryCatch(tryBlock, catchVar, catchBlock)
    }

    // ============================================================
    //                    表达式（按优先级分层）
    // ============================================================

    private fun expression(): Expr = assignment()

    fun parseExpression(): Expr {
        return expression()
    }

    /**
     * 赋值（右结合）。
     * 左值支持：
     *   - 变量           x = v
     *   - 下标           arr`[i]` = v
     *   - 成员           ns.field = v   （新增）
     */
    private fun assignment(): Expr {
        val expr = pair()
        if (match(TokenType.ASSIGN)) {
            val value = assignment()
            return when (expr) {
                is Expr.Variable -> Expr.Assign(expr.name, value)
                is Expr.Index    -> Expr.IndexSet(expr.target, expr.index, value)
                // 新增：成员赋值 a.b = v
                is Expr.MemberAccess -> Expr.MemberSet(expr.target, expr.name, value)
                else -> throw NovaSyntaxError("Invalid assignment target")
            }
        }
        return expr
    }

    private fun pair(): Expr {
        var expr = pipe()
        while (match(TokenType.COLON)) {
            val value = pipe()       // 右边用 or()，可以继续 : 链
            expr = Expr.PairLit(expr, value)
        }
        return expr
    }

    private fun pipe(): Expr {
        var expr = or()
        while (match(TokenType.PIPE)) {
            val right = or()
            expr = Expr.Pipe(expr, right)
        }
        return expr
    }

    private fun or(): Expr {
        var expr = and()
        while (match(TokenType.OR)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, and())
        }
        return expr
    }

    private fun and(): Expr {
        var expr = equality()
        while (match(TokenType.AND)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, equality())
        }
        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()
        while (match(TokenType.EQ, TokenType.NEQ)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, comparison())
        }
        return expr
    }

    private fun comparison(): Expr {
        var expr = term()
        while (match(TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, term())
        }
        return expr
    }

    private fun term(): Expr {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, factor())
        }
        return expr
    }

    private fun factor(): Expr {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = previous().type
            expr = Expr.Binary(expr, op, unary())
        }
        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val op = previous().type
            return Expr.Unary(op, unary())
        }
        return call()
    }

    /**
     * 调用 / 下标 / 成员访问 —— 三者的链式优先级相同，从左往右结合。
     * 例如：a.b.c()、arr[0].x、ns.fn(1)(2) 都能正确解析。
     */
    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(TokenType.LPAREN)) {
                // 函数调用
                val args = mutableListOf<Expr>()
                if (!check(TokenType.RPAREN)) {
                    do { args.add(expression()) } while (match(TokenType.COMMA))
                }
                consume(TokenType.RPAREN, "Expect ')' after arguments")
                expr = Expr.Call(expr, args)
            } else if (match(TokenType.LBRACKET)) {
                // 下标
                val index = expression()
                consume(TokenType.RBRACKET, "Expect ']' after index")
                expr = Expr.Index(expr, index)
            } else if (match(TokenType.DOT)) {
                // 新增：成员访问 a.b
                val name = consume(TokenType.IDENT, "Expect member name after '.'").lexeme
                expr = Expr.MemberAccess(expr, name)
            } else break
        }
        return expr
    }

    private fun primary(): Expr {
        if (match(TokenType.FALSE))  return Expr.Literal(false)
        if (match(TokenType.TRUE))   return Expr.Literal(true)
        if (match(TokenType.NULL))   return Expr.Literal(null)
        if (match(TokenType.NUMBER)) return Expr.Literal(previous().literal)
        if (match(TokenType.STRING)) return Expr.Literal(previous().literal)
        if (match(TokenType.IDENT))  return Expr.Variable(previous().lexeme)
        if (match(TokenType.THIS))   return Expr.This()
        if (match(TokenType.SUPER)) return Expr.Super()

        if (match(TokenType.LPAREN)) {
            val first = expression()
            consume(TokenType.RPAREN, "Expect ')' after expression")
            return first
        }

        if (match(TokenType.LBRACKET)) {
            val elements = mutableListOf<Expr>()
            if (!check(TokenType.RBRACKET)) {
                do { elements.add(expression()) } while (match(TokenType.COMMA))
            }
            consume(TokenType.RBRACKET, "Expect ']' after array")
            return Expr.ArrayLit(elements)
        }

        // 匿名函数：func(a,b) { ... }
        if (match(TokenType.FUNC)) {
            consume(TokenType.LPAREN, "Expect '(' after 'func'")
            val params = mutableListOf<String>()
            if (!check(TokenType.RPAREN)) {
                do {
                    params.add(consume(TokenType.IDENT, "Expect parameter name").lexeme)
                } while (match(TokenType.COMMA))
            }
            consume(TokenType.RPAREN, "Expect ')' after parameters")
            return Expr.FuncExpr(params, block())
        }

        if (match(TokenType.DOLLAR)) return Expr.Dollar()

        throw NovaSyntaxError("Line ${peek().line}: Unexpected token ${peek()}")
    }

    // ============================================================
    //                        工具方法
    // ============================================================

    private fun match(vararg types: TokenType): Boolean {
        for (t in types) if (check(t)) { advance(); return true }
        return false
    }
    private fun check(type: TokenType) = !isAtEnd() && peek().type == type
    private fun checkNext(type: TokenType): Boolean {
        if (pos + 1 >= tokens.size) return false
        return tokens[pos + 1].type == type
    }

    /** 检查“下下个” token 是 type（不前进） */
    private fun checkNextNext(type: TokenType): Boolean {
        if (pos + 2 >= tokens.size) return false
        return tokens[pos + 2].type == type
    }
    private fun advance(): Token = tokens[pos++]
    private fun isAtEnd() = peek().type == TokenType.EOF
    private fun peek() = tokens[pos]
    private fun previous() = tokens[pos - 1]
    private fun consume(type: TokenType, msg: String): Token {
        if (check(type)) return advance()
        throw NovaSyntaxError("Line ${peek().line}: $msg. Got ${peek()}")
    }
}