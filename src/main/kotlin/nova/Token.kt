package nova


enum class TokenType {
    IMPORT,
    MODULE,
    // ---------- 字面量 ----------
    NUMBER, STRING, IDENT,

    // ---------- 关键字 ----------
    VAR,       // var        变量声明（原 let）
    CONST,
    FUNC,         // func       函数定义（原 fn）
    NAMESPACE,    // namespace  命名空间声明（新增）
    IF, ELSE, WHILE, FOR,
    TRUE, FALSE, NULL,
    AND, OR, NOT,
    CLASS, THIS, PRIVATE, PUBLIC,
    PROPERTY,
    RUNTIME,

    // ---------- 运算符 ----------
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, GT, LTE, GTE, ASSIGN,
    DOT,
    SINGLE_ARROW, DOUBLE_ARROW,// .          成员访问（新增）

    // ---------- 分隔符 ----------
    LPAREN, RPAREN, LBRACE, RBRACE,
    LBRACKET, RBRACKET, COMMA, SEMICOLON,
    COLON,
    PIPE, DOLLAR,

    //----------- 语句 -----------
    BREAK, CONTINUE, RETURN, TRY, CATCH,

    // ---------- 特殊 ----------
    EOF,
    SUPER
}

data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int
) {
    override fun toString() = "$type($lexeme)"
}