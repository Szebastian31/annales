package com.annales.journal

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.annales.journal.reminder.Reminder
import com.annales.journal.ui.AnnalesApp
import com.annales.journal.ui.AnnalesTheme

class MainActivity : ComponentActivity() {
    // Annales' language (the phone's, or the one picked in Profile), right to left for Arabic and Persian
    override fun attachBaseContext(base: Context) = super.attachBaseContext(I18n.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminder.schedule(this)
        setContent {
            AnnalesTheme { AnnalesApp(viewModel()) }
        }
    }
}
