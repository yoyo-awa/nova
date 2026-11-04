package nova

/**
 * 表达式节点。
 * 变更点：
 *   - 新增 MemberAccess：object.name  （读取成员）
 *   - 新增 MemberSet：   object.name = v（写入成员）
 */
sealed class Expr {
    data class Literal(val value: Any?) : Expr()
    data class Variable(val name: String) : Expr()
    data class Assign(val name: String, val value: Expr) : Expr()

    /** a.b —— 读取对象的成员（用于命名空间） */
    data class MemberAccess(val target: Expr, val name: String) : Expr()

    /** a.b = v —— 给对象的成员赋值 */
    data class MemberSet(val target: Expr, val name: String, val value: Expr) : Expr()

    data class Binary(val left: Expr, val op: TokenType, val right: Expr) : Expr()
    data class Unary(val op: TokenType, val operand: Expr) : Expr()
    data class Call(val callee: Expr, val args: List<Expr>) : Expr()
    data class ArrayLit(val elements: List<Expr>) : Expr()
    data class Index(val target: Expr, val index: Expr) : Expr()
    data class IndexSet(val target: Expr, val index: Expr, val value: Expr) : Expr()
    data class FuncExpr(val params: List<String>, val body: List<Stmt>) : Expr()
    data class PairLit(val key: Expr, val value: Expr) : Expr()
    data class Pipe(val left: Expr, val right: Expr) : Expr()
    data class Dollar(val dummy: Unit = Unit) : Expr()
    data class This(val dummy: Unit = Unit) : Expr()
    /** super */
    data class Super(val dummy: Unit = Unit) : Expr()
}

/**
 * 语句节点。
 * 变更点：
 *   - Let → New（语义相同，只是名字对应关键字 var）
 *   - FnDecl → FuncDecl（对应关键字 func）
 *   - 新增 NamespaceDecl：namespace Name { ... }
 */
sealed class Stmt {
    data class Expression(val expr: Expr) : Stmt()

    /** var name = init; */
    data class Var(
        val name: String,
        val initializer: Expr,
        val isConst: Boolean = false   // ← 新增
    ) : Stmt()

    data class If(
        val condition: Expr,
        val thenBranch: List<Stmt>,
        val elseBranch: List<Stmt>?
    ) : Stmt()

    data class While(val condition: Expr, val body: List<Stmt>) : Stmt()
    data class For(
        val init: Stmt?,
        val condition: Expr?,
        val update: Expr?,
        val body: List<Stmt>
    ) : Stmt()

    data class Return(val value: Expr?) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    object Break : Stmt()
    object Continue : Stmt()
    data class TryCatch(
        val tryBlock: List<Stmt>,
        val catchVar: String,
        val catchBlock: List<Stmt>
    ) : Stmt()

    /** func name(params) { ... } */
    data class FuncDecl(
        val name: String,
        val params: List<String>,
        val body: List<Stmt>
    ) : Stmt()

    /** namespace Name { ... } —— 新增 */
    data class NamespaceDecl(
        val name: String,
        val body: List<Stmt>
    ) : Stmt()

    data class IndexAssign(
        val target: Expr,
        val index: Expr,
        val value: Expr
    ) : Stmt()


    /** 类字段：修饰符 + 名字 + 初始值 */
    data class ClassField(
        val isPublic: Boolean,
        val name: String,
        val initializer: Expr,
        val isConst: Boolean = false
    )

    /** class Name { ... } */
    data class ClassDecl(
        val name: String,
        val parent: String?,
        val fields: List<ClassField>,
        val methods: List<ClassMethod>
    ) : Stmt()

    /** 类方法：修饰符 + 名字 + 参数 + 函数体 */
    data class ClassMethod(
        val isPublic: Boolean,
        val name: String,
        val params: List<String>,
        val body: List<Stmt>
    )

    data class ForEachIndex(
        val varName: String,
        val collectionExpr: Expr,     // 含 $ 的表达式
        val sizeExpr: Expr?,          // ← 新增：可选“显式长度”
        val body: List<Stmt>
    ) : Stmt()

    data class Property(
        val name: String,
        val initializer: Expr,
        val isConst: Boolean = false,
        val isRuntime: Boolean = false
    ) : Stmt()


    /** module { ... } 或 module name { ... } */
    data class ModuleDecl(
        val name: String?,
        val body: List<Stmt>
    ) : Stmt()

    /** import math; —— 标准库；import .utils; —— 本地文件 */
    data class Import(
        val isRelative: Boolean,   // 是否有前导点
        val parts: List<String>    // 路径部分
    ) : Stmt()
}