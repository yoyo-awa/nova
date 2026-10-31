package nova

/**
 * TokenType 枚举：定义 Nova 语言所有词法单元类型。
 * 本次变更：
 *   - LET → NEW       （变量声明关键字换成 var）
 *   - FN  → FUNC      （函数定义关键字换成 func）
 *   - 新增 NAMESPACE  （命名空间声明）
 *   - 新增 DOT        （成员访问运算符 .）
 */
enum class TokenType {
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