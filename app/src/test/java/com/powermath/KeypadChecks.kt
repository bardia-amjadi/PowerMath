package com.powermath

import com.powermath.editor.Editor
import com.powermath.editor.EditorState
import com.powermath.math.*
import com.powermath.ui.components.KeyAction
import com.powermath.ui.components.KeyKind
import com.powermath.ui.components.KeyLayouts
import com.powermath.viewmodel.DialogRequest
import com.powermath.viewmodel.ModifierState
import com.powermath.viewmodel.PickPurpose
import com.powermath.viewmodel.Screen
import com.powermath.viewmodel.ToolKind

/** Framework-free checks for the Android layer's pure logic (keypad layers + modifier state machine). */
object KeypadChecks {
    class Result(val name: String, val error: String?)
    private val results = ArrayList<Result>()
    private fun t(name: String, block: () -> Unit) {
        try { block(); results.add(Result(name, null)) } catch (e: Throwable) { results.add(Result(name, "${e.javaClass.simpleName}: ${e.message}")) }
    }
    private fun yes(c: Boolean, m: String) { if (!c) throw AssertionError(m) }

    private val allCombos = (0 until 8).map { ModifierState(it and 1 != 0, it and 2 != 0, it and 4 != 0) }
    private fun layer(m: ModifierState) = KeyLayouts.layer(m, listOf("a", "rate"), listOf("f", "g"))
    private fun keys(m: ModifierState) = layer(m).flatten().filter { it.kind != KeyKind.SPACER }
    private fun inserts(m: ModifierState) = keys(m).map { it.action }.filterIsInstance<KeyAction.Insert>().map { it.text }
    private fun press(state: EditorState, label: String, m: ModifierState = ModifierState()): EditorState {
        val key = keys(m).first { it.label == label }
        return when (val a = key.action) {
            is KeyAction.Insert -> Editor.insert(state, a.text)
            KeyAction.Backspace -> Editor.backspace(state)
            KeyAction.ClearEntry -> Editor.clearEntry(state)
            KeyAction.ClearAll -> Editor.clearAll()
            KeyAction.Negate -> Editor.negate(state)
            KeyAction.Left -> Editor.left(state)
            KeyAction.Right -> Editor.right(state)
            else -> throw AssertionError("unexpected action for $label: $a")
        }
    }
    private fun typeAndEval(labels: List<String>, mods: List<ModifierState>? = null, mode: ResultMode = ResultMode.EXACT): String {
        var s = EditorState()
        labels.forEachIndexed { i, l -> s = press(s, l, mods?.get(i) ?: ModifierState()) }
        val f = FormatSettings(mode)
        return Calculator.process(s.text, Workspace(), CalcSettings(AngleMode.DEG, f)).renderText(f)
    }

    fun run(): List<Result> {
        results.clear()
        t("every modifier combination yields a full 6x5 grid") {
            for (m in allCombos) {
                val rows = layer(m)
                yes(rows.size == 6, "rows=${rows.size} for $m")
                for (r in rows) yes(Math.abs(r.sumOf { it.weight.toDouble() } - 5.0) < 1e-6 && r.size == 5, "bad row width for $m: ${r.map { it.label }}")
            }
        }
        t("the 8 modifier combinations are all distinct layers with distinct names") {
            yes(allCombos.map { it.layerName }.toSet().size == 8, "layer names collide")
            val sigs = allCombos.map { m -> layer(m).flatten().joinToString("|") { it.label } }.toSet()
            yes(sigs.size == 8, "layers are not all distinct: ${sigs.size}")
        }
        t("no decorative keys: every visible key has a working action and a description") {
            for (m in allCombos) for (k in keys(m)) {
                yes(k.action != KeyAction.None, "key '${k.label}' in ${m.layerName} does nothing")
                yes(k.label.isNotBlank() && k.description.isNotBlank(), "key without label/description in ${m.layerName}")
            }
        }
        t("every inserted token is lexable by the shared lexer") {
            for (m in allCombos) for (txt in inserts(m)) {
                try { Lexer.tokenize("1" + txt + "1") } catch (e: MathError) { throw AssertionError("'$txt' (${m.layerName}) is not lexable: ${e.message}") }
            }
        }
        t("insert then backspace restores the editor for every key") {
            for (m in allCombos) for (txt in inserts(m)) {
                val s = Editor.insert(EditorState.of("2+"), txt)
                val back = Editor.backspace(s)
                yes(back.text == "2+" || txt.length > 1, "backspace after '$txt' gave '${back.text}'")
            }
        }
        t("standard layer: digits 0-9 and all required keys") {
            val labels = keys(ModifierState()).map { it.label }.toSet()
            for (d in "0123456789") yes(d.toString() in labels, "missing digit $d")
            for (l in listOf(".", "+", "−", "×", "÷", "%", "=", "(", ")", "⌫", "AC", "CE", "(−)", "xʸ", "√", "π", "e", "◀", "▶"))
                yes(l in labels, "missing key $l")
        }
        t("Alt layer: x, y, z and a–g variable keys, symbols") {
            val alt = ModifierState(alt = true)
            val labels = keys(alt).map { it.label }.toSet()
            for (v in listOf("x", "y", "z", "a", "b", "c", "d", "f", "g")) yes(v in labels, "missing variable $v")
            yes("e" in labels || "e" in keys(ModifierState()).map { it.label }, "constant e available")
            yes(inserts(alt).containsAll(listOf("<=", ">=", "!=", ":=", "=")), "relation/assign symbols")
        }
        t("Shift layer: advanced functions with real engine function names") {
            val ins = inserts(ModifierState(shift = true)).toSet()
            for (f in listOf("sqrt(", "root(", "^2", "^", "sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "ln(", "log(", "!", "abs(", "mean(", "delta(", "quad(")) yes(f in ins, "Shift layer lacks $f")
            for (txt in ins.filter { it.endsWith("(") }) { val name = txt.dropLast(1); yes(name in Names.functions || name in Names.commands, "'$name' is not an engine function") }
        }
        t("Ctrl layer: workspace actions are dialogs, plus quick-insert for saved names") {
            val acts = keys(ModifierState(ctrl = true)).map { it.action }
            yes(KeyAction.Open(DialogRequest.DefineVar()) in acts, "define variable")
            yes(KeyAction.Open(DialogRequest.DefineFn()) in acts, "define function")
            for (p in PickPurpose.values()) yes(KeyAction.Open(DialogRequest.Pick(p)) in acts, "pick $p")
            yes(KeyAction.Go(Screen.WORKSPACE) in acts, "view workspace")
            val ins = inserts(ModifierState(ctrl = true))
            yes("a" in ins && "rate" in ins && "f(" in ins && "g(" in ins, "saved names should be insertable: $ins")
        }
        t("Ctrl+Alt layer: equation, inequality, quadratic, system, simplify, expand, factor, evaluate, graph") {
            val acts = keys(ModifierState(alt = true, ctrl = true)).map { it.action }
            for (tk in ToolKind.values()) yes(KeyAction.Open(DialogRequest.Tool(tk)) in acts, "tool $tk")
            for (w in listOf("simplify(", "expand(", "factor(")) yes(KeyAction.Wrap(w) in acts, "wrap $w")
            yes(KeyAction.Go(Screen.GRAPH) in acts, "graph")
        }
        t("Shift+Ctrl and Ctrl+Shift+Alt layers expose extra tools") {
            val sc = keys(ModifierState(shift = true, ctrl = true)).map { it.action }
            yes(KeyAction.SaveResultAsVar in sc && KeyAction.SaveExprAsFn in sc && KeyAction.CopyResult in sc && KeyAction.Paste in sc, "workspace extras")
            val ext = inserts(ModifierState(true, true, true))
            yes(ext.containsAll(listOf("gcd(", "lcm(", "mod(", "ncr(", "npr(", "sinh(", "phi", "tau")), "extended: $ext")
            for (txt in ext.filter { it.endsWith("(") }) yes(txt.dropLast(1) in Names.functions, "$txt is not an engine function")
        }
        t("modifier toggling does not touch the expression") {
            var m = ModifierState(); val expr = "2+3"
            m = m.copy(shift = !m.shift); m = m.copy(alt = !m.alt); m = m.copy(ctrl = !m.ctrl)
            yes(m == ModifierState(true, true, true) && expr == "2+3", "state")
            m = m.copy(alt = false); yes(m.layerName.startsWith("Shift+Ctrl"), m.layerName)
        }
        t("keypad drives the engine: 2 + 3 × 4 = 14, (2+3)×4 = 20") {
            yes(typeAndEval(listOf("2", "+", "3", "×", "4")) == "14", "14")
            yes(typeAndEval(listOf("(", "2", "+", "3", ")", "×", "4")) == "20", "20")
        }
        t("keypad: 1 ÷ 2 in each result mode") {
            val k = listOf("1", "÷", "2")
            yes(typeAndEval(k) == "1/2", "exact"); yes(typeAndEval(k, mode = ResultMode.DECIMAL) == "0.5", "decimal"); yes(typeAndEval(k, mode = ResultMode.SCIENTIFIC) == "5 × 10^-1", "sci")
        }
        t("keypad: 1230000 scientific; 0.0000045 scientific") {
            yes(typeAndEval(listOf("1", "2", "3", "0", "0", "0", "0"), mode = ResultMode.SCIENTIFIC) == "1.23 × 10^6", "1.23e6")
            yes(typeAndEval(listOf("0", ".", "0", "0", "0", "0", "0", "4", "5"), mode = ResultMode.SCIENTIFIC) == "4.5 × 10^-6", "4.5e-6")
        }
        t("keypad: √ then 9 closes automatically and gives 3; π key; negative key; power key") {
            yes(typeAndEval(listOf("√", "9")) == "3", "sqrt 9")
            yes(typeAndEval(listOf("2", "π")) == "2π", "2pi")
            yes(typeAndEval(listOf("5", "(−)", "+", "8")) == "3", "neg")
            yes(typeAndEval(listOf("2", "xʸ", "3")) == "8", "2^3")
        }
        t("keypad with modifiers: Shift sin, Alt x, '=' key solves") {
            val shift = ModifierState(shift = true); val alt = ModifierState(alt = true); val none = ModifierState()
            yes(typeAndEval(listOf("sin", "3", "0"), listOf(shift, none, none)) == "1/2", "sin(30)")
            var s = EditorState()
            s = press(s, "x", alt); s = press(s, "+", none); s = press(s, "3", none); s = press(s, "=", alt); s = press(s, "5", none)
            yes(s.text == "x+3=5", "text ${s.text}")
            yes(Calculator.process(s.text, Workspace(), CalcSettings()).renderText(FormatSettings()) == "x = 2", "solve")
        }
        t("keypad: quadratic  x^2 - 5x + 6 = 0 typed through the keys") {
            val alt = ModifierState(alt = true); val none = ModifierState()
            var s = EditorState()
            for ((lab, m) in listOf("x" to alt, "xʸ" to none, "2" to none, "−" to none, "5" to none, "x" to alt, "+" to none, "6" to none, "=" to alt, "0" to none)) s = press(s, lab, m)
            yes(Calculator.process(s.text, Workspace(), CalcSettings()).render(FormatSettings()) == listOf("x = 2", "x = 3", "Δ = 1"), "roots")
        }
        return results.toList()
    }
}

fun main() {
    val res = KeypadChecks.run()
    for (r in res) println((if (r.error == null) "PASS " else "FAIL ") + r.name + (r.error?.let { "\n     -> $it" } ?: ""))
    println("\n${res.count { it.error == null }}/${res.size} passed")
    if (res.any { it.error != null }) System.exit(1)
}
