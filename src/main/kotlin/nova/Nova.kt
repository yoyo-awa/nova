package nova

object Nova {

    @JvmStatic
    @JvmOverloads
    fun run(source: String, args: List<String> = emptyList()): List<Stmt> {
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        Interpreter(args).interpret(ast)   // ← 传进去
        return ast
    }
}