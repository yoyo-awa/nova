package nova

object Runner {
    fun run(
        source: String,
        args: List<String> = emptyList(),
        fileName: String? = null,
        jarDir: String = ".",
        libDir: String = ".",
    ): List<Stmt> {
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        val expandedAst = MacroExpander().expand(ast)
        Interpreter(args, fileName, jarDir, libDir).interpret(expandedAst)
        return expandedAst
    }
}