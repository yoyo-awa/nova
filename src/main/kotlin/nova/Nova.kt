package nova

object Nova {

    @JvmStatic
    @JvmOverloads
    fun run(source: String, args: List<String> = emptyList(), basePath: String = "."): List<Stmt> {
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        Interpreter(args, basePath).interpret(ast)   // ← 传进去
        return ast
    }
}