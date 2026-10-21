package com.powermath.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.editor.Pretty
import com.powermath.math.AngleMode
import com.powermath.math.FormatSettings
import com.powermath.math.ResultMode
import com.powermath.ui.components.Chip
import com.powermath.ui.components.KeyAction
import com.powermath.ui.components.KeyLayouts
import com.powermath.ui.components.Keypad
import com.powermath.ui.components.ModifierRow
import com.powermath.ui.components.Segmented
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.ResultState
import com.powermath.viewmodel.Screen
import kotlinx.coroutines.delay

@Composable
fun CalculatorScreen(vm: CalcViewModel, onCopy: (String) -> Unit, onPaste: () -> String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    // Route key actions: clipboard actions need the UI layer, everything else is handled by the ViewModel.
    val onKey: (KeyAction) -> Unit = { action ->
        when (action) {
            KeyAction.CopyResult -> {
                val r = vm.result
                if (r is ResultState.Success) { onCopy(r.outcome.renderText(settings.format)); vm.toast = "Result copied" }
                else vm.toast = "Nothing to copy yet"
            }
            KeyAction.Paste -> onPaste()?.let { vm.insert(it.trim()) }
            else -> vm.onKey(action)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        val compact = maxHeight < 640.dp
        val keyHeight = ((maxHeight * 0.5f) / 6).coerceIn(40.dp, 64.dp)

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ---- header: title, nav, angle mode ----
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!compact) Text("Power Math", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                Chip(if (settings.angle == AngleMode.DEG) "DEG" else "RAD", { vm.toggleAngle() }, active = true,
                    desc = "Angle mode: ${if (settings.angle == AngleMode.DEG) "degrees" else "radians"}. Tap to switch")
                Chip("Graph", { vm.openGraph(vm.editor.text) })
                Chip("History", { vm.screen = Screen.HISTORY })
                Chip("Workspace", { vm.screen = Screen.WORKSPACE })
                Chip("Settings", { vm.screen = Screen.SETTINGS })
            }
            Segmented(
                listOf(ResultMode.EXACT to "Exact / Fraction", ResultMode.DECIMAL to "Decimal", ResultMode.SCIENTIFIC to "Scientific"),
                settings.mode, { vm.setMode(it) }, Modifier.fillMaxWidth()
            )

            // ---- display ----
            Display(vm, settings.format, onCopy, Modifier.weight(1f).fillMaxWidth())

            // ---- modifiers + keypad ----
            ModifierRow(vm.modifiers, vm::toggleShift, vm::toggleAlt, vm::toggleCtrl)
            val rows = KeyLayouts.layer(vm.modifiers, vm.wsVars.map { it.name }, vm.wsFuncs.map { it.name })
            Keypad(rows, keyHeight, settings.haptics, onKey, Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Display(vm: CalcViewModel, fmt: FormatSettings, onCopy: (String) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        vm.toast?.let { msg ->
            LaunchedEffect(msg) { delay(2500); if (vm.toast == msg) vm.toast = null }
            Text(msg, color = cs.primary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ---- expression editor (scrollable, multiline) ----
        val scroll = rememberScrollState()
        val ed = vm.editor
        LaunchedEffect(ed.text, ed.cursor) { scroll.animateScrollTo(scroll.maxValue) }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.BottomEnd) {
            val text = buildAnnotatedString {
                if (ed.text.isEmpty()) {
                    withStyle(SpanStyle(color = cs.onSurfaceVariant)) { append("Enter an expression") }
                    withStyle(SpanStyle(color = cs.primary)) { append("|") }
                } else {
                    append(ed.text.substring(0, ed.cursor))
                    withStyle(SpanStyle(color = cs.primary, fontWeight = FontWeight.Bold)) { append("|") }
                    append(ed.text.substring(ed.cursor))
                }
            }
            Text(
                text, fontSize = if (ed.text.length > 30) 22.sp else 30.sp, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Expression: ${ed.text}" }
            )
        }

        // ---- formatted preview + live result ----
        if (ed.text.isNotEmpty()) {
            Text(Pretty.format(ed.text), fontSize = 15.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.fillMaxWidth())
            vm.livePreview?.let { Text("= $it", fontSize = 16.sp, color = cs.primary.copy(alpha = 0.75f), textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth()) }
        }

        ResultCard(vm, fmt, onCopy)
    }
}

@Composable
private fun ResultCard(vm: CalcViewModel, fmt: FormatSettings, onCopy: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var showSteps by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).animateContentSize()) {
        when (val r = vm.result) {
            ResultState.None -> {}
            is ResultState.Failure -> {
                Text(r.message, color = cs.error, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Error: ${r.message}" })
            }
            is ResultState.Success -> {
                val lines = remember(r, fmt) { r.outcome.render(fmt) }
                val multi = lines.size > 1
                Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.End) {
                    for (l in lines) {
                        Text(
                            l,
                            fontSize = if (multi) 22.sp else if (l.length > 16) 28.sp else 40.sp,
                            fontWeight = FontWeight.Light, color = cs.onSurface, textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Result: $l" }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onCopy(lines.joinToString("\n")); vm.toast = "Result copied" }) { Text("Copy") }
                    r.outcome.graphExpr?.let { g -> TextButton(onClick = { vm.plotFromResult(g) }) { Text("Graph") } }
                    if (r.outcome.value != null) TextButton(onClick = { vm.onKey(KeyAction.SaveResultAsVar) }) { Text("Save as variable") }
                    if (r.outcome.steps.isNotEmpty()) TextButton(onClick = { showSteps = !showSteps }) { Text(if (showSteps) "Hide steps" else "Steps") }
                }
                AnimatedVisibility(showSteps && r.outcome.steps.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                        r.outcome.steps.forEachIndexed { i, s ->
                            Text("${i + 1}. $s", fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
