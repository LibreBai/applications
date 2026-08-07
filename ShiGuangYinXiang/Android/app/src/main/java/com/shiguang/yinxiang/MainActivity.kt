package com.shiguang.yinxiang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shiguang.yinxiang.ui.MainScreen
import com.shiguang.yinxiang.ui.theme.ShiGuangTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShiGuangTheme {
                MainScreen()
            }
        }
    }
}
