package nova

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    if (args.isEmpty()) {
        System.err.println("Usage: nova <file.nv> [args...]")
        exitProcess(1)
    }

    val file = args[0]
    val extraArgs = args.drop(1)
    runFile(file, extraArgs)
}

private fun runFile(path: String, extraArgs: List<String>) {
    val p: Path = try {
        Path.of(path)
    } catch (e: InvalidPathException) {
        System.err.println("Invalid path: ${e.message}")
        exitProcess(1)
    }

    if (!Files.exists(p)) {
        System.err.println("File not found: $p")
        exitProcess(1)
    }
    if (Files.isDirectory(p)) {
        System.err.println("Not a file (directory): $p")
        exitProcess(1)
    }



    try {
        val source = Files.readString(p, StandardCharsets.UTF_8)
        val codeSource = Class.forName("nova.EntryKt")
            .protectionDomain?.codeSource?.location
        val jarDir = codeSource?.let {
            java.io.File(it.toURI()).parentFile?.absolutePath
        } ?: "."

        Nova.run(source, extraArgs, p.toString(), jarDir)
    } catch (e: java.io.IOException) {
        System.err.println("Failed to read file: ${e.message}")
        exitProcess(1)
    } catch (e: NovaSyntaxError) {
        System.err.println("Syntax error: ${e.message}")
        exitProcess(1)
    } catch (e: NovaRuntimeError) {
        System.err.println("Runtime error: ${e.message}")
        exitProcess(1)
    }
}