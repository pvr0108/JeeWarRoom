package com.example.jeewarroom

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class Subject { PHYSICS, CHEMISTRY, MATHS }
enum class AppTheme(val label: String) { SYSTEM("System Default"), LIGHT("Light Mode"), DARK("Dark Mode") }
enum class Status(val color: Color) { RED(Color(0xFFEF5350)), YELLOW(Color(0xFFFFC107)), GREEN(Color(0xFF66BB6A)) }

data class Chapter(
    val id: Int,
    val name: String,
    val subject: Subject,
    var status: Status,
    var noteUri: String? = null,
    var order: Int = 0
)

data class StudyRecord(
    val id: Long = System.currentTimeMillis(),
    val date: String,
    val time: String,
    val durationSeconds: Int,
    val isBreak: Boolean,
    val mode: String
)

object DataRepository {
    private const val PREFS_NAME = "JeeWarRoomPrefs"
    private const val KEY_DATA = "chapter_data_json"
    private const val KEY_TERMS = "terms_accepted_v1"
    private const val KEY_THEME = "app_theme_pref"
    private const val KEY_HISTORY = "study_history_json"

    private val gson = Gson()
    var chapters = mutableStateListOf<Chapter>()
    var studyHistory = mutableStateListOf<StudyRecord>()
    var currentTheme = mutableStateOf(AppTheme.SYSTEM)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTheme = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        currentTheme.value = runCatching { AppTheme.valueOf(savedTheme) }.getOrDefault(AppTheme.SYSTEM)

        prefs.getString(KEY_DATA, null)?.let {
            try {
                chapters.clear()
                chapters.addAll(gson.fromJson(it, object : TypeToken<List<Chapter>>() {}.type))
            } catch (e: Exception) {
                loadDefaults(context)
            }
        } ?: loadDefaults(context)

        prefs.getString(KEY_HISTORY, null)?.let {
            try {
                studyHistory.clear()
                studyHistory.addAll(gson.fromJson(it, object : TypeToken<List<StudyRecord>>() {}.type))
            } catch (e: Exception) {}
        }
    }

    private fun saveData(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DATA, gson.toJson(chapters)).apply()
    }

    fun addRecord(context: Context, durationSeconds: Int, isBreak: Boolean, mode: String) {
        if (durationSeconds <= 0) return
        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        studyHistory.add(0, StudyRecord(date = dateStr, time = timeStr, durationSeconds = durationSeconds, isBreak = isBreak, mode = mode))

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, gson.toJson(studyHistory)).apply()
    }

    fun clearHistory(context: Context) {
        studyHistory.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
        Toast.makeText(context, "History Cleared", Toast.LENGTH_SHORT).show()
    }

    fun setTheme(context: Context, theme: AppTheme) {
        currentTheme.value = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_THEME, theme.name).apply()
    }

    fun isTermsAccepted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TERMS, false)
    }

    fun acceptTerms(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TERMS, true).apply()
    }

    fun addChapter(context: Context, name: String, subject: Subject) {
        val maxId = chapters.maxOfOrNull { it.id } ?: 1000
        val maxOrder = chapters.filter { it.subject == subject }.maxOfOrNull { it.order } ?: -1
        chapters.add(Chapter(maxId + 1, name, subject, Status.RED, null, maxOrder + 1))
        saveData(context)
        Toast.makeText(context, "Chapter Added", Toast.LENGTH_SHORT).show()
    }

    fun deleteChapter(context: Context, chapter: Chapter) {
        chapters.remove(chapter)
        saveData(context)
        Toast.makeText(context, "Chapter Deleted", Toast.LENGTH_SHORT).show()
    }

    fun getChaptersBySubject(subject: Subject): List<Chapter> {
        return chapters.filter { it.subject == subject }.sortedBy { it.order }
    }

    fun updateChapterStatus(context: Context, chapter: Chapter, newStatus: Status) {
        val index = chapters.indexOfFirst { it.id == chapter.id }
        if (index != -1) {
            chapters[index] = chapters[index].copy(status = newStatus)
            saveData(context)
        }
    }

    fun updateChapterNote(context: Context, chapter: Chapter, uri: Uri?) {
        val index = chapters.indexOfFirst { it.id == chapter.id }
        if (index != -1) {
            chapters[index] = chapters[index].copy(noteUri = uri?.toString())
            saveData(context)
            if (uri != null) Toast.makeText(context, "Note Attached!", Toast.LENGTH_SHORT).show()
        }
    }

    fun openNote(context: Context, uriString: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriString), "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open Note"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    fun getStatusCounts(subject: Subject): Map<Status, Int> {
        val sub = chapters.filter { it.subject == subject }
        return mapOf(
            Status.RED to sub.count { it.status == Status.RED },
            Status.YELLOW to sub.count { it.status == Status.YELLOW },
            Status.GREEN to sub.count { it.status == Status.GREEN }
        )
    }

    private fun loadDefaults(context: Context) {
        chapters.clear()
        chapters.addAll(listOf(
            Chapter(1, "Units & Dimensions", Subject.PHYSICS, Status.GREEN, null, 0),
            Chapter(2, "Kinematics 1D", Subject.PHYSICS, Status.GREEN, null, 1),
            Chapter(3, "Kinematics 2D", Subject.PHYSICS, Status.YELLOW, null, 2),
            Chapter(4, "Newton's Laws", Subject.PHYSICS, Status.RED, null, 3),
            Chapter(5, "Friction", Subject.PHYSICS, Status.RED, null, 4),
            Chapter(101, "Mole Concept", Subject.CHEMISTRY, Status.GREEN, null, 0),
            Chapter(102, "Atomic Structure", Subject.CHEMISTRY, Status.YELLOW, null, 1),
            Chapter(201, "Sets & Relations", Subject.MATHS, Status.GREEN, null, 0),
            Chapter(202, "Functions", Subject.MATHS, Status.YELLOW, null, 1)
        ))
        saveData(context)
    }
}

// ─────────────────────────────────────────────
// GLOBAL TIMER ENGINE (Survives Minimizing & Navigation)
// ─────────────────────────────────────────────
object TimerManager {
    var isStopwatchMode by mutableStateOf(false)
    var studyMinutes by mutableIntStateOf(25)
    var breakMinutes by mutableIntStateOf(5)
    var autoStartBreaks by mutableStateOf(false)

    // NEW: Tracks the total time allocated to the current phase so math is always perfect
    var currentPhaseTotalSeconds by mutableIntStateOf(25 * 60)
    var timeLeftSeconds by mutableIntStateOf(25 * 60)
    var stopwatchSeconds by mutableIntStateOf(0)
    var isTimerRunning by mutableStateOf(false)
    var isStudyPhase by mutableStateOf(true)
    var showPhaseCompleteDialog by mutableStateOf(false)

    private var timerJob: Job? = null

    fun toggleTimer(context: Context) {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            isTimerRunning = true
            startTimer(context)
        }
    }

    fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
    }

    private fun startTimer(context: Context) {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            var lastTickTime = System.currentTimeMillis()
            while (isTimerRunning) {
                delay(200L) // Check 5 times a second for precise catch-up
                val now = System.currentTimeMillis()
                val deltaMs = now - lastTickTime

                // If 1 full second has passed (or fast-forward if app was minimized)
                if (deltaMs >= 1000L) {
                    val deltaSeconds = (deltaMs / 1000L).toInt()
                    lastTickTime += deltaSeconds * 1000L // Maintain absolute precision

                    if (isStopwatchMode) {
                        if (isStudyPhase) {
                            val milestone = studyMinutes * 60
                            val prev = stopwatchSeconds
                            stopwatchSeconds += deltaSeconds

                            // Milestone Nudge Logic
                            if (milestone > 0 && prev / milestone < stopwatchSeconds / milestone) {
                                showPhaseCompleteDialog = true
                            }
                        } else {
                            val prev = timeLeftSeconds
                            timeLeftSeconds -= deltaSeconds
                            if (prev > 0 && timeLeftSeconds <= 0 && !showPhaseCompleteDialog) {
                                showPhaseCompleteDialog = true
                            }
                        }
                    } else {
                        val prev = timeLeftSeconds
                        timeLeftSeconds -= deltaSeconds
                        if (prev > 0 && timeLeftSeconds <= 0) {
                            if (autoStartBreaks) {
                                // Mathematical perfection: Total allocated time minus whatever the clock says
                                val actualTime = currentPhaseTotalSeconds - timeLeftSeconds
                                DataRepository.addRecord(context, actualTime, !isStudyPhase, "Timer")

                                isStudyPhase = !isStudyPhase
                                currentPhaseTotalSeconds = if (isStudyPhase) studyMinutes * 60 else breakMinutes * 60
                                timeLeftSeconds = currentPhaseTotalSeconds
                            } else {
                                showPhaseCompleteDialog = true
                            }
                        }
                    }
                }
            }
        }
    }

    fun skipOrRecord(context: Context) {
        if (!isStopwatchMode) {
            // Mathematical perfection: Total allocated time minus whatever the clock says
            val actualTime = currentPhaseTotalSeconds - timeLeftSeconds
            if (actualTime > 0) {
                DataRepository.addRecord(context, actualTime, !isStudyPhase, "Timer")
            }
        }

        if (isStopwatchMode) {
            if (isStudyPhase) {
                isStudyPhase = false
                currentPhaseTotalSeconds = breakMinutes * 60
                timeLeftSeconds = currentPhaseTotalSeconds
            } else {
                isStudyPhase = true
            }
        } else {
            isStudyPhase = !isStudyPhase
            currentPhaseTotalSeconds = if (isStudyPhase) studyMinutes * 60 else breakMinutes * 60
            timeLeftSeconds = currentPhaseTotalSeconds
        }
        pauseTimer()
    }

    fun resetTimer() {
        pauseTimer()
        if (isStopwatchMode) {
            stopwatchSeconds = 0
            isStudyPhase = true
        } else {
            isStudyPhase = true
            currentPhaseTotalSeconds = studyMinutes * 60
            timeLeftSeconds = currentPhaseTotalSeconds
        }
    }
}

class MainActivity : ComponentActivity() {
    private companion object { const val KEY_PENDING_CHAPTER_ID = "pending_chapter_id" }
    private var currentChapterForNote: Chapter? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {}

            currentChapterForNote?.let { chapter ->
                DataRepository.updateChapterNote(this, chapter, it)
            }
        }
        currentChapterForNote = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentChapterForNote?.let { outState.putInt(KEY_PENDING_CHAPTER_ID, it.id) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataRepository.initialize(this)

        savedInstanceState?.let { bundle ->
            val pendingId = bundle.getInt(KEY_PENDING_CHAPTER_ID, -1)
            if (pendingId != -1) {
                currentChapterForNote = DataRepository.chapters.firstOrNull { it.id == pendingId }
            }
        }

        setContent {
            val isDark = when (DataRepository.currentTheme.value) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showTermsDialog by remember { mutableStateOf(!DataRepository.isTermsAccepted(this)) }
                    var selectedSubject by rememberSaveable { mutableStateOf<Subject?>(null) }
                    var showPomodoroScreen by rememberSaveable { mutableStateOf(false) }
                    var showHistoryScreen by rememberSaveable { mutableStateOf(false) }

                    when {
                        showTermsDialog -> TermsDialog(onAccept = {
                            DataRepository.acceptTerms(this)
                            showTermsDialog = false
                        })
                        showPomodoroScreen -> PomodoroScreen(onBackClick = { showPomodoroScreen = false })
                        showHistoryScreen -> HistoryScreen(onBackClick = { showHistoryScreen = false })
                        selectedSubject != null -> SubjectDetailScreen(
                            subject = selectedSubject!!,
                            onBackClick = { selectedSubject = null },
                            onAttachNote = { chapter ->
                                currentChapterForNote = chapter
                                filePickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                        else -> MainDashboard(
                            onSubjectClick = { selectedSubject = it },
                            onPomodoroClick = { showPomodoroScreen = true },
                            onHistoryClick = { showHistoryScreen = true }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (DataRepository.studyHistory.isNotEmpty()) {
                        IconButton(onClick = { DataRepository.clearHistory(context) }) {
                            Icon(Icons.Default.DeleteSweep, "Clear History", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (DataRepository.studyHistory.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("No sessions recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(DataRepository.studyHistory) { record ->
                    val color = if (record.isBreak) Status.GREEN.color else MaterialTheme.colorScheme.primary
                    val hours = record.durationSeconds / 3600
                    val minutes = (record.durationSeconds % 3600) / 60
                    val seconds = record.durationSeconds % 60

                    val durationStr = buildString {
                        if (hours > 0) append("${hours}h ")
                        if (minutes > 0 || hours > 0) append("${minutes}m ")
                        append("${seconds}s")
                    }.trim()

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).background(color, CircleShape))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (record.isBreak) "Break Time" else "Focus Session", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                                Spacer(Modifier.height(4.dp))
                                Text(record.time, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(durationStr, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(4.dp))
                                Text(record.date, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var showStudyInfo by remember { mutableStateOf(false) }
    var showBreakInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("War Room Timer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Mode Slider
            Row(
                modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (!TimerManager.isStopwatchMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (TimerManager.isStopwatchMode) {
                                TimerManager.isStopwatchMode = false
                                TimerManager.pauseTimer()
                            }
                        }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Timer", fontWeight = FontWeight.Bold, color = if (!TimerManager.isStopwatchMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (TimerManager.isStopwatchMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (!TimerManager.isStopwatchMode) {
                                TimerManager.isStopwatchMode = true
                                TimerManager.pauseTimer()
                                TimerManager.isStudyPhase = true
                            }
                        }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Stopwatch", fontWeight = FontWeight.Bold, color = if (TimerManager.isStopwatchMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(visible = !TimerManager.isStopwatchMode || !TimerManager.isStudyPhase) {
                Surface(
                    color = if (TimerManager.isStudyPhase) MaterialTheme.colorScheme.primaryContainer else Status.GREEN.color.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        if (TimerManager.isStudyPhase) "🔥 FOCUS MODE" else "☕ BREAK TIME",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (TimerManager.isStudyPhase) MaterialTheme.colorScheme.onPrimaryContainer else Status.GREEN.color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) Spacer(Modifier.height(48.dp))

            // Clock Display
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val progressColor = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) MaterialTheme.colorScheme.primary else {
                    if (TimerManager.isStudyPhase) MaterialTheme.colorScheme.primary else Status.GREEN.color
                }

                // Calculates arc based on currentPhaseTotalSeconds so +5 mins seamlessly adjusts!
                val targetSweepAngle = if (TimerManager.isStopwatchMode) {
                    if (TimerManager.isStudyPhase) {
                        (TimerManager.stopwatchSeconds % 60) / 60f * 360f
                    } else {
                        val t = TimerManager.currentPhaseTotalSeconds
                        if (t > 0) (max(TimerManager.timeLeftSeconds, 0).toFloat() / t) * 360f else 0f
                    }
                } else {
                    val t = TimerManager.currentPhaseTotalSeconds
                    if (t > 0) (max(TimerManager.timeLeftSeconds, 0).toFloat() / t) * 360f else 0f
                }

                val animatedSweepAngle by animateFloatAsState(targetValue = targetSweepAngle, animationSpec = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) tween(0) else tween(1000, easing = LinearEasing), label = "sweep")

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 14.dp.toPx()
                    drawCircle(trackColor, style = Stroke(strokeW))
                    drawArc(color = progressColor, startAngle = -90f, sweepAngle = animatedSweepAngle, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round))
                }

                val isNegative = (!TimerManager.isStopwatchMode && TimerManager.timeLeftSeconds < 0) || (TimerManager.isStopwatchMode && !TimerManager.isStudyPhase && TimerManager.timeLeftSeconds < 0)
                val displaySeconds = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) TimerManager.stopwatchSeconds else TimerManager.timeLeftSeconds
                val absSeconds = abs(displaySeconds)
                val h = absSeconds / 3600
                val m = (absSeconds % 3600) / 60
                val s = absSeconds % 60
                val sign = if (isNegative) "-" else ""

                val timeString = if (h > 0) String.format("%s%02d:%02d:%02d", sign, h, m, s) else String.format("%s%02d:%02d", sign, m, s)

                Text(
                    text = timeString,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = if (h > 0) 56.sp else 72.sp, fontWeight = FontWeight.Black),
                    color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(48.dp))

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { TimerManager.resetTimer() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Refresh, "Reset")
                }

                val fabContainerColor by animateColorAsState(targetValue = if (TimerManager.isTimerRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary, animationSpec = tween(400), label="c")
                val fabContentColor by animateColorAsState(targetValue = if (TimerManager.isTimerRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary, animationSpec = tween(400), label="c2")

                FloatingActionButton(onClick = { TimerManager.toggleTimer(context) }, modifier = Modifier.size(72.dp), containerColor = fabContainerColor, contentColor = fabContentColor) {
                    AnimatedContent(targetState = TimerManager.isTimerRunning, transitionSpec = { (scaleIn(tween(300)) + fadeIn(tween(300))).togetherWith(scaleOut(tween(300)) + fadeOut(tween(300))) }, label = "play") { running ->
                        Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, if (running) "Pause" else "Start", modifier = Modifier.size(36.dp))
                    }
                }

                FilledTonalIconButton(
                    onClick = { TimerManager.skipOrRecord(context) },
                    modifier = Modifier.size(56.dp)
                ) {
                    val baseTime = if (TimerManager.isStudyPhase) TimerManager.studyMinutes * 60 else TimerManager.breakMinutes * 60
                    val isSessionActive = TimerManager.isTimerRunning || TimerManager.timeLeftSeconds != baseTime

                    val icon = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) {
                        Icons.Default.FreeBreakfast
                    } else if (!TimerManager.isStopwatchMode && isSessionActive) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.SkipNext
                    }

                    Icon(icon, "Break / Skip / Stop")
                }
            }
            Spacer(Modifier.height(32.dp))

            // Settings
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(24.dp))
                Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(visible = !TimerManager.isStopwatchMode) {
                    Row(
                        modifier = Modifier.padding(bottom = 16.dp).clip(RoundedCornerShape(8.dp)).clickable { TimerManager.autoStartBreaks = !TimerManager.autoStartBreaks }.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = TimerManager.autoStartBreaks, onCheckedChange = { TimerManager.autoStartBreaks = it })
                        Text("Auto-start next phase without asking", fontSize = 14.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (TimerManager.isStopwatchMode) "Study Milestone" else "Study Duration", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (TimerManager.isStopwatchMode) {
                        Box {
                            IconButton(onClick = { showStudyInfo = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                Icon(Icons.Default.Info, "Info", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showStudyInfo, onDismissRequest = { showStudyInfo = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                                Text("In Stopwatch mode, this sets a milestone to gently remind you to take a break without stopping your flow state.", modifier = Modifier.width(220.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 25, 50, 90).forEach { min ->
                        FilterChip(
                            selected = TimerManager.studyMinutes == min,
                            onClick = {
                                TimerManager.studyMinutes = min
                                if (!TimerManager.isTimerRunning && TimerManager.isStudyPhase && !TimerManager.isStopwatchMode) {
                                    TimerManager.currentPhaseTotalSeconds = min * 60
                                    TimerManager.timeLeftSeconds = TimerManager.currentPhaseTotalSeconds
                                }
                            },
                            label = { Text("${min}m") },
                            enabled = !TimerManager.isTimerRunning
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Break Duration", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (TimerManager.isStopwatchMode) {
                        Box {
                            IconButton(onClick = { showBreakInfo = true }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                                Icon(Icons.Default.Info, "Info", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showBreakInfo, onDismissRequest = { showBreakInfo = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                                Text("If you accept a break suggestion, the stopwatch pauses and a temporary break countdown takes over.", modifier = Modifier.width(220.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5, 10, 15).forEach { min ->
                        FilterChip(
                            selected = TimerManager.breakMinutes == min,
                            onClick = {
                                TimerManager.breakMinutes = min
                                if (!TimerManager.isTimerRunning && !TimerManager.isStudyPhase) {
                                    TimerManager.currentPhaseTotalSeconds = min * 60
                                    TimerManager.timeLeftSeconds = TimerManager.currentPhaseTotalSeconds
                                }
                            },
                            label = { Text("${min}m") },
                            enabled = !TimerManager.isTimerRunning
                        )
                    }
                }
            }
        }
    }

    if (TimerManager.showPhaseCompleteDialog) {
        val title = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) "Milestone Reached!" else if (TimerManager.isStudyPhase) "Focus Session Complete!" else "Break Over!"
        val text = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) "You've been in the zone for ${TimerManager.studyMinutes} minutes! Want to keep your flow state, or take a quick break?" else if (TimerManager.isStudyPhase) "Great job hitting your target! You can start your break now, or add 5 more minutes if you're in the zone." else if (TimerManager.isStopwatchMode) "Time to get back to the War Room! Your stopwatch is waiting." else "Time to get back to the War Room! Ready to start focusing again?"

        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(text) },
            confirmButton = {
                Button(onClick = {
                    if (!TimerManager.isStopwatchMode) {
                        val actualTime = TimerManager.currentPhaseTotalSeconds - TimerManager.timeLeftSeconds
                        DataRepository.addRecord(context, actualTime, !TimerManager.isStudyPhase, "Timer")
                    }
                    TimerManager.showPhaseCompleteDialog = false

                    if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) {
                        TimerManager.isStudyPhase = false
                        TimerManager.currentPhaseTotalSeconds = TimerManager.breakMinutes * 60
                        TimerManager.timeLeftSeconds = TimerManager.currentPhaseTotalSeconds
                    } else if (TimerManager.isStopwatchMode && !TimerManager.isStudyPhase) {
                        TimerManager.isStudyPhase = true
                    } else {
                        TimerManager.isStudyPhase = !TimerManager.isStudyPhase
                        TimerManager.currentPhaseTotalSeconds = if (TimerManager.isStudyPhase) TimerManager.studyMinutes * 60 else TimerManager.breakMinutes * 60
                        TimerManager.timeLeftSeconds = TimerManager.currentPhaseTotalSeconds
                    }
                }) {
                    Text(if (TimerManager.isStudyPhase && TimerManager.isStopwatchMode) "Take Break" else if (TimerManager.isStudyPhase) "Start Break" else "Resume Focus")
                }
            },
            dismissButton = {
                if (TimerManager.isStudyPhase && !TimerManager.isStopwatchMode) {
                    OutlinedButton(onClick = {
                        TimerManager.showPhaseCompleteDialog = false
                        TimerManager.currentPhaseTotalSeconds += 5 * 60
                        TimerManager.timeLeftSeconds += 5 * 60
                    }) {
                        Text("+5 Mins Focus")
                    }
                } else if (TimerManager.isStudyPhase && TimerManager.isStopwatchMode) {
                    OutlinedButton(onClick = {
                        TimerManager.showPhaseCompleteDialog = false
                    }) {
                        Text("Keep Grinding")
                    }
                } else {
                    OutlinedButton(onClick = {
                        if (!TimerManager.isStopwatchMode) {
                            val actualTime = TimerManager.currentPhaseTotalSeconds - TimerManager.timeLeftSeconds
                            DataRepository.addRecord(context, actualTime, !TimerManager.isStudyPhase, "Timer")
                        }
                        TimerManager.showPhaseCompleteDialog = false
                        TimerManager.isStudyPhase = true
                        TimerManager.currentPhaseTotalSeconds = TimerManager.studyMinutes * 60
                        TimerManager.timeLeftSeconds = TimerManager.currentPhaseTotalSeconds
                        TimerManager.pauseTimer()
                    }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(onSubjectClick: (Subject) -> Unit, onPomodoroClick: () -> Unit, onHistoryClick: () -> Unit) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(32.dp))
                Text("Menu", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
                Spacer(Modifier.height(16.dp))

                NavigationDrawerItem(icon = { Icon(Icons.Default.Palette, "Theme") }, label = { Text("App Theme") }, selected = false, onClick = { scope.launch { drawerState.close() }; showThemeDialog = true }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(icon = { Icon(Icons.Default.History, "History") }, label = { Text("Study History") }, selected = false, onClick = { scope.launch { drawerState.close() }; onHistoryClick() }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
                NavigationDrawerItem(icon = { Icon(Icons.Default.Email, "Help") }, label = { Text("Help & Support") }, selected = false, onClick = { scope.launch { drawerState.close() }; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf("t.preethivardhanreddy@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "JEE War Room - Support") }, "Send Email")) }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding))
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text("JEE War Room", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    actions = {
                        IconButton(onClick = onHistoryClick) { Icon(Icons.Default.History, "History") }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Help & Support") }, onClick = { showMenu = false; context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf("t.preethivardhanreddy@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "JEE War Room - Support") }, "Send Email")) }, leadingIcon = { Icon(Icons.Default.Email, null) })
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                val fabText = if (TimerManager.isTimerRunning) {
                    val sec = if (TimerManager.isStopwatchMode && TimerManager.isStudyPhase) TimerManager.stopwatchSeconds else abs(TimerManager.timeLeftSeconds)
                    val sign = if (!TimerManager.isStopwatchMode && TimerManager.timeLeftSeconds < 0) "-" else ""
                    String.format("Timer: %s%02d:%02d", sign, (sec % 3600) / 60, sec % 60)
                } else {
                    "War Room Timer"
                }

                ExtendedFloatingActionButton(
                    onClick = onPomodoroClick,
                    icon = { Icon(Icons.Default.Timer, "Timer") },
                    text = { Text(fabText, fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                SubjectCard(Subject.PHYSICS, "P", onClick = { onSubjectClick(Subject.PHYSICS) })
                Spacer(Modifier.height(16.dp))
                SubjectCard(Subject.CHEMISTRY, "C", onClick = { onSubjectClick(Subject.CHEMISTRY) })
                Spacer(Modifier.height(16.dp))
                SubjectCard(Subject.MATHS, "M", onClick = { onSubjectClick(Subject.MATHS) })
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    if (showThemeDialog) ThemeDialog(currentTheme = DataRepository.currentTheme.value, onThemeSelected = { theme -> DataRepository.setTheme(context, theme) }, onDismiss = { showThemeDialog = false })
}

@Composable
fun ThemeDialog(currentTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Choose Theme", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                AppTheme.values().forEach { theme ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onThemeSelected(theme) }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == currentTheme, onClick = { onThemeSelected(theme) })
                        Spacer(Modifier.width(12.dp))
                        Text(theme.label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCard(subject: Subject, letter: String, onClick: () -> Unit) {
    val counts = DataRepository.getStatusCounts(subject)
    val (weak, review, mastered) = listOf(counts[Status.RED] ?: 0, counts[Status.YELLOW] ?: 0, counts[Status.GREEN] ?: 0)

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgress(weak, review, mastered)
                Text(letter, fontSize = 42.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(24.dp))
            Column {
                Text(subject.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("$weak Weak • $review Review", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$mastered Mastered", fontSize = 14.sp, color = Status.GREEN.color, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CircularProgress(weak: Int, review: Int, mastered: Int) {
    val total = weak + review + mastered
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Canvas(Modifier.size(100.dp)) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        if (total == 0) drawCircle(trackColor, radius, style = Stroke(strokeWidth, cap = StrokeCap.Round)) else {
            var startAngle = -90f
            if (mastered > 0) {
                val ang = (mastered.toFloat() / total) * 360f
                drawArc(Status.GREEN.color, startAngle, ang, false, style = Stroke(strokeWidth, cap = StrokeCap.Round), topLeft = Offset(strokeWidth / 2, strokeWidth / 2), size = Size(size.width - strokeWidth, size.height - strokeWidth))
                startAngle += ang
            }
            if (review > 0) {
                val ang = (review.toFloat() / total) * 360f
                drawArc(Status.YELLOW.color, startAngle, ang, false, style = Stroke(strokeWidth, cap = StrokeCap.Round), topLeft = Offset(strokeWidth / 2, strokeWidth / 2), size = Size(size.width - strokeWidth, size.height - strokeWidth))
                startAngle += ang
            }
            if (weak > 0) {
                drawArc(Status.RED.color, startAngle, (weak.toFloat() / total) * 360f, false, style = Stroke(strokeWidth, cap = StrokeCap.Round), topLeft = Offset(strokeWidth / 2, strokeWidth / 2), size = Size(size.width - strokeWidth, size.height - strokeWidth))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(subject: Subject, onBackClick: () -> Unit, onAttachNote: (Chapter) -> Unit) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var filterRed by remember { mutableStateOf(true) }
    var filterYellow by remember { mutableStateOf(true) }
    var filterGreen by remember { mutableStateOf(true) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    BackHandler { onBackClick() }

    val filteredChapters = DataRepository.getChaptersBySubject(subject).filter {
        when (it.status) { Status.RED -> filterRed; Status.YELLOW -> filterYellow; Status.GREEN -> filterGreen }
    }.let { list ->
        if (sortAscending) list.sortedBy { it.name } else list.sortedByDescending { it.name }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(subject.name, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Ascending") }, onClick = { sortAscending = true; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Descending") }, onClick = { sortAscending = false; showSortMenu = false })
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("Add Chapter") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterRed, onClick = { filterRed = !filterRed }, label = { Text("Weak") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Status.RED.color.copy(alpha = 0.2f), selectedLabelColor = Status.RED.color))
                FilterChip(selected = filterYellow, onClick = { filterYellow = !filterYellow }, label = { Text("Review") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Status.YELLOW.color.copy(alpha = 0.2f), selectedLabelColor = Color(0xFFE6A800)))
                FilterChip(selected = filterGreen, onClick = { filterGreen = !filterGreen }, label = { Text("Mastered") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Status.GREEN.color.copy(alpha = 0.2f), selectedLabelColor = Status.GREEN.color))
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(filteredChapters) { chapter ->
                    ChapterCard(chapter = chapter, onAttachNote = onAttachNote, onLongPress = { chapterToDelete = it })
                }
            }
        }
    }

    if (showAddDialog) AddDialog(subject) { showAddDialog = false }
    if (chapterToDelete != null) {
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Delete Chapter?") },
            text = { Text("Are you sure you want to delete '${chapterToDelete?.name}' from your war room?") },
            confirmButton = { TextButton(onClick = { DataRepository.deleteChapter(context, chapterToDelete!!); chapterToDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { chapterToDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterCard(chapter: Chapter, onAttachNote: (Chapter) -> Unit, onLongPress: (Chapter) -> Unit) {
    val context = LocalContext.current
    val actionButton = when (chapter.status) { Status.RED -> "Mark for Review"; Status.YELLOW -> "Mark Completed"; Status.GREEN -> "✨ Chill" }
    val hasNote = chapter.noteUri != null
    val noteIcon = if (hasNote) Icons.Default.Description else Icons.Default.Add
    val noteTint = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).combinedClickable(onClick = {}, onLongClick = { onLongPress(chapter) }),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).background(chapter.status.color, CircleShape))
            Spacer(Modifier.width(16.dp))
            Text(text = chapter.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))

            IconButton(onClick = { if (hasNote) DataRepository.openNote(context, chapter.noteUri!!) else onAttachNote(chapter) }, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.iconButtonColors(contentColor = noteTint)) {
                Icon(noteIcon, if (hasNote) "Open Note" else "Attach Note")
            }
            IconButton(onClick = { val prev = when (chapter.status) { Status.GREEN -> Status.YELLOW; Status.YELLOW -> Status.RED; Status.RED -> Status.RED }; if (prev != chapter.status) DataRepository.updateChapterStatus(context, chapter, prev) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(8.dp))
            if (chapter.status == Status.GREEN) {
                Text(text = actionButton, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Status.GREEN.color, modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                FilledTonalButton(onClick = { when (chapter.status) { Status.RED -> DataRepository.updateChapterStatus(context, chapter, Status.YELLOW); Status.YELLOW -> DataRepository.updateChapterStatus(context, chapter, Status.GREEN); Status.GREEN -> { } } }, modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(actionButton, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AddDialog(subject: Subject, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Chapter") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Chapter Name") }, singleLine = true, shape = MaterialTheme.shapes.medium) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { DataRepository.addChapter(context, name.trim(), subject); onDismiss() } }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TermsDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Welcome to JEE War Room", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column {
                Text("\"Success is the sum of small efforts repeated day in and day out.\"", fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("This app tracks your JEE grind. Every chapter you complete, every concept you master - it all counts. No shortcuts, just consistent progress.", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("By continuing, you agree to:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("• Put in the work, not just track it", fontSize = 13.sp)
                Text("• Stay consistent with your prep", fontSize = 13.sp)
                Text("• Take responsibility for your progress", fontSize = 13.sp)
            }
        },
        confirmButton = { Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("Let's get this rank. 💪") } }
    )
}