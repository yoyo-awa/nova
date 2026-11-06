package nova

class NovaSyntaxError(message: String) : RuntimeException(message)

/**
 * 词法分析器：源码 → Token 流
 * 变更点：
 *   - 关键字表加入 namespace / var / func
 *   - operator() 中加入 '.' → DOT
 */
class Lexer(private val source: String) {

    private var pos = 0
    private var line = 1
    private val tokens = mutableListOf<Token>()

    /** 关键字映射表（已更新） */
    private val keywords = mapOf(
        "import"    to TokenType.IMPORT,
        "module"    to TokenType.MODULE,
        "var"       to TokenType.VAR,          // 变量声明
        "const"     to TokenType.CONST,
        "func"      to TokenType.FUNC,         // 函数定义
        "namespace" to TokenType.NAMESPACE,    // 命名空间
        "if"        to TokenType.IF,
        "else"      to TokenType.ELSE,
        "while"     to TokenType.WHILE,
        "for"       to TokenType.FOR,
        "return"    to TokenType.RETURN,
        "true"      to TokenType.TRUE,
        "false"     to TokenType.FALSE,
        "null"      to TokenType.NULL,
        "and"       to TokenType.AND,
        "or"        to TokenType.OR,
        "not"       to TokenType.NOT,
        "break"     to TokenType.BREAK,
        "continue"  to TokenType.CONTINUE,
        "try"       to TokenType.TRY,
        "catch"     to TokenType.CATCH,
        "public"    to TokenType.PUBLIC,
        "property"  to TokenType.PROPERTY,
        "runtime" to TokenType.RUNTIME,
        "class"     to TokenType.CLASS,
        "private"   to TokenType.PRIVATE,
        "this"      to TokenType.THIS,
        "super"     to TokenType.SUPER,
    )

    fun tokenize(): List<Token> {
        while (pos < source.length) {
            val c = source[pos]
            when {
                // 跳过空白
                c == ' ' || c == '\t' || c == '\r' -> pos++
                // 换行
                c == '\n' -> { line++; pos++ }
                // 单行注释
                c == '/' && peek(1) == '/' -> {
                    while (pos < source.length && source[pos] != '\n') pos++
                }
                // 多行注释
                c == '/' && peek(1) == '*' -> {
                    pos += 2
                    while (pos + 1 < source.length &&
                           !(source[pos] == '*' && source[pos + 1] == '/')) {
                        if (source[pos] == '\n') line++
                        pos++
                    }
                    pos += 2
                }
                // 数字
                c.isDigit() -> number()
                // 字符串
                c == '"' -> string()
                // 标识符 / 关键字
                c.isLetter() || c == '_' -> identifier()
                // 其它：运算符和分隔符
                else -> operator()
            }
        }
        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun peek(k: Int): Char? =
        if (pos + k < source.length) source[pos + k] else null

    private fun number() {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        if (pos < source.length && source[pos] == '.' &&
            pos + 1 < source.length && source[pos + 1].isDigit()) {
            pos++
            while (pos < source.length && source[pos].isDigit()) pos++
            val text = source.substring(start, pos)
            tokens.add(Token(TokenType.NUMBER, text, text.toDouble(), line))
        } else {
            val text = source.substring(start, pos)
            tokens.add(Token(TokenType.NUMBER, text, text.toInt(), line))
        }
    }

    private fun string() {
        pos++
        val sb = StringBuilder()
        while (pos < source.length && source[pos] != '"') {
            if (source[pos] == '\\' && pos + 1 < source.length) {
                pos++
                sb.append(when (source[pos]) {
                    'n' -> '\n'; 't' -> '\t'; '"' -> '"'; '\\' -> '\\'
                    else -> source[pos]
                })
            } else sb.append(source[pos])
            pos++
        }
        if (pos >= source.length)
            throw NovaSyntaxError("Line $line: Unterminated string")
        pos++
        tokens.add(Token(TokenType.STRING, sb.toString(), sb.toString(), line))
    }

    private fun identifier() {
        val start = pos
        while (pos < source.length &&
               (source[pos].isLetterOrDigit() || source[pos] == '_')) pos++
        val text = source.substring(start, pos)
        val type = keywords[text] ?: TokenType.IDENT
        tokens.add(Token(type, text, null, line))
    }

    private fun operator() {
        val c = source[pos]
        val next = peek(1) ?: '\u0000'
        val two = "$c$next"

        // 双字符运算符优先
        when (two) {
            "==" -> { add(TokenType.EQ, two); return }
            "!=" -> { add(TokenType.NEQ, two); return }
            "<=" -> { add(TokenType.LTE, two); return }
            ">=" -> { add(TokenType.GTE, two); return }
            "&&" -> { add(TokenType.AND, two); return }
            "||" -> { add(TokenType.OR, two); return }
            "|>" -> { add(TokenType.PIPE, two); return }
            "->" -> { add(TokenType.SINGLE_ARROW, two); return }
            "=>" -> { add(TokenType.DOUBLE_ARROW, two); return }
        }
        when (c) {
            '+' -> add(TokenType.PLUS, "+")
            '-' -> add(TokenType.MINUS, "-")
            '*' -> add(TokenType.STAR, "*")
            '/' -> add(TokenType.SLASH, "/")
            '%' -> add(TokenType.PERCENT, "%")
            '<' -> add(TokenType.LT, "<")
            '>' -> add(TokenType.GT, ">")
            '=' -> add(TokenType.ASSIGN, "=")
            '.' -> add(TokenType.DOT, ".")          // 新增：成员访问
            '(' -> add(TokenType.LPAREN, "(")
            ')' -> add(TokenType.RPAREN, ")")
            '{' -> add(TokenType.LBRACE, "{")
            '}' -> add(TokenType.RBRACE, "}")
            '[' -> add(TokenType.LBRACKET, "[")
            ']' -> add(TokenType.RBRACKET, "]")
            ',' -> add(TokenType.COMMA, ",")
            ';' -> add(TokenType.SEMICOLON, ";")
            '!' -> add(TokenType.NOT, "!")
            ':' -> add(TokenType.COLON, ":")
            '$' -> add(TokenType.DOLLAR, "$")
            '@' -> add(TokenType.AT, "@")
            else -> throw NovaSyntaxError("Line $line: Unexpected character '$c'")
        }
    }

    private fun add(type: TokenType, lexeme: String) {
        tokens.add(Token(type, lexeme, null, line))
        pos += lexeme.length
    }
}