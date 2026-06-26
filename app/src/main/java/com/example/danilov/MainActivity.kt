package com.example.danilov

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val playerName: String,
    val score: Int,
    val date: Long = System.currentTimeMillis()
)

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity)

    @Query("SELECT * FROM scores ORDER BY score DESC")
    suspend fun getAll(): List<ScoreEntity>

    @Query("DELETE FROM scores")
    suspend fun deleteAll()

    @Query("DELETE FROM scores WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Database(entities = [ScoreEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scoreDao(): ScoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snake_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LeaderboardRepository(
    private val dao: ScoreDao
) {
    suspend fun saveScore(playerName: String, score: Int) {
        dao.insert(ScoreEntity(playerName = playerName, score = score))
    }

    suspend fun getLeaderboard(): List<ScoreEntity> {
        return dao.getAll()
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun deleteById(id: Int) {
        dao.deleteById(id)
    }
}

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(val entries: List<ScoreEntity>) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}

class LeaderboardViewModel(
    private val repository: LeaderboardRepository
) : ViewModel() {
    private val _state = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val state: StateFlow<LeaderboardUiState> = _state

    private val _submitStatus = MutableStateFlow<Boolean?>(null)
    val submitStatus: StateFlow<Boolean?> = _submitStatus

    fun loadLeaderboard() {
        viewModelScope.launch {
            _state.value = LeaderboardUiState.Loading
            try {
                val entries = repository.getLeaderboard()
                _state.value = if (entries.isEmpty()) {
                    LeaderboardUiState.Error("Нет рекордов")
                } else {
                    LeaderboardUiState.Success(entries)
                }
            } catch (e: Exception) {
                _state.value = LeaderboardUiState.Error("Ошибка загрузки: ${e.message}")
            }
        }
    }

    fun saveScore(playerName: String, score: Int) {
        viewModelScope.launch {
            try {
                repository.saveScore(playerName, score)
                _submitStatus.value = true
                loadLeaderboard()
            } catch (e: Exception) {
                _submitStatus.value = false
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            try {
                repository.deleteAll()
                loadLeaderboard()
            } catch (e: Exception) {
            }
        }
    }

    fun deleteById(id: Int) {
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                loadLeaderboard()
            } catch (e: Exception) {
            }
        }
    }

    fun resetSubmitStatus() {
        _submitStatus.value = null
    }
}

@Composable
fun SnakeMenu() {
    var currentScreen by remember { mutableStateOf("menu") }
    var difficulty by remember { mutableStateOf(200) }
    var playerName by remember { mutableStateOf("Игрок") }

    when (currentScreen) {
        "menu" -> MenuScreen(
            onNewGame = { currentScreen = "game" },
            onLeaderboard = { currentScreen = "leaderboard" },
            onSettings = { currentScreen = "settings" },
            onExit = { finish() }
        )
        "settings" -> SettingsScreen(
            currentDifficulty = when(difficulty) {
                300 -> "Лёгкая"
                200 -> "Средняя"
                else -> "Сложная"
            },
            playerName = playerName,
            onPlayerNameChange = { playerName = it },
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
            playerName = playerName,
            onBack = { currentScreen = "menu" }
        )
        "leaderboard" -> LeaderboardScreen(
            onBack = { currentScreen = "menu" }
        )
    }
}

@Composable
fun MenuScreen(
    onNewGame: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🐍 ЗМЕЙКА", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNewGame) { Text("▶ Новая игра") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLeaderboard) { Text("🏆 Таблица лидеров") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSettings) { Text("⚙ Настройки") }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onExit) { Text("❌ Выход") }
    }
}

@Composable
fun SettingsScreen(
    currentDifficulty: String,
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    onDifficultyChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var nameInput by remember { mutableStateOf(playerName) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Настройки", fontSize = 32.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Text("Игрок:")
        Spacer(modifier = Modifier.height(8.dp))
        BasicTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(Color.White, shape = MaterialTheme.shapes.small)
                .padding(12.dp),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (nameInput.isEmpty()) {
                        Text("Введите имя", color = Color.Gray)
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onPlayerNameChange(nameInput) }) {
            Text("Сохранить имя")
        }

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
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val repository = remember { LeaderboardRepository(database.scoreDao()) }
    val viewModel: LeaderboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LeaderboardViewModel(repository) as T
            }
        }
    )

    val state by viewModel.state.collectAsState()
    val submitStatus by viewModel.submitStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLeaderboard()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Text("🏆 Таблица лидеров", fontSize = 32.sp)
            Button(onClick = { viewModel.deleteAll() }) {
                Text("🗑️ Очистить")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (val currentState = state) {
            is LeaderboardUiState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Загрузка...")
            }
            is LeaderboardUiState.Success -> {
                if (currentState.entries.isEmpty()) {
                    Text("Пока нет рекордов", fontSize = 18.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        items(currentState.entries) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "#${currentState.entries.indexOf(entry) + 1}",
                                        fontSize = 18.sp,
                                        color = if (currentState.entries.indexOf(entry) == 0)
                                            Color(0xFFFFD700) else Color.Unspecified
                                    )
                                    Text(entry.playerName, fontSize = 18.sp)
                                    Text("${entry.score} очков", fontSize = 18.sp)
                                    Button(onClick = { viewModel.deleteById(entry.id) }) {
                                        Text("✕", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is LeaderboardUiState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ ${currentState.message}", fontSize = 18.sp, color = Color.Red)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadLeaderboard() }) {
                        Text("Повторить")
                    }
                }
            }
        }

        submitStatus?.let { success ->
            Spacer(modifier = Modifier.height(16.dp))
            if (success) {
                Text("✅ Рекорд сохранён!", color = Color.Green)
            } else {
                Text("❌ Ошибка сохранения", color = Color.Red)
            }
            LaunchedEffect(Unit) {
                delay(2000)
                viewModel.resetSubmitStatus()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text("← В меню") }
    }
}

@Composable
fun GameScreen(speedMs: Int, playerName: String, onBack: () -> Unit) {
    val gridSize = 12
    val cellSize = 28

    var gameOver by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var snakeBody by remember { mutableStateOf(listOf(Pair(6,6), Pair(5,6), Pair(4,6))) }
    var foodX by remember { mutableStateOf(9) }
    var foodY by remember { mutableStateOf(9) }
    var directionX by remember { mutableStateOf(1) }
    var directionY by remember { mutableStateOf(0) }
    var scoreSaved by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val repository = remember { LeaderboardRepository(database.scoreDao()) }
    val viewModel: LeaderboardViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LeaderboardViewModel(repository) as T
            }
        }
    )

    fun generateNewFood() {
        do {
            foodX = (0 until gridSize).random()
            foodY = (0 until gridSize).random()
        } while (snakeBody.contains(Pair(foodX, foodY)))
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

    if (gameOver && !scoreSaved && score > 0) {
        LaunchedEffect(Unit) {
            viewModel.saveScore(playerName, score)
            scoreSaved = true
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
                            Text("🍎", fontSize = (cellSize * 0.6f).sp)
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
                modifier = Modifier.size(50.dp)
            ) { Text("↑", fontSize = 20.sp) }

            Spacer(modifier = Modifier.height(6.dp))

            Row {
                Button(
                    onClick = {
                        if (directionX != 1) {
                            directionX = -1
                            directionY = 0
                        }
                    },
                    modifier = Modifier.size(50.dp)
                ) { Text("←", fontSize = 20.sp) }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (directionX != -1) {
                            directionX = 1
                            directionY = 0
                        }
                    },
                    modifier = Modifier.size(50.dp)
                ) { Text("→", fontSize = 20.sp) }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (directionY != -1) {
                        directionX = 0
                        directionY = 1
                    }
                },
                modifier = Modifier.size(50.dp)
            ) { Text("↓", fontSize = 20.sp) }
        }

        Spacer(modifier = Modifier.height(12.dp))
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