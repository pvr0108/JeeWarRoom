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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// Data classes and enums
enum class Subject { PHYSICS, CHEMISTRY, MATHS }

enum class AppTheme(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class Status(val color: Color) {
    RED(Color(0xFFEF5350)),
    YELLOW(Color(0xFFFFC107)),
    GREEN(Color(0xFF66BB6A))
}

data class Chapter(
    val id: Int,
    val name: String,
    val subject: Subject,
    var status: Status,
    var noteUri: String? = null,
    var order: Int = 0
)

// Data Repository
object DataRepository {
    private const val PREFS_NAME = "JeeWarRoomPrefs"
    private const val KEY_DATA = "chapter_data_json"
    private const val KEY_TERMS = "terms_accepted_v1"
    private const val KEY_THEME = "app_theme_pref"

    private val gson = Gson()
    var chapters = mutableStateListOf<Chapter>()

    var currentTheme = mutableStateOf(AppTheme.SYSTEM)

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val savedTheme = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        currentTheme.value = runCatching { AppTheme.valueOf(savedTheme) }.getOrDefault(AppTheme.SYSTEM)

        val jsonString = prefs.getString(KEY_DATA, null)
        if (jsonString != null) {
            try {
                val type = object : TypeToken<List<Chapter>>() {}.type
                val savedList: List<Chapter> = gson.fromJson(jsonString, type)
                chapters.clear()
                chapters.addAll(savedList)
            } catch (e: Exception) {
                loadDefaults(context)
            }
        } else {
            loadDefaults(context)
        }
    }

    private fun saveData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = gson.toJson(chapters)
        prefs.edit().putString(KEY_DATA, jsonString).apply()
    }

    fun setTheme(context: Context, theme: AppTheme) {
        currentTheme.value = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    fun isTermsAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TERMS, false)
    }

    fun acceptTerms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TERMS, true).apply()
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
        val subjectChapters = chapters.filter { it.subject == subject }
        return mapOf(
            Status.RED to subjectChapters.count { it.status == Status.RED },
            Status.YELLOW to subjectChapters.count { it.status == Status.YELLOW },
            Status.GREEN to subjectChapters.count { it.status == Status.GREEN }
        )
    }

    private fun loadDefaults(context: Context) {
        chapters.clear()
        chapters.addAll(
            listOf(
                Chapter(1, "Units & Dimensions", Subject.PHYSICS, Status.GREEN, order = 0),
                Chapter(2, "Kinematics 1D", Subject.PHYSICS, Status.GREEN, order = 1),
                Chapter(3, "Kinematics 2D", Subject.PHYSICS, Status.YELLOW, order = 2),
                Chapter(4, "Newton's Laws", Subject.PHYSICS, Status.RED, order = 3),
                Chapter(5, "Friction", Subject.PHYSICS, Status.RED, order = 4),
                Chapter(6, "Work Power Energy", Subject.PHYSICS, Status.YELLOW, order = 5),
                Chapter(7, "Rotational Motion", Subject.PHYSICS, Status.RED, order = 6),
                Chapter(101, "Mole Concept", Subject.CHEMISTRY, Status.GREEN, order = 0),
                Chapter(102, "Atomic Structure", Subject.CHEMISTRY, Status.YELLOW, order = 1),
                Chapter(103, "Chemical Bonding", Subject.CHEMISTRY, Status.RED, order = 2),
                Chapter(104, "Thermodynamics", Subject.CHEMISTRY, Status.RED, order = 3),
                Chapter(201, "Sets & Relations", Subject.MATHS, Status.GREEN, order = 0),
                Chapter(202, "Functions", Subject.MATHS, Status.YELLOW, order = 1),
                Chapter(203, "Trigonometry", Subject.MATHS, Status.RED, order = 2)
            )
        )
        saveData(context)
    }
}

// ─────────────────────────────────────────────
// Main Activity
// ─────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private companion object {
        const val KEY_PENDING_CHAPTER_ID = "pending_chapter_id"
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
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

    private var currentChapterForNote: Chapter? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentChapterForNote?.let {
            outState.putInt(KEY_PENDING_CHAPTER_ID, it.id)
        }
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

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showTermsDialog by remember { mutableStateOf(!DataRepository.isTermsAccepted(this)) }

                    var selectedSubject by rememberSaveable { mutableStateOf<Subject?>(null) }
                    var showPomodoroScreen by rememberSaveable { mutableStateOf(false) }

                    if (showTermsDialog) {
                        TermsDialog(onAccept = {
                            DataRepository.acceptTerms(this)
                            showTermsDialog = false
                        })
                    } else if (showPomodoroScreen) {
                        PomodoroScreen(onBackClick = { showPomodoroScreen = false })
                    } else if (selectedSubject != null) {
                        SubjectDetailScreen(
                            subject = selectedSubject!!,
                            onBackClick = { selectedSubject = null },
                            onAttachNote = { chapter ->
                                currentChapterForNote = chapter
                                filePickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    } else {
                        MainDashboard(
                            onSubjectClick = { selectedSubject = it },
                            onPomodoroClick = { showPomodoroScreen = true }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Pomodoro Timer & Stopwatch Screen
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBackClick() }

    // Bonus: Keep the screen awake while the timer is visible
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Mode Toggle
    var isStopwatchMode by rememberSaveable { mutableStateOf(false) }

    // Timer Settings
    var studyMinutes by rememberSaveable { mutableIntStateOf(25) }
    var breakMinutes by rememberSaveable { mutableIntStateOf(5) }

    // Core Engine States
    var timeLeftSeconds by rememberSaveable { mutableIntStateOf(studyMinutes * 60) }
    var stopwatchSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isTimerRunning by rememberSaveable { mutableStateOf(false) }
    var isStudyPhase by rememberSaveable { mutableStateOf(true) }

    // Dialog State
    var showPhaseCompleteDialog by rememberSaveable { mutableStateOf(false) }

    // Timer / Stopwatch Coroutine
    LaunchedEffect(isTimerRunning, isStopwatchMode) {
        while (isTimerRunning) {
            delay(1000L)
            if (isStopwatchMode) {
                stopwatchSeconds += 1
            } else {
                timeLeftSeconds -= 1 // It can safely go negative now

                // Trigger dialog exactly when it hits 0
                if (timeLeftSeconds == 0) {
                    showPhaseCompleteDialog = true
                }
            }
        }
    }

    fun resetTimer() {
        isTimerRunning = false
        if (isStopwatchMode) {
            stopwatchSeconds = 0
        } else {
            isStudyPhase = true
            timeLeftSeconds = studyMinutes * 60
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("War Room Timer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Mode Slider (Timer vs Stopwatch)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                // Timer Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (!isStopwatchMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (isStopwatchMode) {
                                isStopwatchMode = false
                                isTimerRunning = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Timer",
                        fontWeight = FontWeight.Bold,
                        color = if (!isStopwatchMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Stopwatch Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (isStopwatchMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            if (!isStopwatchMode) {
                                isStopwatchMode = true
                                isTimerRunning = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Stopwatch",
                        fontWeight = FontWeight.Bold,
                        color = if (isStopwatchMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Phase Indicator (Only for Timer Mode)
            AnimatedVisibility(visible = !isStopwatchMode) {
                Surface(
                    color = if (isStudyPhase) MaterialTheme.colorScheme.primaryContainer else Status.GREEN.color.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text = if (isStudyPhase) "🔥 FOCUS MODE" else "☕ BREAK TIME",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = if (isStudyPhase) MaterialTheme.colorScheme.onPrimaryContainer else Status.GREEN.color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isStopwatchMode) Spacer(Modifier.height(48.dp))

            // The Circular Timer Display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                // Circular Progress Ring
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                val progressColor = if (isStopwatchMode) MaterialTheme.colorScheme.primary else {
                    if (isStudyPhase) MaterialTheme.colorScheme.primary else Status.GREEN.color
                }

                // Sweep calculation (Capped at 0 for negative times so it looks empty)
                val targetSweepAngle = if (isStopwatchMode) {
                    (stopwatchSeconds % 60) / 60f * 360f
                } else {
                    val totalTimerSeconds = if (isStudyPhase) studyMinutes * 60 else breakMinutes * 60
                    if (totalTimerSeconds > 0) {
                        (max(timeLeftSeconds, 0).toFloat() / totalTimerSeconds) * 360f
                    } else 0f
                }

                val animatedSweepAngle by animateFloatAsState(
                    targetValue = targetSweepAngle,
                    animationSpec = if (isStopwatchMode) tween(0) else tween(1000, easing = LinearEasing),
                    label = "sweep_angle"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 14.dp.toPx()
                    drawCircle(trackColor, style = Stroke(strokeW))
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = animatedSweepAngle,
                        useCenter = false,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }

                // The Time Text
                val isNegative = !isStopwatchMode && timeLeftSeconds < 0
                val absSeconds = abs(if (isStopwatchMode) stopwatchSeconds else timeLeftSeconds)
                val hours = absSeconds / 3600
                val minutes = (absSeconds % 3600) / 60
                val seconds = absSeconds % 60

                val sign = if (isNegative) "-" else ""
                val timeString = if (hours > 0) {
                    String.format("%s%02d:%02d:%02d", sign, hours, minutes, seconds)
                } else {
                    String.format("%s%02d:%02d", sign, minutes, seconds)
                }

                Text(
                    text = timeString,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = if (hours > 0) 56.sp else 72.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(48.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                FilledTonalIconButton(
                    onClick = { resetTimer() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Reset")
                }

                val fabContainerColor by animateColorAsState(
                    targetValue = if (isTimerRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                    animationSpec = tween(400)
                )
                val fabContentColor by animateColorAsState(
                    targetValue = if (isTimerRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                    animationSpec = tween(400)
                )

                // Play / Pause
                FloatingActionButton(
                    onClick = { isTimerRunning = !isTimerRunning },
                    modifier = Modifier.size(72.dp),
                    containerColor = fabContainerColor,
                    contentColor = fabContentColor
                ) {
                    AnimatedContent(
                        targetState = isTimerRunning,
                        transitionSpec = {
                            (scaleIn(tween(300)) + fadeIn(tween(300))).togetherWith(scaleOut(tween(300)) + fadeOut(tween(300)))
                        },
                        label = "play_pause_animation"
                    ) { running ->
                        Icon(
                            imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (running) "Pause" else "Start",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Skip Next Button (Symmetrical to Reset button)
                if (!isStopwatchMode) {
                    FilledTonalIconButton(
                        onClick = {
                            isStudyPhase = !isStudyPhase
                            timeLeftSeconds = if (isStudyPhase) studyMinutes * 60 else breakMinutes * 60
                            isTimerRunning = false
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, "Skip Phase")
                    }
                } else {
                    // Invisible spacer to keep the Play button perfectly centered in stopwatch mode
                    Spacer(Modifier.size(56.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Settings Section
            AnimatedVisibility(
                visible = !isStopwatchMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(24.dp))

                    Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))

                    Text("Study Duration", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 50, 90).forEach { min ->
                            FilterChip(
                                selected = studyMinutes == min,
                                onClick = {
                                    studyMinutes = min
                                    if (!isTimerRunning && isStudyPhase) timeLeftSeconds = min * 60
                                },
                                label = { Text("${min}m") },
                                enabled = !isTimerRunning
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text("Break Duration", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { min ->
                            FilterChip(
                                selected = breakMinutes == min,
                                onClick = {
                                    breakMinutes = min
                                    if (!isTimerRunning && !isStudyPhase) timeLeftSeconds = min * 60
                                },
                                label = { Text("${min}m") },
                                enabled = !isTimerRunning
                            )
                        }
                    }
                }
            }
        }
    }

    // Phase Completion Dialog
    if (showPhaseCompleteDialog && !isStopwatchMode) {
        AlertDialog(
            onDismissRequest = { showPhaseCompleteDialog = false },
            title = {
                Text(
                    text = if (isStudyPhase) "Focus Session Complete!" else "Break Over!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isStudyPhase) "Great job hitting your target! You can start your break now, or add 5 more minutes if you're in the zone."
                    else "Time to get back to the War Room! Ready to start focusing again?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPhaseCompleteDialog = false
                        isStudyPhase = !isStudyPhase
                        timeLeftSeconds = if (isStudyPhase) studyMinutes * 60 else breakMinutes * 60
                        // Note: Timer continues running into the new phase!
                    }
                ) {
                    Text(if (isStudyPhase) "Start Break" else "Start Focus")
                }
            },
            dismissButton = {
                if (isStudyPhase) {
                    OutlinedButton(
                        onClick = {
                            showPhaseCompleteDialog = false
                            // Add 5 mins. Since timeLeftSeconds is currently negative (e.g. -45s),
                            // adding 300s makes it 255s (4m 15s). Math is beautiful!
                            timeLeftSeconds += 5 * 60
                        }
                    ) {
                        Text("+5 Mins Focus")
                    }
                } else {
                    OutlinedButton(
                        onClick = { showPhaseCompleteDialog = false }
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Dashboard & Details
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(onSubjectClick: (Subject) -> Unit, onPomodoroClick: () -> Unit) {
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
                Text(
                    "Menu",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
                Spacer(Modifier.height(16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Palette, contentDescription = "Theme") },
                    label = { Text("App Theme") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showThemeDialog = true
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Email, contentDescription = "Help") },
                    label = { Text("Help & Support") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("t.preethivardhanreddy@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "JEE War Room - Support")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text("JEE War Room", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Help & Support") },
                                onClick = {
                                    showMenu = false
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf("t.preethivardhanreddy@gmail.com"))
                                        putExtra(Intent.EXTRA_SUBJECT, "JEE War Room - Support")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                                },
                                leadingIcon = { Icon(Icons.Default.Email, null) }
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onPomodoroClick,
                    icon = { Icon(Icons.Default.Timer, "Timer") },
                    text = { Text("War Room Timer", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = DataRepository.currentTheme.value,
            onThemeSelected = { theme -> DataRepository.setTheme(context, theme) },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun ThemeDialog(currentTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                AppTheme.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == currentTheme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(theme.label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCard(subject: Subject, letter: String, onClick: () -> Unit) {
    val counts = DataRepository.getStatusCounts(subject)
    val weak = counts[Status.RED] ?: 0
    val review = counts[Status.YELLOW] ?: 0
    val mastered = counts[Status.GREEN] ?: 0

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        if (total == 0) {
            drawCircle(trackColor, radius, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        } else {
            val weakAngle = (weak.toFloat() / total) * 360f
            val reviewAngle = (review.toFloat() / total) * 360f
            val masteredAngle = (mastered.toFloat() / total) * 360f
            var startAngle = -90f

            if (mastered > 0) {
                drawArc(
                    Status.GREEN.color, startAngle, masteredAngle, false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
                startAngle += masteredAngle
            }
            if (review > 0) {
                drawArc(
                    Status.YELLOW.color, startAngle, reviewAngle, false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
                startAngle += reviewAngle
            }
            if (weak > 0) {
                drawArc(
                    Status.RED.color, startAngle, weakAngle, false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    BackHandler { onBackClick() }

    val allChapters = DataRepository.getChaptersBySubject(subject)
    val filteredChapters = allChapters.filter {
        when (it.status) {
            Status.RED -> filterRed
            Status.YELLOW -> filterYellow
            Status.GREEN -> filterGreen
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(subject.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterRed,
                    onClick = { filterRed = !filterRed },
                    label = { Text("Weak") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Status.RED.color.copy(alpha = 0.2f),
                        selectedLabelColor = Status.RED.color
                    )
                )
                FilterChip(
                    selected = filterYellow,
                    onClick = { filterYellow = !filterYellow },
                    label = { Text("Review") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Status.YELLOW.color.copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFE6A800)
                    )
                )
                FilterChip(
                    selected = filterGreen,
                    onClick = { filterGreen = !filterGreen },
                    label = { Text("Mastered") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Status.GREEN.color.copy(alpha = 0.2f),
                        selectedLabelColor = Status.GREEN.color
                    )
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredChapters) { chapter ->
                    ChapterCard(
                        chapter = chapter,
                        onAttachNote = onAttachNote,
                        onLongPress = { selectedChapter -> chapterToDelete = selectedChapter }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddDialog(subject) { showAddDialog = false }
    }

    if (chapterToDelete != null) {
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete Chapter?") },
            text = { Text("Are you sure you want to delete '${chapterToDelete?.name}' from your war room?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        DataRepository.deleteChapter(context, chapterToDelete!!)
                        chapterToDelete = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterCard(
    chapter: Chapter,
    onAttachNote: (Chapter) -> Unit,
    onLongPress: (Chapter) -> Unit
) {
    val context = LocalContext.current

    val actionButton = when (chapter.status) {
        Status.RED -> "Mark for Review"
        Status.YELLOW -> "Mark Completed"
        Status.GREEN -> "✨ Chill"
    }

    val hasNote = chapter.noteUri != null
    val noteIcon = if (hasNote) Icons.Default.Description else Icons.Default.Add
    val noteTint = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(chapter) }
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(16.dp).background(chapter.status.color, CircleShape))
            Spacer(Modifier.width(16.dp))
            Text(
                text = chapter.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    if (hasNote) {
                        DataRepository.openNote(context, chapter.noteUri!!)
                    } else {
                        onAttachNote(chapter)
                    }
                },
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = noteTint
                )
            ) {
                Icon(noteIcon, contentDescription = if (hasNote) "Open Note" else "Attach Note")
            }

            IconButton(
                onClick = {
                    val prev = when (chapter.status) {
                        Status.GREEN -> Status.YELLOW
                        Status.YELLOW -> Status.RED
                        Status.RED -> Status.RED
                    }
                    if (prev != chapter.status) {
                        DataRepository.updateChapterStatus(context, chapter, prev)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.width(8.dp))

            if (chapter.status == Status.GREEN) {
                Text(
                    text = actionButton,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Status.GREEN.color,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                FilledTonalButton(
                    onClick = {
                        when (chapter.status) {
                            Status.RED -> DataRepository.updateChapterStatus(context, chapter, Status.YELLOW)
                            Status.YELLOW -> DataRepository.updateChapterStatus(context, chapter, Status.GREEN)
                            Status.GREEN -> { }
                        }
                    },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
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
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Chapter Name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        DataRepository.addChapter(context, name.trim(), subject)
                        onDismiss()
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TermsDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                "Welcome to JEE War Room",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    "\"Success is the sum of small efforts repeated day in and day out.\"",
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "This app tracks your JEE grind. Every chapter you complete, every concept you master - it all counts. No shortcuts, just consistent progress.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "By continuing, you agree to:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text("• Put in the work, not just track it", fontSize = 13.sp)
                Text("• Stay consistent with your prep", fontSize = 13.sp)
                Text("• Take responsibility for your progress", fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Let's get this rank. 💪")
            }
        }
    )
}