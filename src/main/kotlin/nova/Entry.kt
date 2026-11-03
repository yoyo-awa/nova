package nova

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Nova command-line entry point.
 *
 * Usage:
 *   `nova <file.nv> [args...]`
 *
 * With args: first arg is file path, rest are passed to Nova's getArgs().
 * Without args: print usage and exit.
 */
fun main(args: Array<String>) {
    // 先检查 --version
    if (args.contains("--version") || args.contains("-v")) {
        val version = readVersionFromResources()
        println("Nova $version")
        return
    }

    if (args.isEmpty()) {
        System.err.println("Usage: nova <file.nv> [args...]")
        exitProcess(1)
    }

    val file = args[0]
    val extraArgs = args.drop(1)
    runFile(file, extraArgs)
}

/**
 * Read file and execute.
 */
private fun runFile(path: String, extraArgs: List<String>) {
    // Convert to Path
    val p: Path = try {
        Path.of(path)
    } catch (e: InvalidPathException) {
        System.err.println("Invalid path: ${e.message}")
        exitProcess(1)
    }

    // Check existence
    if (!Files.exists(p)) {
        System.err.println("File not found: $p")
        exitProcess(1)
    }
    // Check not a directory
    if (Files.isDirectory(p)) {
        System.err.println("Not a file (directory): $p")
        exitProcess(1)
    }

    // Read + run
    try {
        val source = Files.readString(p, StandardCharsets.UTF_8)
        Nova.run(source, extraArgs, p.parent?.toString() ?: ".")
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

private fun readVersionFromResources(): String {
    val stream = object {}.javaClass.getResourceAsStream("/version")
    return stream?.bufferedReader()?.readText()?.trim() ?: "unknown"
}