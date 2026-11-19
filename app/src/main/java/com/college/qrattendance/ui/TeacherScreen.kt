package com.college.qrattendance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.qrattendance.data.Mark
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.Session
import com.college.qrattendance.data.User
import com.college.qrattendance.data.readable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(user: User, onLogout: () -> Unit) {
    var live by remember { mutableStateOf<Session?>(null) }
    var details by remember { mutableStateOf<Session?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fresh by remember(user.code) { Repo.watchUser(user.code).catch { } }.collectAsState(initial = user)
    val subjects = (fresh ?: user).subjects
    val sessions = rememberLoad(user.code) { Repo.teacherSessions(user.code) }
    val active = (sessions as? Load.Ready)?.items?.firstOrNull { it.active }

    live?.let { session ->
        LiveSessionScreen(session = session, onClose = { live = null })
        return
    }

    details?.let { SessionDialog(it, onDismiss = { details = null }) }

    if (askLogout) {
        ConfirmDialog(
            title = "Выйти?",
            text = "Чтобы войти снова, понадобится ваш личный код.",
            confirm = "Выйти",
            onConfirm = onLogout,
            onDismiss = { askLogout = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Преподаватель") },
                actions = {
                    IconButton(onClick = { askLogout = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            if (active != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Сейчас идёт пара", style = MaterialTheme.typography.labelLarge)
                            Text(active.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                            Text("Начата ${active.startedAt.pretty()}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { live = active }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Открыть кьюар-код")
                            }
                        }
                    }
                }
            } else {
                item { SectionTitle("Начать пару") }

                if (subjects.isEmpty()) {
                    item { EmptyText("Администратор ещё не назначил вам предметы") }
                } else {
                    subjects.forEach { subject ->
                        item(key = "subject_$subject") {
                            OutlinedCard(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = subject }
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selected == subject, onClick = { selected = subject })
                                    Text(subject, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }

                    item {
                        error?.let { MessageCard(it, error = true, modifier = Modifier.padding(bottom = 8.dp)) }
                        Button(
                            onClick = {
                                val subject = selected
                                if (subject == null || subject !in subjects) {
                                    error = "Сначала выберите предмет"
                                    return@Button
                                }
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        live = Repo.startSession(fresh ?: user, subject)
                                    } catch (e: Exception) {
                                        error = e.readable()
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Начать пару", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            item { SectionTitle("Прошедшие пары", Modifier.padding(top = 12.dp)) }

            loadItems(sessions.filter { !it.active }, empty = "Здесь появятся завершённые пары", key = { it.id }) { session ->
                OutlinedCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { details = session }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(session.subject, style = MaterialTheme.typography.titleSmall)
                        Text(
                            session.startedAt.pretty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveSessionScreen(session: Session, onClose: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(session.token) }
    var secondsLeft by remember { mutableIntStateOf(Repo.ROTATE_SECONDS) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var askEnd by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    val marks = rememberLoad(session.id) { Repo.sessionMarks(session.id) }
    val qr = remember(token) { qrBitmap(Repo.qrText(session.id, token)) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(session.id) {
        var current = session.token
        while (true) {
            val next = Repo.newToken()
            val task = Repo.rotate(session.id, next, current)
            current = next
            token = next
            syncError = try {
                withTimeout(5_000) { task.await() }
                null
            } catch (e: TimeoutCancellationException) {
                "Нет связи с сервером — студенты пока не смогут отметиться"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.readable()
            }
            for (s in Repo.ROTATE_SECONDS downTo 1) {
                secondsLeft = s
                delay(1_000)
            }
        }
    }

    BackHandler(onBack = onClose)

    if (askEnd) {
        ConfirmDialog(
            title = "Завершить пару?",
            text = "После этого студенты больше не смогут отметиться.",
            confirm = "Завершить",
            onConfirm = {
                ending = true
                scope.launch {
                    try {
                        Repo.endSession(session.id)
                        onClose()
                    } catch (e: Exception) {
                        syncError = e.readable()
                    }
                    ending = false
                }
            },
            onDismiss = { askEnd = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session.subject, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = "Кьюар-код пары",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            item {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { secondsLeft / Repo.ROTATE_SECONDS.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    Text(
                        "Код обновится через $secondsLeft с",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            syncError?.let { item { MessageCard(it, error = true) } }

            item {
                val count = (marks as? Load.Ready)?.items?.size ?: 0
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Отметились", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("$count", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            when (marks) {
                is Load.Ready -> itemsIndexed(marks.items, key = { _, m -> m.id }) { index, mark ->
                    MarkLine(index + 1, mark)
                }
                else -> loadItems(marks, empty = "") { }
            }

            item {
                Button(
                    onClick = { askEnd = true },
                    enabled = !ending,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp)
                ) {
                    Text("Завершить пару")
                }
            }
        }
    }
}

@Composable
private fun MarkLine(number: Int, mark: Mark) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number.", modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(mark.studentName)
                Text(mark.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(mark.time.clock(), style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
}

@Composable
fun SessionDialog(session: Session, onDismiss: () -> Unit) {
    val marks = rememberLoad(session.id) { Repo.sessionMarks(session.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(session.subject)
                Text(session.startedAt.pretty(), style = MaterialTheme.typography.bodyMedium)
            }
        },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (marks is Load.Ready) {
                    item {
                        Text(
                            "Присутствовало: ${marks.items.size}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    itemsIndexed(marks.items, key = { _, m -> m.id }) { index, mark -> MarkLine(index + 1, mark) }
                }
                if (marks !is Load.Ready || marks.items.isEmpty()) {
                    loadItems(marks, empty = "Никто не отметился") { }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
