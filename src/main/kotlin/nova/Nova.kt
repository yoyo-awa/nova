package nova

object Nova {
    @JvmStatic
    @JvmOverloads
    fun run(
        source: String,
        args: List<String> = emptyList(),
        fileName: String? = null,
        jarDir: String = ".",
        libDir: String = ".",
    ): List<Stmt> {
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        Interpreter(args, fileName, jarDir,libDir).interpret(ast)
        return ast
    }
}