package com.medic.app.eval

import com.medic.app.core.SafetyTree
import com.medic.app.core.Severity
import java.io.File

/**
 * Headless runner for the triage evaluation.
 *
 * This exists so the harness scores the SHIPPED Kotlin. The repo also contains
 * scripts/verify_safety_tree.py, a Python mirror written when no Kotlin
 * compiler was available — useful as a cross-check, but scoring it would
 * measure a copy that can silently drift from the code on the device.
 *
 * Usage:
 *   EvalCli <cases.json> <naive|safetytree> > results.json
 *
 * Reads a JSON array of {id, text, expected}, writes a JSON array of
 * {id, severity, rule}. Deliberately hand-rolled JSON: the corpus shape is
 * three string fields and pulling a parser into jvmMain for that would be
 * more machinery than the job needs.
 */

/**
 * The baseline: keyword matching with no negation handling.
 *
 * This is what a competent engineer writes in an afternoon, and it is a fair
 * representation of how the task is handled before this project — it is not a
 * straw man. It scans for the same injury vocabulary the real tree uses and
 * assigns the same severities. The single thing it does not do is ask whether
 * the words were negated.
 */
object NaiveKeywordTriage {

    private val critical = listOf(
        "not breathing", "isn't breathing", "stopped breathing", "no breathing",
        "can't breathe", "cannot breathe", "no pulse",
        "third degree", "3rd degree", "charred", "circumferential burn",
        "spurting", "gushing", "hemorrhage", "arterial"
    )
    private val serious = listOf(
        "bleeding", "blood", "shock", "fracture", "broken bone", "head injury",
        "concussion", "unconscious", "spine", "neck injury", "hypothermia",
        "frostbite", "second degree", "2nd degree"
    )
    private val moderate = listOf(
        "infection", "red streak", "swelling", "sprain", "mild burn", "rash"
    )
    private val minor = listOf(
        "small cut", "scrape", "minor cut", "first degree", "1st degree",
        "splinter", "bruise"
    )

    fun evaluate(input: String): Pair<Severity, String> {
        val t = input.lowercase()
        critical.firstOrNull { t.contains(it) }?.let { return Severity.CRITICAL to "KW:$it" }
        serious.firstOrNull { t.contains(it) }?.let { return Severity.SERIOUS to "KW:$it" }
        moderate.firstOrNull { t.contains(it) }?.let { return Severity.MODERATE to "KW:$it" }
        minor.firstOrNull { t.contains(it) }?.let { return Severity.MINOR to "KW:$it" }
        return Severity.UNKNOWN to "KW:none"
    }
}

private fun jsonEscape(s: String): String = buildString {
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
    }
}

/** Pull the string value of [key] out of one flat JSON object. */
private fun field(obj: String, key: String): String? {
    val marker = "\"$key\""
    var i = obj.indexOf(marker)
    if (i < 0) return null
    i = obj.indexOf(':', i + marker.length)
    if (i < 0) return null
    while (i < obj.length && obj[i] != '"') {
        if (obj[i] == ',' || obj[i] == '}') return null   // non-string value
        i++
    }
    if (i >= obj.length) return null
    val sb = StringBuilder()
    var j = i + 1
    while (j < obj.length) {
        val c = obj[j]
        if (c == '\\' && j + 1 < obj.length) {
            when (val n = obj[j + 1]) {
                'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                '"' -> sb.append('"');  '\\' -> sb.append('\\')
                else -> sb.append(n)
            }
            j += 2
        } else if (c == '"') break
        else { sb.append(c); j++ }
    }
    return sb.toString()
}

/** Split a JSON array of flat objects into its object substrings. */
private fun objects(json: String): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    var start = -1
    var inStr = false
    var esc = false
    for ((i, c) in json.withIndex()) {
        when {
            esc -> esc = false
            c == '\\' && inStr -> esc = true
            c == '"' -> inStr = !inStr
            inStr -> {}
            c == '{' -> { if (depth == 0) start = i; depth++ }
            c == '}' -> { depth--; if (depth == 0 && start >= 0) out.add(json.substring(start, i + 1)) }
        }
    }
    return out
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: EvalCli <cases.json> <naive|safetytree>")
        kotlin.system.exitProcess(2)
    }
    val cases = objects(File(args[0]).readText())
    val system = args[1]

    val rows = cases.mapNotNull { obj ->
        val id = field(obj, "id") ?: return@mapNotNull null
        val text = field(obj, "text") ?: return@mapNotNull null
        val (sev, rule) = when (system) {
            "naive" -> NaiveKeywordTriage.evaluate(text)
            "safetytree" -> SafetyTree.evaluate(text).let { it.severity to it.matchedRule }
            else -> error("unknown system: $system")
        }
        """{"id":"${jsonEscape(id)}","severity":"$sev","rule":"${jsonEscape(rule)}"}"""
    }
    println(rows.joinToString(",\n", "[\n", "\n]"))
}
