package com.powermath.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.ui.components.DialogHost
import com.powermath.ui.components.KeyAction
import com.powermath.ui.screens.CalculatorScreen
import com.powermath.ui.screens.GraphScreen
import com.powermath.ui.screens.HistoryScreen
import com.powermath.ui.screens.SettingsScreen
import com.powermath.ui.screens.WorkspaceScreen
import com.powermath.ui.theme.PowerMathTheme
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen

/** Hardware keyboard support for the calculator screen. */
private fun handlePhysicalKey(e: KeyEvent, vm: CalcViewModel): Boolean {
    if (e.type != KeyEventType.KeyDown || vm.dialog != null) return false
    when (e.key) {
        Key.Enter, Key.NumPadEnter -> { vm.onKey(KeyAction.Equals); return true }
        Key.Backspace -> { vm.onKey(KeyAction.Backspace); return true }
        Key.Delete -> { vm.onKey(KeyAction.ClearEntry); return true }
        Key.Escape -> { vm.onKey(KeyAction.ClearAll); return true }
        Key.DirectionLeft -> { vm.onKey(KeyAction.Left); return true }
        Key.DirectionRight -> { vm.onKey(KeyAction.Right); return true }
    }
    if (e.isCtrlPressed || e.isAltPressed) return false
    val cp = e.utf16CodePoint
    if (cp in 32..126) { vm.insert(cp.toChar().toString()); return true }
    return false
}

@Composable
fun PowerMathApp(vm: CalcViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showSplash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    PowerMathTheme(settings.theme, settings.accent) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(vm.screen, vm.dialog) {
            if (vm.screen == Screen.CALCULATOR && vm.dialog == null) runCatching { focus.requestFocus() }
        }
        BackHandler(enabled = vm.screen != Screen.CALCULATOR) { vm.screen = Screen.CALCULATOR }

        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                if (targetState == Screen.CALCULATOR) (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 6 } + fadeOut())
                else (slideInHorizontally { it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
            },
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars),
            label = "screen"
        ) { screen ->
            when (screen) {
                Screen.CALCULATOR -> Box(
                    Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent { handlePhysicalKey(it, vm) }
                ) {
                    CalculatorScreen(
                        vm,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                        onPaste = { clipboard.getText()?.text }
                    )
                }
                Screen.GRAPH -> GraphScreen(vm)
                Screen.WORKSPACE -> WorkspaceScreen(vm)
                Screen.HISTORY -> HistoryScreen(vm) { clipboard.setText(AnnotatedString(it)) }
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
        DialogHost(vm)
        vm.lastCrash?.let { trace ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vm.lastCrash = null },
                title = { androidx.compose.material3.Text("The app closed unexpectedly") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text("Sorry about that. Copy the details and send them to the developer so it can be fixed.",
                            fontSize = 13.sp)
                        androidx.compose.material3.Text(trace.take(1500),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 8.dp).heightIn(max = 240.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()))
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { clipboard.setText(AnnotatedString(trace)); vm.lastCrash = null }) { androidx.compose.material3.Text("Copy and close") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { vm.lastCrash = null }) { androidx.compose.material3.Text("Close") } }
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(androidx.compose.animation.core.tween(700))
        ) { SplashScreen { showSplash = false } }
    }
}
