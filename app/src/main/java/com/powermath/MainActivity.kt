package com.powermath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.powermath.ui.PowerMathApp
import com.powermath.viewmodel.CalcViewModel

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PowerMathApp(vm) }
    }
}
