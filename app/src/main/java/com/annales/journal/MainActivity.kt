package com.annales.journal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.annales.journal.reminder.Reminder
import com.annales.journal.ui.AnnalesApp
import com.annales.journal.ui.AnnalesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminder.schedule(this)
        setContent {
            AnnalesTheme { AnnalesApp(viewModel()) }
        }
    }
}
