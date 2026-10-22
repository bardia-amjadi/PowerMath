package com.powermath.viewmodel

import com.powermath.math.ErrorKind
import com.powermath.math.Outcome

enum class Screen { CALCULATOR, GRAPH, WORKSPACE, HISTORY, SETTINGS, TOOLS }

/** Modifier state machine: three independent persistent toggles; their combination selects the key layer. */
data class ModifierState(val shift: Boolean = false, val alt: Boolean = false, val ctrl: Boolean = false) {
    val layerName: String
        get() = when {
            shift && alt && ctrl -> "Ctrl+Shift+Alt · Extended"
            ctrl && alt -> "Ctrl+Alt · Algebra tools"
            shift && ctrl -> "Shift+Ctrl · Workspace extras"
            shift && alt -> "Shift+Alt · Variable functions"
            shift -> "Shift · Functions"
            alt -> "Alt · Variables & symbols"
            ctrl -> "Ctrl · Workspace"
            else -> "Standard"
        }
}

sealed interface ResultState {
    data object None : ResultState
    data class Success(val input: String, val outcome: Outcome) : ResultState
    data class Failure(val input: String, val message: String, val kind: ErrorKind?) : ResultState
}

enum class PickPurpose { EDIT_VAR, DELETE_VAR, EDIT_FN, DELETE_FN, INSERT }
enum class ToolKind(val title: String) {
    EQUATION("Solve equation"), INEQUALITY("Solve inequality"), QUADRATIC("Solve quadratic"),
    SYSTEM("Solve system"), EVALUATE("Evaluate under conditions")
}

sealed interface DialogRequest {
    data class DefineVar(val name: String = "", val expr: String = "", val editing: Boolean = false) : DialogRequest
    data class DefineFn(val name: String = "", val params: String = "x", val body: String = "", val editing: Boolean = false) : DialogRequest
    data class Pick(val purpose: PickPurpose) : DialogRequest
    data class Tool(val tool: ToolKind, val prefill: String = "") : DialogRequest
    data object ConfirmClearHistory : DialogRequest
}
