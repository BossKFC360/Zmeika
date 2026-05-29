package com.example.danilov

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SnakeMenu(onExit = { finish() })
            }
        }
    }
}

@Composable
fun SnakeMenu(onExit: () -> Unit) {
    var currentScreen by remember { mutableStateOf("menu") }
    var difficulty by remember { mutableStateOf("Средняя") }

    when (currentScreen) {
        "menu" -> MenuScreen(
            onNewGame = { currentScreen = "game" },
            onSettings = { currentScreen = "settings" },
            onExit = onExit
        )
        "settings" -> SettingsScreen(
            currentDifficulty = difficulty,
            onDifficultyChange = { difficulty = it },
            onBack = { currentScreen = "menu" }
        )
        "game" -> GameScreen(
            difficulty = difficulty,
            onBack = { currentScreen = "menu" }
        )
    }
}

@Composable
fun MenuScreen(onNewGame: () -> Unit, onSettings: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐍 ЗМЕЙКА", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNewGame) { Text("▶ Новая игра") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSettings) { Text("⚙ Настройки") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onExit) { Text("❌ Выход") }
    }
}

@Composable
fun SettingsScreen(currentDifficulty: String, onDifficultyChange: (String) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Настройки", fontSize = 32.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Text("Сложность: $currentDifficulty")
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(onClick = { onDifficultyChange("Лёгкая") }) { Text("Лёгкая") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onDifficultyChange("Средняя") }) { Text("Средняя") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onDifficultyChange("Сложная") }) { Text("Сложная") }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) { Text("← Назад") }
    }
}

@Composable
fun GameScreen(difficulty: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎮 ИГРА", fontSize = 32.sp)
        Text("Сложность: $difficulty", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Тут будет змейка...")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack) { Text("← В меню") }
    }
}