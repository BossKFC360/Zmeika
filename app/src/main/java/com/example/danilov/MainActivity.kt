package com.example.danilov

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SnakeMenu()
            }
        }
    }
}

@Composable
fun SnakeMenu() {
    var currentScreen by remember { mutableStateOf("menu") }
    var difficulty by remember { mutableStateOf(200) }

    when (currentScreen) {
        "menu" -> MenuScreen(
            onNewGame = { currentScreen = "game" },
            onSettings = { currentScreen = "settings" },
            onExit = { finish() }
        )
        "settings" -> SettingsScreen(
            currentDifficulty = when(difficulty) {
                300 -> "Лёгкая"
                200 -> "Средняя"
                else -> "Сложная"
            },
            onDifficultyChange = {
                difficulty = when(it) {
                    "Лёгкая" -> 300
                    "Средняя" -> 200
                    else -> 120
                }
            },
            onBack = { currentScreen = "menu" }
        )
        "game" -> GameScreen(
            speedMs = difficulty,
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
fun GameScreen(speedMs: Int, onBack: () -> Unit) {
    val gridSize = 20
    val cellSize = 20

    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var snakeBody by remember { mutableStateOf(listOf(Pair(10,10), Pair(9,10), Pair(8,10))) }
    var foodX by remember { mutableStateOf(15) }
    var foodY by remember { mutableStateOf(15) }
    var directionX by remember { mutableStateOf(1) }
    var directionY by remember { mutableStateOf(0) }

    fun generateNewFood() {
        foodX = (0 until gridSize).random()
        foodY = (0 until gridSize).random()
    }

    LaunchedEffect(Unit) {
        while (!gameOver) {
            delay(speedMs.toLong())

            val head = snakeBody.first()
            val newX = head.first + directionX
            val newY = head.second + directionY

            if (newX < 0 || newX >= gridSize || newY < 0 || newY >= gridSize) {
                gameOver = true
                break
            }

            val ateFood = (newX == foodX && newY == foodY)

            val newBody = if (ateFood) {
                score++
                generateNewFood()
                listOf(Pair(newX, newY)) + snakeBody
            } else {
                listOf(Pair(newX, newY)) + snakeBody.dropLast(1)
            }

            if (newBody.drop(1).contains(Pair(newX, newY))) {
                gameOver = true
                break
            }

            snakeBody = newBody
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐍 Счет: $score", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size((cellSize * gridSize).dp)
                .background(Color(0xFF1a1a2e))
        ) {
            for (x in 0 until gridSize) {
                for (y in 0 until gridSize) {
                    val isSnake = snakeBody.contains(Pair(x, y))
                    val isFood = (x == foodX && y == foodY)

                    if (isSnake) {
                        Box(
                            modifier = Modifier
                                .offset(x = (x * cellSize).dp, y = (y * cellSize).dp)
                                .size(cellSize.dp)
                                .background(Color(0xFF4CAF50))
                        )
                    } else if (isFood) {
                        Box(
                            modifier = Modifier
                                .offset(x = (x * cellSize).dp, y = (y * cellSize).dp)
                                .size(cellSize.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍎", fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = {
                    if (directionY != 1) {
                        directionX = 0
                        directionY = -1
                    }
                },
                modifier = Modifier.size(60.dp)
            ) { Text("↑", fontSize = 24.sp) }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Button(
                    onClick = {
                        if (directionX != 1) {
                            directionX = -1
                            directionY = 0
                        }
                    },
                    modifier = Modifier.size(60.dp)
                ) { Text("←", fontSize = 24.sp) }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (directionX != -1) {
                            directionX = 1
                            directionY = 0
                        }
                    },
                    modifier = Modifier.size(60.dp)
                ) { Text("→", fontSize = 24.sp) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (directionY != -1) {
                        directionX = 0
                        directionY = 1
                    }
                },
                modifier = Modifier.size(60.dp)
            ) { Text("↓", fontSize = 24.sp) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("← В меню") }
    }

    if (gameOver) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(modifier = Modifier.padding(32.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💀 GAME OVER 💀", fontSize = 24.sp)
                    Text("Счет: $score", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("В меню") }
                }
            }
        }
    }
}

fun finish() {
    android.os.Process.killProcess(android.os.Process.myPid())
}