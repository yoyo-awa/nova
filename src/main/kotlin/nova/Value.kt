package nova

import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JFrame
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JTextField

/**
 * Nova 的文件句柄。
 * 在 File.open 的回调里传给用户——回调结束后自动关闭。
 */
class NovaFile(
    val path: Path,
    val mode: String
) {
    // 读
    private var reader: BufferedReader? = null

    // 写
    private var writer: BufferedWriter? = null

    init {
        when (mode) {
            "r" -> {
                reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)
            }
            "w" -> {
                writer = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            }
            "a" -> {
                writer = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                )
            }
            else -> throw NovaRuntimeError("File.open: unknown mode '$mode'")
        }
    }

    /** 读整个文件 */
    fun readAll(): String {
        val r = reader ?: throw NovaRuntimeError("File not opened for reading")
        return r.readText()
    }

    /** 读一行 */
    fun readLine(): String? {
        val r = reader ?: throw NovaRuntimeError("File not opened for reading")
        return r.readLine()
    }

    /** 读所有行（数组） */
    fun readLines(): MutableList<Any?> {
        val r = reader ?: throw NovaRuntimeError("File not opened for reading")
        val lines = mutableListOf<Any?>()
        var line: String? = r.readLine()
        while (line != null) {
            lines.add(line)
            line = r.readLine()
        }
        return lines
    }

    /** 写 */
    fun write(text: String) {
        val w = writer ?: throw NovaRuntimeError("File not opened for writing")
        w.write(text)
    }

    /** 写一行 */
    fun writeLine(text: String) {
        val w = writer ?: throw NovaRuntimeError("File not opened for writing")
        w.write(text)
        w.newLine()
    }

    /** 关闭 */
    fun close() {
        try {
            reader?.close()
        } catch (_: Exception) {}
        try {
            writer?.close()
        } catch (_: Exception) {}
        reader = null
        writer = null
    }

    override fun toString() = "<file $path ($mode)>"
}
/**
 * Nova 的 GUI 文本框。
 */
class NovaTextField(placeholder: String) {
    val textField = JTextField(20)
    private val placeholderText = placeholder

    init {
        // 初始显示 placeholder
        textField.text = placeholder
        textField.foreground = Color.GRAY

        textField.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent) {
                // 获得焦点时，如果还是 placeholder，清空
                if (textField.text == placeholderText) {
                    textField.text = ""
                    textField.foreground = Color.BLACK
                }
            }
            override fun focusLost(e: FocusEvent) {
                // 失去焦点时，如果为空，恢复 placeholder
                if (textField.text.isEmpty()) {
                    textField.text = placeholderText
                    textField.foreground = Color.GRAY
                }
            }
        })
    }

    var text: String
        get() = if (textField.text == placeholderText) "" else textField.text
        set(value) {
            if (value.isEmpty()) {
                textField.text = placeholderText
                textField.foreground = Color.GRAY
            } else {
                textField.text = value
                textField.foreground = Color.BLACK
            }
        }

    override fun toString() = "<textfield>"
}


class NovaRuntimeError(message: String) : RuntimeException(message)

/**
 * NovaFunction（函数值 / 闭包）
 * 记录参数、函数体、以及定义时的环境（闭包捕获）。
 */
class NovaFunction(
    val params: List<String>,
    val body: List<Stmt>,
    val closure: Environment,
    val name: String = "<anonymous>"
) {
    override fun toString() = "<fn $name>"
}

/**
 * NovaNamespace（命名空间运行时值）—— 新增
 *
 * 本质上就是一个"装着变量的字典"。
 * - 通过 novaGet(name) 读取成员
 * - 通过 novaSet(name, value) 修改成员
 * 支持命名空间里嵌套命名空间（成员本身就是另一个 NovaNamespace）。
 */
class NovaNamespace(val name: String) {
    // 命名空间内部的变量表
    private val members = mutableMapOf<String, Any?>()

    /** 定义/覆盖成员 */
    fun define(member: String, value: Any?) {
        members[member] = value
    }

    /** 读取成员，若不存在则报错 */
    fun get(member: String): Any? {
        if (!members.containsKey(member))
            throw NovaRuntimeError("Namespace '$name' has no member '$member'")
        return members[member]
    }

    /** 修改成员，若不存在则报错（避免误创建） */
    fun set(member: String, value: Any?) {
        if (!members.containsKey(member))
            throw NovaRuntimeError("Namespace '$name' has no member '$member'")
        members[member] = value
    }

    /** 是否包含某成员 */
    fun has(member: String) = members.containsKey(member)

    override fun toString() = "<namespace $name>"
}

class NovaPair(val key: Any?, val value: Any?) {
    override fun toString() = "(${key}:${value})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NovaPair) return false
        return key == other.key && value == other.value
    }

    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}

class NovaMap : MutableMap<Any?, Any?> by mutableMapOf() {
    override fun toString() = "<map>"
}

/**
 * Nova 的类。
 */
class NovaClass(
    val name: String,
    val parent: String?,          // ← 新增
    val fields: List<Stmt.ClassField>,
    val methods: Map<String, ClassMethodInfo>,
    val closure: Environment
) {
    var parentClass: NovaClass? = null     // ← 运行时"父类引用"

    override fun toString() = "<class $name>"
}

/**
 * super 的运行时引用——"从父类找成员"。
 */
class SuperRef(
    val instance: NovaInstance,
    val currentClass: NovaClass,
) {
    override fun toString() = "<super>"
}

/**
 * 类方法的运行时信息。
 */
class ClassMethodInfo(
    val isPublic: Boolean,
    val fn: NovaFunction
)

/**
 * Nova 的实例。
 */
class NovaInstance(val klass: NovaClass) {
    val fields = mutableMapOf<String, Any?>()
    override fun toString() = "<instance of ${klass.name}>"
}

/**
 * 绑定到实例的方法。
 */
class BoundMethod(
    val instance: NovaInstance,
    val fn: NovaFunction,
    val definingClass: NovaClass      // ← 新增：方法定义在哪个类
) {
    override fun toString() = "<bound method ${fn.name}>"
}

/**
 * Nova 的异常对象。
 * catch 块里 e 就是这个类型。
 */
class NovaError(
    val message: String,
    val type: String = "RuntimeError"
) {
    override fun toString() = "$type: $message"
}

/**
 * 绑定到某个对象的"内置方法"。
 * 调用时直接执行 Kotlin lambda。
 */
class BoundBuiltinMethod(
    val name: String,
    val fn: () -> Any?
) {
    override fun toString() = "<bound builtin $name>"
}

/**
 * Nova 的 GUI 窗口。
 */
class NovaWindow(val title: String, val width: Int, val height: Int) {
    val frame = JFrame(title)
    val panel = JPanel()

    init {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(width, height)
        panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)
        frame.add(panel)
    }

    fun show() {
        frame.isVisible = true
    }

    override fun toString() = "<window $title>"
}

/**
 * Nova 的 GUI 按钮。
 */
class NovaButton(text: String) {
    val button = JButton(text)

    var text: String
        get() = button.text
        set(value) {
            button.text = value
        }

    override fun toString() = "<button ${button.text}>"
}

/**
 * Nova 的 GUI 标签。
 */
class NovaLabel(text: String) {
    val label = JLabel(text)

    var text: String
        get() = label.text               // ← 读 label 的
        set(value) {
            label.text = value           // ← 写 label 的
        }

    override fun toString() = "<label ${label.text}>"
}

/**
 * 绑定到对象的"内置方法"——带参数。
 * 用于 gui.add(x)、button.addEvent(fn) 这类调用。
 */
class BoundBuiltinMethodWithArgs(
    val name: String,
    val fn: (List<Any?>) -> Any?
) {
    override fun toString() = "<bound builtin $name>"
}