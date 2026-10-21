package com.powermath.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.powermath.data.AppSettings
import com.powermath.data.FunctionEntity
import com.powermath.data.HistoryEntity
import com.powermath.data.PowerMathDatabase
import com.powermath.data.SettingsStore
import com.powermath.data.VariableEntity
import com.powermath.editor.Editor
import com.powermath.editor.EditorState
import com.powermath.graph.Annotations
import com.powermath.graph.GraphAnalyzer
import com.powermath.graph.GraphEngine
import com.powermath.graph.GraphFunction
import com.powermath.graph.Viewport
import com.powermath.math.AngleMode
import com.powermath.math.CalcSettings
import com.powermath.math.Calculator
import com.powermath.math.ErrorKind
import com.powermath.math.Formatter
import com.powermath.math.FuncDef
import com.powermath.math.MathError
import com.powermath.math.Outcome
import com.powermath.math.OutcomeKind
import com.powermath.math.ResultMode
import com.powermath.math.VarDef
import com.powermath.math.Value
import com.powermath.math.Workspace
import com.powermath.math.WsChange
import com.powermath.ui.components.KeyAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GraphFn(
    val id: Int,
    val text: String,
    val label: String,
    val colorIndex: Int,
    val visible: Boolean = true,
    val compiled: GraphFunction? = null,
    val error: String? = null
)

class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val db = PowerMathDatabase.get(app)
    private val settingsStore = SettingsStore(app)
    val workspace = Workspace()
    private val loaded = CompletableDeferred<Unit>()

    val settings: StateFlow<AppSettings> = settingsStore.flow
    val history: StateFlow<List<HistoryEntity>> =
        db.historyDao().recent(500).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- calculator state (kept here so it survives screen changes and rotation) ----
    var screen by mutableStateOf(Screen.CALCULATOR)
    var editor by mutableStateOf(EditorState())
        private set
    var modifiers by mutableStateOf(ModifierState())
        private set
    var result by mutableStateOf<ResultState>(ResultState.None)
        private set
    var livePreview by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var dialog by mutableStateOf<DialogRequest?>(null)
    var toast by mutableStateOf<String?>(null)

    var wsVars by mutableStateOf<List<VarDef>>(emptyList())
        private set
    var wsFuncs by mutableStateOf<List<FuncDef>>(emptyList())
        private set
    /** Incremented whenever workspace contents change (graphs and previews depend on it). */
    var wsVersion by mutableIntStateOf(0)
        private set

    private var justEvaluated = false
    private var calcJob: Job? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val (vars, funcs) = withContext(Dispatchers.IO) { Pair(db.workspaceDao().variables(), db.workspaceDao().functions()) }
            workspace.restore(
                vars.map { Triple(it.name, it.source, it.valueText) },
                funcs.map { Triple(it.name, it.params.split(',').filter { p -> p.isNotBlank() }, it.body) }
            )
            refreshWorkspace()
            loaded.complete(Unit)
        }
    }

    private fun calcSettings(s: AppSettings = settings.value) = CalcSettings(s.angle, s.format)

    private fun refreshWorkspace() {
        wsVars = workspace.variables
        wsFuncs = workspace.functions
        wsVersion++
    }

    // ---------------------------------------------------------------- editor
    private fun setEditor(e: EditorState) {
        editor = e
        schedulePreview()
    }

    fun setExpression(text: String) {
        justEvaluated = false
        setEditor(EditorState.of(text))
    }

    fun insert(text: String) {
        var base = editor
        if (justEvaluated) {
            val first = text.firstOrNull()
            base = if (first != null && first in "+-*/^%") EditorState.of("ans") else Editor.clearAll()
            justEvaluated = false
        }
        setEditor(Editor.insert(base, text))
    }

    fun toggleShift() { modifiers = modifiers.copy(shift = !modifiers.shift) }
    fun toggleAlt() { modifiers = modifiers.copy(alt = !modifiers.alt) }
    fun toggleCtrl() { modifiers = modifiers.copy(ctrl = !modifiers.ctrl) }

    fun onKey(action: KeyAction) {
        when (action) {
            KeyAction.None -> {}
            is KeyAction.Insert -> insert(action.text)
            is KeyAction.Wrap -> {
                if (editor.text.isBlank()) insert(action.prefix)
                else { justEvaluated = false; val t = action.prefix + editor.text.trim() + ")"; setEditor(EditorState.of(t)); evaluate(t) }
            }
            KeyAction.Backspace -> { justEvaluated = false; setEditor(Editor.backspace(editor)) }
            KeyAction.ClearEntry -> { justEvaluated = false; setEditor(Editor.clearEntry(editor)) }
            KeyAction.ClearAll -> { justEvaluated = false; setEditor(Editor.clearAll()); result = ResultState.None }
            KeyAction.Left -> { justEvaluated = false; editor = Editor.left(editor) }
            KeyAction.Right -> { justEvaluated = false; editor = Editor.right(editor) }
            KeyAction.Negate -> { justEvaluated = false; setEditor(Editor.negate(editor)) }
            KeyAction.Equals -> evaluate()
            is KeyAction.Open -> dialog = action.dialog
            is KeyAction.Go -> if (action.screen == Screen.GRAPH) openGraph(editor.text) else screen = action.screen
            KeyAction.SaveResultAsVar -> {
                val v = (result as? ResultState.Success)?.outcome?.value
                if (v == null) toast = "Evaluate a numeric expression first"
                else dialog = DialogRequest.DefineVar(expr = if (v is Value.D) Formatter.linear(v) else Formatter.exact(v))
            }
            KeyAction.SaveExprAsFn ->
                if (editor.text.isBlank()) toast = "Type an expression such as x^2 + 1 first"
                else dialog = DialogRequest.DefineFn(body = editor.text)
            KeyAction.ClearVars -> clearVariables()
            KeyAction.ClearFns -> clearFunctions()
            KeyAction.ClearHistory -> dialog = DialogRequest.ConfirmClearHistory
            KeyAction.CopyResult, KeyAction.Paste -> {} // handled in the UI (needs the clipboard)
        }
    }

    // ---------------------------------------------------------------- evaluation
    fun evaluate(override: String? = null) {
        val input = (override ?: editor.text).trim()
        if (input.isEmpty()) return
        calcJob?.cancel()
        calcJob = viewModelScope.launch {
            loaded.await()
            busy = true
            val s = settings.value
            val outcome: Result<Outcome> = withContext(Dispatchers.Default) {
                try { Result.success(Calculator.process(input, workspace, calcSettings(s))) }
                catch (e: MathError) { Result.failure(e) }
                catch (e: StackOverflowError) { Result.failure(MathError(ErrorKind.LIMIT, "The expression is too complex")) }
                catch (e: Exception) { Result.failure(MathError(ErrorKind.UNSUPPORTED, "Unexpected problem: ${e.message}")) }
            }
            busy = false
            outcome.fold(
                onSuccess = { out ->
                    result = ResultState.Success(input, out)
                    justEvaluated = out.kind == OutcomeKind.VALUE
                    if (out.changes.isNotEmpty()) { persist(out.changes); refreshWorkspace(); refreshGraphs() }
                    saveHistory(input, out, s)
                },
                onFailure = { e ->
                    val me = e as? MathError
                    result = ResultState.Failure(input, me?.message ?: "Error", me?.kind)
                    justEvaluated = false
                }
            )
        }
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        val text = editor.text
        previewJob = viewModelScope.launch {
            delay(120)
            loaded.await()
            livePreview = if (text.isBlank()) null
            else withContext(Dispatchers.Default) { try { Calculator.preview(text, workspace, calcSettings()) } catch (e: Exception) { null } }
        }
    }

    /** Re-render preview when settings (mode/angle) change. */
    fun onSettingsChanged() { schedulePreview(); refreshGraphs() }

    // ---------------------------------------------------------------- history
    private fun saveHistory(input: String, out: Outcome, s: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            db.historyDao().insert(HistoryEntity(expression = input, result = out.renderText(s.format), mode = s.mode.name,
                timestamp = System.currentTimeMillis(), kind = out.kind.name))
            db.historyDao().trim(1000)
        }
    }

    fun restoreFromHistory(e: HistoryEntity) { setExpression(e.expression); screen = Screen.CALCULATOR }
    fun deleteHistory(id: Long) { viewModelScope.launch(Dispatchers.IO) { db.historyDao().delete(id) } }
    fun clearHistory() { viewModelScope.launch(Dispatchers.IO) { db.historyDao().clear() } }

    // ---------------------------------------------------------------- workspace
    private fun persist(changes: List<WsChange>) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = db.workspaceDao()
            val now = System.currentTimeMillis()
            for (c in changes) when (c) {
                is WsChange.VarSet -> dao.upsertVariable(VariableEntity(c.def.name, c.def.source, Formatter.linear(c.def.value), now))
                is WsChange.VarDelete -> dao.deleteVariable(c.name)
                is WsChange.FnSet -> dao.upsertFunction(FunctionEntity(c.def.name, c.def.params.joinToString(","), c.def.source, now))
                is WsChange.FnDelete -> dao.deleteFunction(c.name)
            }
        }
    }

    private inline fun guarded(block: () -> List<WsChange>): String? = try {
        persist(block()); refreshWorkspace(); refreshGraphs(); schedulePreview(); null
    } catch (e: MathError) { e.message }

    fun defineVariable(name: String, source: String, editing: Boolean): String? =
        guarded { listOf(workspace.defineVariable(name.trim(), source, overwrite = editing, angle = settings.value.angle)) }

    fun defineFunction(name: String, params: String, body: String, editing: Boolean): String? {
        val ps = params.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return guarded { listOf(workspace.defineFunction(name.trim(), ps, body, overwrite = editing)) }
    }

    fun deleteVariable(name: String): String? = guarded { listOf(workspace.deleteVariable(name)) }
    fun deleteFunction(name: String): String? = guarded { listOf(workspace.deleteFunction(name)) }
    fun clearVariables() { toast = guarded { workspace.clearVariables() } ?: "All variables deleted" }
    fun clearFunctions() { toast = guarded { workspace.clearFunctions() } ?: "All functions deleted" }

    // ---------------------------------------------------------------- graph
    var graphFns by mutableStateOf<List<GraphFn>>(emptyList())
        private set
    var graphAnnotations by mutableStateOf<Map<Int, Annotations>>(emptyMap())
        private set
    var viewport by mutableStateOf(Viewport.DEFAULT)
    var graphInput by mutableStateOf("")
    var graphError by mutableStateOf<String?>(null)
    var showAnnotations by mutableStateOf(true)
    private var nextGraphId = 1

    fun openGraph(prefill: String? = null) {
        screen = Screen.GRAPH
        if (!prefill.isNullOrBlank()) graphInput = prefill.trim()
    }

    /** Adds the text in the graph input (or [text]) as a plotted function. Returns true on success. */
    fun plot(text: String = graphInput): Boolean {
        val src = text.trim()
        if (src.isEmpty()) { graphError = "Enter a function, e.g. f(x) = x^2 - 4"; return false }
        return try {
            val fn = GraphFunction.compile(src, workspace, settings.value.angle)
            val g = GraphFn(nextGraphId, src, fn.label, (nextGraphId - 1) % 6, true, fn)
            nextGraphId++
            graphFns = graphFns + g
            graphError = null
            graphInput = ""
            recomputeAnnotations(autoFit = true)
            true
        } catch (e: MathError) { graphError = e.message; false }
    }

    fun plotSaved(f: FuncDef) { plot("${f.name}(${f.params.firstOrNull() ?: "x"})") }

    fun plotFromResult(expr: String) { screen = Screen.GRAPH; plot("y = $expr") }

    fun removeGraph(id: Int) { graphFns = graphFns.filter { it.id != id }; recomputeAnnotations(autoFit = false) }
    fun toggleGraph(id: Int) { graphFns = graphFns.map { if (it.id == id) it.copy(visible = !it.visible) else it } }
    fun clearGraphs() { graphFns = emptyList(); graphAnnotations = emptyMap(); viewport = Viewport.DEFAULT }

    /** Recompile all plotted functions (angle mode or workspace changed). */
    private fun refreshGraphs() {
        if (graphFns.isEmpty()) return
        graphFns = graphFns.map { g ->
            try { g.copy(compiled = GraphFunction.compile(g.text, workspace, settings.value.angle), error = null) }
            catch (e: MathError) { g.copy(compiled = null, error = e.message) }
        }
        recomputeAnnotations(autoFit = false)
    }

    fun recomputeAnnotations(autoFit: Boolean) {
        val fns = graphFns
        val angle = settings.value.angle
        viewModelScope.launch {
            val ann = withContext(Dispatchers.Default) {
                fns.mapNotNull { g -> g.compiled?.let { c -> g.id to GraphAnalyzer.analyze(c, workspace, angle) } }.toMap()
            }
            graphAnnotations = ann
            if (autoFit) fit()
        }
    }

    fun fit() {
        val fns = graphFns.filter { it.visible && it.compiled != null }
        if (fns.isEmpty()) { viewport = Viewport.DEFAULT; return }
        val interesting = fns.flatMap { graphAnnotations[it.id]?.all ?: emptyList() }
        viewport = GraphEngine.autoViewport(fns.map { f -> { x: Double -> f.compiled!!.eval(x) } }, interesting)
    }

    // ---------------------------------------------------------------- settings
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        onSettingsChanged()
    }

    fun setMode(mode: ResultMode) = updateSettings { it.copy(mode = mode) }
    fun toggleAngle() = updateSettings { it.copy(angle = if (it.angle == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG) }
}
