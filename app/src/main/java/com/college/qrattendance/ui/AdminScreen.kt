package com.college.qrattendance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.Request
import com.college.qrattendance.data.Role
import com.college.qrattendance.data.Subject
import com.college.qrattendance.data.User
import com.college.qrattendance.data.readable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val tabs = listOf("Заявки", "Студенты", "Преподаватели", "Предметы")

private class AdminActions(private val scope: CoroutineScope, private val snackbar: SnackbarHostState) {
    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            val message = try {
                block()
                done
            } catch (e: Exception) {
                e.readable()
            }
            message?.let { snackbar.showSnackbar(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var askLogout by remember { mutableStateOf(false) }
    var addingTeacher by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { AdminActions(scope, snackbar) }
    val subjects = rememberLoad(Unit) { Repo.subjects() }
    val subjectNames = (subjects as? Load.Ready)?.items?.map { it.name }.orEmpty()

    if (askLogout) {
        ConfirmDialog(
            title = "Выйти из режима администратора?",
            text = "Для входа снова понадобится пароль.",
            confirm = "Выйти",
            onConfirm = onLogout,
            onDismiss = { askLogout = false }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Администратор") },
                    actions = {
                        IconButton(onClick = { askLogout = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Выйти")
                        }
                    }
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == 2) {
                ExtendedFloatingActionButton(
                    onClick = { addingTeacher = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Преподаватель") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> RequestsTab(actions)
                1 -> StudentsTab(actions)
                2 -> TeachersTab(actions, subjectNames, addingTeacher, onAddingDone = { addingTeacher = false })
                else -> SubjectsTab(actions, subjects)
            }
        }
    }
}

@Composable
private fun RequestsTab(actions: AdminActions) {
    val requests = rememberLoad("requests") { Repo.pendingRequests() }
    var issued by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rejecting by remember { mutableStateOf<Request?>(null) }

    issued?.let { (name, code) ->
        CodeDialog(
            title = name,
            code = code,
            hint = "Код уже появился у студента на телефоне. Можно также продиктовать его.",
            onDismiss = { issued = null }
        )
    }

    rejecting?.let { request ->
        ConfirmDialog(
            title = "Отклонить заявку?",
            text = "${request.fullName}, группа ${request.group}",
            confirm = "Отклонить",
            onConfirm = { actions.act("Заявка отклонена") { Repo.reject(request) } },
            onDismiss = { rejecting = null }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        loadItems(requests, empty = "Новых заявок нет", key = { it.id }) { request ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(request.fullName, style = MaterialTheme.typography.titleMedium)
                    Text("Группа ${request.group}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 12.dp)) {
                        Button(onClick = {
                            actions.act {
                                val code = Repo.approve(request)
                                issued = request.fullName to code
                            }
                        }) { Text("Принять") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { rejecting = request }) { Text("Отклонить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentsTab(actions: AdminActions) {
    val students = rememberLoad(Role.STUDENT) { Repo.users(Role.STUDENT) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<User?>(null) }
    var deleting by remember { mutableStateOf<User?>(null) }

    val shown = students.filter {
        val q = query.trim().lowercase()
        q.isEmpty() || it.name.lowercase().contains(q) || it.group.lowercase().contains(q) || it.code.contains(q)
    }

    selected?.let { student ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(student.name) },
            text = {
                Column {
                    Text("Группа: ${student.group}")
                    Text("Код: ${student.code}")
                    Text(if (student.deviceId.isEmpty()) "Телефон не привязан" else "Привязан к телефону")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    actions.act("Привязка сброшена — студент может войти с нового телефона") { Repo.resetDevice(student.code) }
                }) { Text("Сбросить телефон") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selected = null
                    deleting = student
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    deleting?.let { student ->
        ConfirmDialog(
            title = "Удалить студента?",
            text = "${student.name} больше не сможет войти. Отметки останутся в истории.",
            confirm = "Удалить",
            onConfirm = { actions.act("Студент удалён") { Repo.deleteUser(student.code) } },
            onDismiss = { deleting = null }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск: ФИО, группа или код") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        loadItems(shown, empty = "Студентов не найдено", key = { it.code }) { student ->
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { selected = student }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(student.name, style = MaterialTheme.typography.titleSmall)
                        Text(student.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(student.code, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TeachersTab(
    actions: AdminActions,
    subjects: List<String>,
    adding: Boolean,
    onAddingDone: () -> Unit
) {
    val teachers = rememberLoad(Role.TEACHER) { Repo.users(Role.TEACHER) }
    var editing by remember { mutableStateOf<User?>(null) }
    var deleting by remember { mutableStateOf<User?>(null) }
    var issued by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (adding) {
        TeacherDialog(
            title = "Новый преподаватель",
            initialName = "",
            initialSubjects = emptyList(),
            allSubjects = subjects,
            onDismiss = onAddingDone,
            onSave = { name, picked ->
                onAddingDone()
                actions.act {
                    val code = Repo.addTeacher(name, picked)
                    issued = name to code
                }
            }
        )
    }

    editing?.let { teacher ->
        TeacherDialog(
            title = "Изменить",
            initialName = teacher.name,
            initialSubjects = teacher.subjects,
            allSubjects = (subjects + teacher.subjects).distinct(),
            onDismiss = { editing = null },
            onSave = { name, picked ->
                editing = null
                actions.act("Сохранено") { Repo.updateTeacher(teacher.code, name, picked) }
            },
            onDelete = {
                editing = null
                deleting = teacher
            }
        )
    }

    deleting?.let { teacher ->
        ConfirmDialog(
            title = "Удалить преподавателя?",
            text = "${teacher.name} больше не сможет войти.",
            confirm = "Удалить",
            onConfirm = { actions.act("Преподаватель удалён") { Repo.deleteUser(teacher.code) } },
            onDismiss = { deleting = null }
        )
    }

    issued?.let { (name, code) ->
        CodeDialog(
            title = name,
            code = code,
            hint = "Передайте этот код преподавателю — он нужен для входа.",
            onDismiss = { issued = null }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        loadItems(teachers, empty = "Преподавателей пока нет", key = { it.code }) { teacher ->
            OutlinedCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { editing = teacher }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(teacher.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(teacher.code, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        teacher.subjects.joinToString(", ").ifEmpty { "Предметы не назначены" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherDialog(
    title: String,
    initialName: String,
    initialSubjects: List<String>,
    allSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var picked by remember { mutableStateOf(initialSubjects.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            error = null
                        },
                        label = { Text("ФИО") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Предметы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    if (allSubjects.isEmpty()) {
                        Text(
                            "Сначала добавьте предметы на вкладке «Предметы»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                items(allSubjects) { subject ->
                    val checked = subject in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { picked = if (checked) picked - subject else picked + subject },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { picked = if (it) picked + subject else picked - subject })
                        Text(subject)
                    }
                }
                if (onDelete != null) {
                    item {
                        TextButton(onClick = onDelete, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Удалить преподавателя", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = "Введите ФИО"
                } else {
                    onSave(name.trim(), allSubjects.filter { it in picked })
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SubjectsTab(actions: AdminActions, subjects: Load<Subject>) {
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Subject?>(null) }
    val existing = (subjects as? Load.Ready)?.items.orEmpty()

    deleting?.let { subject ->
        ConfirmDialog(
            title = "Удалить предмет?",
            text = "«${subject.name}» пропадёт из списка. У преподавателей он останется, пока вы его не уберёте.",
            confirm = "Удалить",
            onConfirm = { actions.act("Предмет удалён") { Repo.deleteSubject(subject) } },
            onDismiss = { deleting = null }
        )
    }

    fun add() {
        val value = name.trim()
        when {
            value.isEmpty() -> return
            existing.any { it.name.equals(value, ignoreCase = true) } -> actions.act("Такой предмет уже есть") { }
            else -> {
                name = ""
                actions.act("Предмет добавлен") { Repo.addSubject(value) }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название предмета") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = ::add) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            }
        }
        loadItems(subjects, empty = "Список предметов пуст", key = { it.id }) { subject ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(subject.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { deleting = subject }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }
        }
    }
}
