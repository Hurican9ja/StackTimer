package com.dibya.stacktimer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// --- 1. DB & DATA ENGINE ---
@Entity(tableName = "timer_stacks")
data class TimerStackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val itemsJson: String,
    val repeats: Int
)

@Dao
interface TimerStackDao {
    @Query("SELECT * FROM timer_stacks ORDER BY id DESC")
    fun getAllStacks(): Flow<List<TimerStackEntity>>
    @Upsert suspend fun upsertStack(stack: TimerStackEntity)
    @Delete suspend fun deleteStack(stack: TimerStackEntity)
}

@Database(entities = [TimerStackEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timerStackDao(): TimerStackDao
}

data class TimerItem(val id: Long = System.currentTimeMillis(), var label: String, var mins: Int, var secs: Int, var color: Int = 0xFF6750A4.toInt())

// --- 2. MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "hybrid_pro_db")
            .fallbackToDestructiveMigration(true).build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("ST_Prefs", Context.MODE_PRIVATE)
        val isFirst = prefs.getBoolean("isFirst", true)

        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf(if (isFirst) "onboarding" else "library") }
                val stacks by db.timerStackDao().getAllStacks().collectAsState(initial = emptyList())
                var activeEntity by remember { mutableStateOf<TimerStackEntity?>(null) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        "onboarding" -> OnboardingScreen {
                            prefs.edit(true) { putBoolean("isFirst", false) }
                            screen = "library"
                        }
                        "library" -> LibraryScreen(
                            stacks = stacks,
                            onSelect = { selectedStack -> activeEntity = selectedStack; screen = "main" },
                            onNew = { activeEntity = null; screen = "main" },
                            onOpenBackup = { screen = "backup" }
                        )
                        "main" -> StackTimerApp(
                            activeStack = activeEntity,
                            onBack = { screen = "library" },
                            onSave = { entityToSave ->
                                scope.launch { db.timerStackDao().upsertStack(entityToSave) }
                                activeEntity = entityToSave
                                Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { entityToDelete ->
                                scope.launch { db.timerStackDao().deleteStack(entityToDelete) }
                                screen = "library"
                            }
                        )
                        "backup" -> BackupScreen(stacks, onBack = { screen = "library" }, onRestore = { list ->
                            scope.launch { list.forEach { db.timerStackDao().upsertStack(it.copy(id = 0)) } }
                            screen = "library"
                        })
                    }
                }
            }
        }
    }
}

// --- 3. LIBRARY SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(stacks: List<TimerStackEntity>, onSelect: (TimerStackEntity) -> Unit, onNew: () -> Unit, onOpenBackup: () -> Unit) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(text = "MY STACKS", fontWeight = FontWeight.Black) }, actions = { IconButton(onClick = onOpenBackup) { Icon(imageVector = Icons.Default.Backup, contentDescription = null) } }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, shape = RoundedCornerShape(24.dp)) { Icon(imageVector = Icons.Default.Add, contentDescription = null) }
        }
    ) { p ->
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(paddingValues = p).padding(all = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(stacks) { stack ->
                val listType = object : TypeToken<List<TimerItem>>() {}.type
                val itemsList: List<TimerItem> = Gson().fromJson(stack.itemsJson, listType)
                val totalMins = (itemsList.sumOf { it.mins * 60 + it.secs } * stack.repeats) / 60

                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(stack) }, shape = RoundedCornerShape(size = 24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(all = 16.dp)) {
                        Text(text = stack.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(text = "${itemsList.size} Steps", fontSize = 12.sp, modifier = Modifier.alpha(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(height = 12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Text(text = "${totalMins}m", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { onSelect(stack) }, modifier = Modifier.size(size = 36.dp).background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 4. MAIN TIMER APP (CLASSIC UX) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackTimerApp(activeStack: TimerStackEntity?, onBack: () -> Unit, onSave: (TimerStackEntity) -> Unit, onDelete: (TimerStackEntity) -> Unit) {
    val context = LocalContext.current
    val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }

    var stackName by remember(activeStack) { mutableStateOf(value = activeStack?.name ?: "Default") }
    val type = object : TypeToken<List<TimerItem>>() {}.type
    val initialStack = if (activeStack != null) Gson().fromJson<List<TimerItem>>(activeStack.itemsJson, type) else emptyList()
    val timerStack = remember(activeStack) { mutableStateListOf<TimerItem>().apply { addAll(initialStack) } }
    var repeats by remember(activeStack) { mutableIntStateOf(value = activeStack?.repeats ?: 1) }

    var running by remember { mutableStateOf(false) }
    var activeIdx by remember { mutableIntStateOf(value = 0) }
    var timeLeft by remember { mutableIntStateOf(value = 0) }
    var currentLoop by remember { mutableIntStateOf(value = 1) }
    var isModified by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTimerRenameDialog by remember { mutableStateOf(false) }
    var renamingIdx by remember { mutableIntStateOf(value = -1) }

    fun pulse(vibe: Int = VibrationEffect.EFFECT_CLICK) = vibrator.vibrate(VibrationEffect.createPredefined(vibe))

    LaunchedEffect(running, timeLeft) {
        if (running && timeLeft > 0) { delay(1000); timeLeft-- }
        else if (running && timeLeft == 0) {
            pulse(VibrationEffect.EFFECT_DOUBLE_CLICK)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            if (activeIdx < timerStack.size - 1) {
                activeIdx++; timeLeft = timerStack[activeIdx].mins * 60 + timerStack[activeIdx].secs
            } else if (currentLoop < repeats) {
                currentLoop++
                activeIdx = 0
                timeLeft = timerStack[0].mins * 60 + timerStack[0].secs
            } else { running = false; pulse(VibrationEffect.EFFECT_HEAVY_CLICK) }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stackName.uppercase(), fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                actions = {
                    val tint = if (isModified || activeStack == null) MaterialTheme.colorScheme.primary else Color.Gray
                    IconButton(onClick = {
                        onSave(TimerStackEntity(activeStack?.id ?: 0, stackName, Gson().toJson(timerStack.toList()), repeats))
                        isModified = false; pulse()
                    }) { Icon(imageVector = if (!isModified && activeStack != null) Icons.Default.CheckCircle else Icons.Default.Save, contentDescription = null, tint = tint) }

                    var mOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { mOpen = true; pulse() }) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = mOpen, onDismissRequest = { mOpen = false }) {
                        DropdownMenuItem(text = { Text(text = "Rename Stack") }, onClick = { showRenameDialog = true; mOpen = false; pulse() }, leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) })
                        DropdownMenuItem(text = { Text(text = "Save as Copy") }, onClick = { onSave(TimerStackEntity(0, "$stackName (Copy)", Gson().toJson(timerStack.toList()), repeats)); mOpen = false; pulse() }, leadingIcon = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null) })
                        DropdownMenuItem(text = { Text(text = "Delete Stack") }, onClick = { activeStack?.let { onDelete(it) }; mOpen = false; pulse() }, leadingIcon = { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red) })
                    }
                }
            )
        }
    ) { p ->
        Column(modifier = Modifier.padding(paddingValues = p).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().height(height = 220.dp).clip(shape = RoundedCornerShape(size = 40.dp)).background(color = MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(size = 12.dp)) {
                        Text(text = "LOOP $currentLoop OF $repeats", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    AnimatedContent(targetState = timeLeft, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "time") { t ->
                        Text(text = "%02d:%02d".format(t / 60, t % 60), fontSize = 75.sp, fontWeight = FontWeight.Thin)
                    }
                    Button(onClick = { if (!running && timerStack.isNotEmpty()) { timeLeft = timerStack[0].mins * 60 + timerStack[0].secs; activeIdx = 0; running = true } else { running = false }; pulse() }, shape = CircleShape, modifier = Modifier.size(size = 64.dp)) { Icon(imageVector = if (running) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Repeat Count", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (repeats > 1) { repeats--; isModified = true; pulse() } }) { Icon(imageVector = Icons.Default.Remove, contentDescription = null) }
                    Text(text = repeats.toString(), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    IconButton(onClick = { repeats++; isModified = true; pulse() }) { Icon(imageVector = Icons.Default.Add, contentDescription = null) }
                }
            }
            LazyColumn(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
                itemsIndexed(items = timerStack) { idx, item ->
                    val active = running && activeIdx == idx
                    val scale by animateFloatAsState(targetValue = if (active) 1.05f else 1f, label = "card")
                    Card(modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(size = 20.dp), colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(modifier = Modifier.padding(all = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(weight = 1f).clickable { renamingIdx = idx; showTimerRenameDialog = true; pulse() }) {
                                Text(text = item.label, fontWeight = FontWeight.Bold)
                                Text(text = "${item.mins}m ${item.secs}s", fontSize = 12.sp, modifier = Modifier.alpha(alpha = 0.6f))
                            }
                            IconButton(onClick = { if (idx > 0) { val itm = timerStack.removeAt(idx); timerStack.add(idx - 1, itm); isModified = true; pulse() } }) { Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = null) }
                            IconButton(onClick = { timerStack.add(timerStack[idx].copy(id = System.currentTimeMillis())); isModified = true; pulse() }) { Icon(imageVector = Icons.Default.AddCircle, contentDescription = null) }
                            IconButton(onClick = { timerStack.removeAt(idx); isModified = true; pulse() }) { Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        }
                    }
                }
            }
            Button(onClick = { showDialog = true; pulse() }, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).height(height = 56.dp), shape = RoundedCornerShape(size = 16.dp)) { Text(text = "ADD NEW TIMER") }
        }
    }
    if (showDialog) {
        var l by remember { mutableStateOf(value = "Set") }; var m by remember { mutableStateOf(value = "10") }; var s by remember { mutableStateOf(value = "0") }
        AlertDialog(onDismissRequest = { showDialog = false }, title = { Text(text = "Setup Timer") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                OutlinedTextField(value = l, onValueChange = { l = it }, label = { Text(text = "Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text(text = "Min") }, modifier = Modifier.weight(weight = 1f))
                    OutlinedTextField(value = s, onValueChange = { s = it }, label = { Text(text = "Sec") }, modifier = Modifier.weight(weight = 1f))
                }
            }
        }, confirmButton = { Button(onClick = { timerStack.add(TimerItem(label = l, mins = m.toIntOrNull() ?: 0, secs = s.toIntOrNull() ?: 0)); showDialog = false; isModified = true; pulse() }) { Text(text = "Add") } })
    }
    if (showRenameDialog) {
        var n by remember { mutableStateOf(value = stackName) }
        AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text(text = "Rename Stack") }, text = { OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(text = "Name") }) },
            confirmButton = { Button(onClick = { stackName = n; showRenameDialog = false; isModified = true; pulse() }) { Text(text = "Save") } })
    }
    if (showTimerRenameDialog && renamingIdx != -1) {
        var n by remember { mutableStateOf(value = timerStack[renamingIdx].label) }
        AlertDialog(onDismissRequest = { showTimerRenameDialog = false }, title = { Text(text = "Rename Step") }, text = { OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(text = "Name") }) },
            confirmButton = { Button(onClick = { timerStack[renamingIdx] = timerStack[renamingIdx].copy(label = n); showTimerRenameDialog = false; isModified = true; pulse() }) { Text(text = "Save") } })
    }
}

// --- 5. BACKUP SCREEN ---
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(stacks: List<TimerStackEntity>, onBack: () -> Unit, onRestore: (List<TimerStackEntity>) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var importText by remember { mutableStateOf(value = "") }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(text = "Backup") }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } } ) } ) { p ->
        Column(modifier = Modifier.padding(paddingValues = p).padding(all = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = { clipboard.setText(AnnotatedString(text = Gson().toJson(stacks))) }, modifier = Modifier.fillMaxWidth()) { Text(text = "COPY BACKUP CODE") }
            HorizontalDivider()
            OutlinedTextField(value = importText, onValueChange = { importText = it }, modifier = Modifier.fillMaxWidth().height(height = 150.dp), label = { Text(text = "Paste Code") })
            Button(onClick = { try { onRestore(Gson().fromJson(importText, object : TypeToken<List<TimerStackEntity>>() {}.type)); onBack() } catch (e: Exception) {} }, modifier = Modifier.fillMaxWidth()) { Text(text = "RESTORE") }
        }
    }
}

// --- 6. ONBOARDING SCREEN ---
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val state = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = state, modifier = Modifier.weight(weight = 1f)) { p ->
            Column(
                modifier = Modifier.fillMaxSize().padding(all = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val comp by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(if(p==0) R.raw.anim_stack else R.raw.anim_save))
                LottieAnimation(composition = comp, iterations = LottieConstants.IterateForever, modifier = Modifier.size(size = 280.dp))
                Spacer(modifier = Modifier.height(height = 40.dp))
                Text(text = if(p==0) "Stack Timers" else "Save Profiles", fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(height = 16.dp))
                Text(text = if(p==0) "Create individual timers and stack them." else "Backup and restore your sets easily.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(all = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row { repeat(2) { i -> Box(modifier = Modifier.padding(all = 4.dp).size(size = 10.dp).clip(shape = CircleShape).background(color = if (state.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))) } }
            Button(onClick = { if (state.currentPage < 1) scope.launch { state.animateScrollToPage(page = 1) } else onDone() }) { Text(text = if (state.currentPage == 1) "START" else "NEXT") }
        }
    }
}