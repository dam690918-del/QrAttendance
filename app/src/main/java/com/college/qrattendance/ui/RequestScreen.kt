package com.college.qrattendance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.Request
import com.college.qrattendance.data.Role
import com.college.qrattendance.data.User
import com.college.qrattendance.data.readable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestScreen(onBack: () -> Unit, onUser: (User) -> Unit) {
    var request by remember { mutableStateOf<Request?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Repo.myRequest()
            .catch {
                loadError = it.readable()
                loaded = true
            }
            .collect {
                request = it
                loaded = true
            }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заявка на регистрацию") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val current = request
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                loadError != null -> MessageCard(loadError!!, error = true, modifier = Modifier.padding(16.dp))
                current == null || editing -> RequestForm(
                    initial = current,
                    onSent = { editing = false }
                )
                current.status == "pending" -> PendingView(current)
                current.status == "approved" -> ApprovedView(current, onUser, onRetry = { editing = true })
                else -> RejectedView(onRetry = { editing = true })
            }
        }
    }
}

@Composable
private fun RequestForm(initial: Request?, onSent: () -> Unit) {
    var lastName by rememberSaveable { mutableStateOf(initial?.lastName.orEmpty()) }
    var firstName by rememberSaveable { mutableStateOf(initial?.firstName.orEmpty()) }
    var group by rememberSaveable { mutableStateOf(initial?.group.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Заполните данные. Администратор проверит заявку и выдаст вам личный код для входа.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Фамилия") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(firstName, { firstName = it }, label = { Text("Имя") }, singleLine = true, keyboardOptions = words, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            group,
            { group = it },
            label = { Text("Группа") },
            placeholder = { Text("например, ИС-21") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (lastName.isBlank() || firstName.isBlank() || group.isBlank()) {
                    error = "Заполните все поля"
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        Repo.sendRequest(firstName.trim(), lastName.trim(), group.trim().uppercase())
                        onSent()
                    } catch (e: Exception) {
                        error = e.readable()
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Отправить заявку")
        }
    }
}

@Composable
private fun PendingView(request: Request) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            "Заявка отправлена",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            "${request.fullName}, ${request.group}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "Как только администратор её примет, здесь появится ваш личный код. Экран можно закрыть — заявка не потеряется.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ApprovedView(request: Request, onUser: (User) -> Unit, onRetry: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Заявка принята!", style = MaterialTheme.typography.titleLarge, color = Success)
        Card(Modifier.padding(top = 20.dp)) {
            Column(Modifier.padding(horizontal = 32.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ваш личный код", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(request.code, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            }
        }
        Text(
            "Запомните или запишите код. Он привязан к этому телефону.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        onUser(Repo.login(request.code, Role.STUDENT))
                    } catch (e: Exception) {
                        error = e.readable()
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp)
        ) {
            Text("Войти")
        }
        TextButton(onClick = onRetry, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
            Text("Код не подходит? Подать новую заявку")
        }
    }
}

@Composable
private fun RejectedView(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Заявка отклонена", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(
            "Проверьте, правильно ли указаны данные, и отправьте заявку ещё раз.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) {
            Text("Заполнить заново")
        }
    }
}
