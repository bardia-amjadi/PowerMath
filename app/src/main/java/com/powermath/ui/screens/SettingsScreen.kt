package com.powermath.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermath.data.ThemePref
import com.powermath.math.AngleMode
import com.powermath.math.ResultMode
import com.powermath.ui.components.Chip
import com.powermath.ui.components.ScreenHeader
import com.powermath.ui.components.Segmented
import com.powermath.viewmodel.CalcViewModel
import com.powermath.viewmodel.Screen

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Chip("−", { if (value > range.first) onChange(value - 1) }, desc = "Decrease $label")
        Text("$value", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Chip("+", { if (value < range.last) onChange(value + 1) }, desc = "Increase $label")
    }
}

@Composable
fun SettingsScreen(vm: CalcViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = { vm.screen = Screen.CALCULATOR })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Section("Result format") {
                Segmented(listOf(ResultMode.EXACT to "Exact / Fraction", ResultMode.DECIMAL to "Decimal", ResultMode.SCIENTIFIC to "Scientific"),
                    s.mode, { m -> vm.updateSettings { it.copy(mode = m) } }, Modifier.fillMaxWidth())
            }
            Section("Decimal places") {
                Stepper(s.decimals, 0..50, "decimal places") { n -> vm.updateSettings { it.copy(decimals = n) } }
                Text("Used in Decimal mode. Trailing zeros are removed.", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Section("Significant digits") {
                Stepper(s.sigDigits, 1..50, "significant digits") { n -> vm.updateSettings { it.copy(sigDigits = n) } }
                Text("Used in Scientific notation mode.", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Section("Angle mode") {
                Segmented(listOf(AngleMode.DEG to "Degrees", AngleMode.RAD to "Radians"), s.angle, { a -> vm.updateSettings { it.copy(angle = a) } }, Modifier.fillMaxWidth())
            }
            Section("Theme") {
                Segmented(ThemePref.values().map { it to it.label }, s.theme, { t -> vm.updateSettings { it.copy(theme = t) } }, Modifier.fillMaxWidth())
            }
            Section("Feedback") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Key haptics", modifier = Modifier.weight(1f))
                    Switch(checked = s.haptics, onCheckedChange = { h -> vm.updateSettings { it.copy(haptics = h) } })
                }
            }
            Section("About") {
                Text("Power Math 1.0 · works fully offline. Exact arithmetic uses arbitrary-precision rationals; " +
                    "irrational values are kept exact where possible (√, π, e) and marked ≈ when approximated.",
                    fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}
