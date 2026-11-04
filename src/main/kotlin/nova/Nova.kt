package nova

object Nova {
    @JvmStatic
    @JvmOverloads
    fun run(
        source: String,
        args: List<String> = emptyList(),
        fileName: String? = null
    ): List<Stmt> {
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        Interpreter(args, fileName).interpret(ast)
        return ast
    }
}