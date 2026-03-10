package com.example.jeewarroom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Data classes and enums
enum class Subject { PHYSICS, CHEMISTRY, MATHS }

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
    private val gson = Gson()
    var chapters = mutableStateListOf<Chapter>()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

// Main Activity
class MainActivity : ComponentActivity() {
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            currentChapterForNote?.let { chapter ->
                DataRepository.updateChapterNote(this, chapter, it)
            }
        }
    }
    private var currentChapterForNote: Chapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataRepository.initialize(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    var showTermsDialog by remember { mutableStateOf(!DataRepository.isTermsAccepted(this)) }
                    var selectedSubject by remember { mutableStateOf<Subject?>(null) }

                    if (showTermsDialog) {
                        TermsDialog(onAccept = {
                            DataRepository.acceptTerms(this)
                            showTermsDialog = false
                        })
                    } else {
                        if (selectedSubject == null) {
                            MainDashboard(onSubjectClick = { selectedSubject = it })
                        } else {
                            SubjectDetailScreen(
                                subject = selectedSubject!!,
                                onBackClick = { selectedSubject = null },
                                onAttachNote = { chapter ->
                                    currentChapterForNote = chapter
                                    filePickerLauncher.launch("application/pdf")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(onSubjectClick: (Subject) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Help") },
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
            Spacer(Modifier.height(40.dp))
            Text("JEE WAR ROOM", fontSize = 14.sp, color = Color.Gray, letterSpacing = 2.sp)
            Spacer(Modifier.height(80.dp))
            SubjectItem(Subject.PHYSICS, "P", onClick = { onSubjectClick(Subject.PHYSICS) })
            Spacer(Modifier.height(60.dp))
            SubjectItem(Subject.CHEMISTRY, "C", onClick = { onSubjectClick(Subject.CHEMISTRY) })
            Spacer(Modifier.height(60.dp))
            SubjectItem(Subject.MATHS, "M", onClick = { onSubjectClick(Subject.MATHS) })
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun SubjectItem(subject: Subject, letter: String, onClick: () -> Unit) {
    val counts = DataRepository.getStatusCounts(subject)
    val weak = counts[Status.RED] ?: 0
    val review = counts[Status.YELLOW] ?: 0
    val mastered = counts[Status.GREEN] ?: 0

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgress(weak, review, mastered)
            Text(letter, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(subject.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("$weak Weak • $review Review • $mastered Mastered", fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun CircularProgress(weak: Int, review: Int, mastered: Int) {
    val total = weak + review + mastered
    Canvas(Modifier.size(120.dp)) {
        val strokeWidth = 12.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        if (total == 0) {
            drawCircle(Color.LightGray, radius, style = Stroke(strokeWidth))
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
    var showMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filterRed by remember { mutableStateOf(true) }
    var filterYellow by remember { mutableStateOf(true) }
    var filterGreen by remember { mutableStateOf(true) }

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
        topBar = {
            TopAppBar(
                title = { Text("Chapter List", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Help") },
                            onClick = {
                                showMenu = false
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:")
                                    putExtra(Intent.EXTRA_EMAIL, arrayOf("t.preethivardhanreddy@gmail.com"))
                                }
                                context.startActivity(Intent.createChooser(intent, "Send Email"))
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { showFilter = !showFilter }
                    .padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Filter by Status", color = Color.Gray)
                    Icon(
                        if (showFilter) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null,
                        tint = Color.Gray
                    )
                }
                if (showFilter) {
                    Spacer(Modifier.height(12.dp))
                    FilterRow("Weak", filterRed) { filterRed = it }
                    FilterRow("Review", filterYellow) { filterYellow = it }
                    FilterRow("Mastered", filterGreen) { filterGreen = it }
                }
            }
            HorizontalDivider(color = Color.LightGray.copy(0.3f))

            // Chapters
            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredChapters) { chapter ->
                    ChapterRow(chapter, onAttachNote)
                    HorizontalDivider(color = Color.LightGray.copy(0.3f))
                }
            }
        }
    }

    if (showAddDialog) {
        AddDialog(subject) { showAddDialog = false }
    }
}

@Composable
fun FilterRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
fun ChapterRow(chapter: Chapter, onAttachNote: (Chapter) -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val statusLabel = when (chapter.status) {
        Status.GREEN -> "✨ Chill"
        Status.YELLOW -> "Revise Concepts"
        Status.RED -> "Start Reading"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { showMenu = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).background(chapter.status.color, CircleShape))
        Spacer(Modifier.width(16.dp))
        Text(chapter.name, Modifier.weight(1f))
        IconButton(onClick = { onAttachNote(chapter) }, Modifier.size(24.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = Color.Gray)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {
                val next = when (chapter.status) {
                    Status.RED -> Status.YELLOW
                    Status.YELLOW -> Status.GREEN
                    Status.GREEN -> Status.GREEN
                }
                DataRepository.updateChapterStatus(context, chapter, next)
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(20.dp), tint = Color.Gray)
        }
        Spacer(Modifier.width(16.dp))
        Surface(color = Color.Black, shape = MaterialTheme.shapes.small) {
            Text(statusLabel, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        DropdownMenuItem(text = { Text("Mark as Weak") }, onClick = {
            showMenu = false
            DataRepository.updateChapterStatus(context, chapter, Status.RED)
        })
        DropdownMenuItem(text = { Text("Mark as Review") }, onClick = {
            showMenu = false
            DataRepository.updateChapterStatus(context, chapter, Status.YELLOW)
        })
        DropdownMenuItem(text = { Text("Mark as Mastered") }, onClick = {
            showMenu = false
            DataRepository.updateChapterStatus(context, chapter, Status.GREEN)
        })
        if (chapter.noteUri != null) {
            DropdownMenuItem(text = { Text("Open Note") }, onClick = {
                showMenu = false
                DataRepository.openNote(context, chapter.noteUri!!)
            })
        }
        DropdownMenuItem(text = { Text("Attach Note") }, onClick = {
            showMenu = false
            onAttachNote(chapter)
        })
        DropdownMenuItem(text = { Text("Delete") }, onClick = {
            showMenu = false
            DataRepository.deleteChapter(context, chapter)
        })
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
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
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
        title = { Text("Terms & Conditions") },
        text = { Text("Welcome to JEE War Room!\n\nThis app helps track your JEE prep.\n\nGood luck! 🚀") },
        confirmButton = { Button(onClick = onAccept) { Text("Accept") } }
    )
}