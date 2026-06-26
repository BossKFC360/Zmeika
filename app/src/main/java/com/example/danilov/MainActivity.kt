package com.example.danilov

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF4CAF50),
                    secondary = Color(0xFF8BC34A),
                    background = Color(0xFF0A0A1A),
                    surface = Color(0xFF1A1A2E)
                )
            ) {
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

class LeaderboardRepository(private val dao: ScoreDao) {
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A3E), Color(0xFF0A0A1A)),
                    radius = 800f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🐍", fontSize = 72.sp, modifier = Modifier.shadow(20.dp, RoundedCornerShape(50)))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ЗМЕЙКА",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4CAF50),
                letterSpacing = 4.sp,
                modifier = Modifier.shadow(10.dp, RoundedCornerShape(10))
            )
            Text("Классическая игра", fontSize = 14.sp, color = Color(0xFF8BC34A), letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(40.dp))

            MenuButton("▶ Новая игра", onNewGame, Color(0xFF4CAF50))
            Spacer(modifier = Modifier.height(10.dp))
            MenuButton("🏆 Таблица рекордов", onLeaderboard, Color(0xFF2196F3))
            Spacer(modifier = Modifier.height(10.dp))
            MenuButton("⚙ Настройки", onSettings, Color(0xFFFF9800))
            Spacer(modifier = Modifier.height(10.dp))
            MenuButton("❌ Выход", onExit, Color(0xFFF44336))
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, color: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.9f).height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 3.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚙ Настройки", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👤 Игрок", fontSize = 18.sp, color = Color(0xFF8BC34A), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A1A), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF4CAF50)),
                        decorationBox = { innerTextField ->
                            Box {
                                if (nameInput.isEmpty()) {
                                    Text("Введите имя", color = Color.Gray, fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onPlayerNameChange(nameInput) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Сохранить имя", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯 Сложность: $currentDifficulty", fontSize = 16.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DifficultyButton("Лёгкая", currentDifficulty) { onDifficultyChange("Лёгкая") }
                        DifficultyButton("Средняя", currentDifficulty) { onDifficultyChange("Средняя") }
                        DifficultyButton("Сложная", currentDifficulty) { onDifficultyChange("Сложная") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.5f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text("← Назад", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DifficultyButton(text: String, current: String, onClick: () -> Unit) {
    val isSelected = text == current
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp).width(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF333333),
            contentColor = if (isSelected) Color.White else Color.Gray
        )
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏆 Таблица рекордов", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                Button(
                    onClick = { viewModel.deleteAll() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336).copy(alpha = 0.2f)
                    )
                ) {
                    Text("🗑️", color = Color(0xFFF44336))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            when (val currentState = state) {
                is LeaderboardUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Загрузка...", color = Color.Gray)
                        }
                    }
                }
                is LeaderboardUiState.Success -> {
                    if (currentState.entries.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏆", fontSize = 64.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Пока нет рекордов", fontSize = 20.sp, color = Color.Gray)
                                Text("Сыграйте и станьте первым!", fontSize = 16.sp, color = Color.DarkGray)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text("▶ Играть", color = Color.White, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF333333)
                                    )
                                ) {
                                    Text("← В меню", color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentState.entries) { entry ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1A1A2E)
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = when (currentState.entries.indexOf(entry)) {
                                                    0 -> "🥇"
                                                    1 -> "🥈"
                                                    2 -> "🥉"
                                                    else -> "#${currentState.entries.indexOf(entry) + 1}"
                                                },
                                                fontSize = 24.sp
                                            )
                                            Column {
                                                Text(
                                                    text = entry.playerName,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                                                        .format(entry.date),
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${entry.score}",
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            IconButton(
                                                onClick = { viewModel.deleteById(entry.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("✕", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is LeaderboardUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⚠️", fontSize = 48.sp)
                            Text(currentState.message, fontSize = 18.sp, color = Color.Red, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadLeaderboard() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Повторить")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF333333)
                                )
                            ) {
                                Text("← В меню", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            submitStatus?.let { success ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (success) "✅ Рекорд сохранён!" else "❌ Ошибка сохранения",
                    color = if (success) Color.Green else Color.Red,
                    fontSize = 16.sp
                )
                LaunchedEffect(Unit) {
                    delay(2000)
                    viewModel.resetSubmitStatus()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.5f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text("← В меню", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun GameScreen(speedMs: Int, playerName: String, onBack: () -> Unit) {
    val gridSize = 12
    val cellSize = 28

    var gameOver by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var snakeBody by remember { mutableStateOf(listOf(Pair(6,6), Pair(5,6), Pair(4,6))) }
    var foodX by remember { mutableStateOf(9) }
    var foodY by remember { mutableStateOf(9) }
    var directionX by remember { mutableStateOf(1) }
    var directionY by remember { mutableStateOf(0) }
    var scoreSaved by remember { mutableStateOf(false) }

    var deathParticles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var showDeathEffect by remember { mutableStateOf(false) }

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
            if (!isPaused) {
                delay(speedMs.toLong())

                val head = snakeBody.first()
                val newX = head.first + directionX
                val newY = head.second + directionY

                if (newX < 0 || newX >= gridSize || newY < 0 || newY >= gridSize) {
                    showDeathEffect = true
                    val positions = snakeBody.toList()
                    deathParticles = positions.flatMap { pos ->
                        List(12) {
                            val angle = Math.random() * 2 * Math.PI
                            val speed = 3.0 + Math.random() * 5.0
                            Particle(
                                x = pos.first.toFloat() * cellSize + cellSize / 2f,
                                y = pos.second.toFloat() * cellSize + cellSize / 2f,
                                vx = (Math.cos(angle) * speed).toFloat(),
                                vy = (Math.sin(angle) * speed).toFloat(),
                                color = Color(0xFF4CAF50),
                                life = 1f,
                                size = 3f + (Math.random() * 6).toFloat()
                            )
                        }
                    }
                    // Анимируем частицы 1 секунду
                    var elapsedTime = 0L
                    while (showDeathEffect && elapsedTime < 1000) {
                        delay(16)
                        elapsedTime += 16
                        deathParticles = deathParticles.map { particle ->
                            particle.copy(
                                x = particle.x + particle.vx,
                                y = particle.y + particle.vy,
                                vx = particle.vx * 0.98f,
                                vy = particle.vy * 0.98f,
                                life = particle.life - 0.005f,
                                size = particle.size * 0.997f
                            )
                        }.filter { it.life > 0 }
                    }
                    gameOver = true
                    showDeathEffect = false
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
                    showDeathEffect = true
                    val positions = snakeBody.toList()
                    deathParticles = positions.flatMap { pos ->
                        List(12) {
                            val angle = Math.random() * 2 * Math.PI
                            val speed = 3.0 + Math.random() * 5.0
                            Particle(
                                x = pos.first.toFloat() * cellSize + cellSize / 2f,
                                y = pos.second.toFloat() * cellSize + cellSize / 2f,
                                vx = (Math.cos(angle) * speed).toFloat(),
                                vy = (Math.sin(angle) * speed).toFloat(),
                                color = Color(0xFF4CAF50),
                                life = 1f,
                                size = 3f + (Math.random() * 6).toFloat()
                            )
                        }
                    }
                    var elapsedTime = 0L
                    while (showDeathEffect && elapsedTime < 1000) {
                        delay(16)
                        elapsedTime += 16
                        deathParticles = deathParticles.map { particle ->
                            particle.copy(
                                x = particle.x + particle.vx,
                                y = particle.y + particle.vy,
                                vx = particle.vx * 0.98f,
                                vy = particle.vy * 0.98f,
                                life = particle.life - 0.005f,
                                size = particle.size * 0.997f
                            )
                        }.filter { it.life > 0 }
                    }
                    gameOver = true
                    showDeathEffect = false
                    break
                }

                snakeBody = newBody
            } else {
                delay(100)
            }
        }
    }
    if (gameOver && !scoreSaved && score > 0) {
        LaunchedEffect(Unit) {
            viewModel.saveScore(playerName, score)
            scoreSaved = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐍", fontSize = 28.sp)
                Text(
                    text = "Счет: $score",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
                Row {
                    Button(
                        onClick = { isPaused = !isPaused },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text(
                            if (isPaused) "▶" else "⏸",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF333333)
                        )
                    ) {
                        Text("✕", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .size((cellSize * gridSize).dp)
                    .shadow(20.dp, RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                for (x in 0 until gridSize) {
                    for (y in 0 until gridSize) {
                        val isSnake = snakeBody.contains(Pair(x, y))
                        val isFood = (x == foodX && y == foodY)
                        val isHead = isSnake && snakeBody.first() == Pair(x, y)

                        if (isSnake) {
                            Box(
                                modifier = Modifier
                                    .offset(x = (x * cellSize).dp, y = (y * cellSize).dp)
                                    .size(cellSize.dp)
                                    .background(
                                        color = if (isHead) Color(0xFF66BB6A) else Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(if (isHead) 6.dp else 4.dp)
                                    )
                            ) {
                                if (isHead) {
                                    val isHorizontal = directionX != 0
                                    if (isHorizontal) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(modifier = Modifier.size(5.dp).background(Color.Black, RoundedCornerShape(50)))
                                            Box(modifier = Modifier.size(5.dp).background(Color.Black, RoundedCornerShape(50)))
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(5.dp).background(Color.Black, RoundedCornerShape(50)))
                                            Box(modifier = Modifier.size(5.dp).background(Color.Black, RoundedCornerShape(50)))
                                        }
                                    }
                                }
                            }
                        } else if (isFood) {
                            Box(
                                modifier = Modifier
                                    .offset(x = (x * cellSize).dp, y = (y * cellSize).dp)
                                    .size(cellSize.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🍎", fontSize = (cellSize * 0.7f).sp)
                            }
                        }
                    }
                }

                if (showDeathEffect) {
                    deathParticles.forEach { particle ->
                        Box(
                            modifier = Modifier
                                .offset(x = particle.x.dp, y = particle.y.dp)
                                .size(particle.size.dp)
                                .background(particle.color, RoundedCornerShape(50))
                        )
                    }
                }

                if (isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⏸ ПАУЗА",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 4.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GameButton(
                        onClick = {
                            if (!isPaused && directionY != 1) {
                                directionX = 0
                                directionY = -1
                            }
                        },
                        text = "↑"
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GameButton(
                        onClick = {
                            if (!isPaused && directionX != 1) {
                                directionX = -1
                                directionY = 0
                            }
                        },
                        text = "←"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    GameButton(
                        onClick = {
                            if (!isPaused && directionY != -1) {
                                directionX = 0
                                directionY = 1
                            }
                        },
                        text = "↓"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    GameButton(
                        onClick = {
                            if (!isPaused && directionX != -1) {
                                directionX = 1
                                directionY = 0
                            }
                        },
                        text = "→"
                    )
                }
            }
        }

        if (gameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💀", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "GAME OVER",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF44336),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Счет: $score",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD700)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("В меню", color = Color.White, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val life: Float,
    val size: Float
)

@Composable
fun GameButton(
    onClick: () -> Unit,
    text: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .shadow(12.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2A2A4E),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    ) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

fun finish() {
    android.os.Process.killProcess(android.os.Process.myPid())
}