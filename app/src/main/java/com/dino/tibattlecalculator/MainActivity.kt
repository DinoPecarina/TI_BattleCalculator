// MainActivity.kt
package com.dino.tibattlecalculator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(
                onScanClick = {
                    val intent = Intent(this@MainActivity, ScanActivity::class.java)
                    startActivity(intent)
                }
            )
        }
    }
}
