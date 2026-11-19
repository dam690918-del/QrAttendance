package com.college.qrattendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.Role
import com.college.qrattendance.data.User
import com.college.qrattendance.data.readable
import kotlinx.coroutines.launch

private val roleLabels = mapOf(
    Role.STUDENT to "Студент",
    Role.TEACHER to "Учитель",
    Role.ADMIN to "Админ"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onUser: (User) -> Unit, onAdmin: () -> Unit, onRequest: () -> Unit) {
    var role by rememberSaveable { mutableStateOf(Role.STUDENT) }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val isAdmin = role == Role.ADMIN

    fun submit() {
        val value = input.trim()
        error = when {
            value.isEmpty() -> if (isAdmin) "Введите пароль" else "Введите личный код"
            isAdmin && value.length < 4 -> "Пароль должен быть не короче 4 символов"
            !isAdmin && (value.length != 6 || !value.all { it.isDigit() }) -> "Код состоит из 6 цифр"
            else -> null
        }
        if (error != null) return

        busy = true
        scope.launch {
            try {
                if (isAdmin) {
                    Repo.loginAdmin(value)
                    onAdmin()
                } else {
                    onUser(Repo.login(value, role))
                }
            } catch (e: Exception) {
                error = e.readable()
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Кьюар Посещаемость",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Отметка на парах по кьюар-коду",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(32.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Role.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = role == item,
                    onClick = {
                        role = item
                        input = ""
                        error = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, Role.entries.size)
                ) {
                    Text(roleLabels.getValue(item), maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = if (isAdmin) it else it.filter(Char::isDigit).take(6)
                error = null
            },
            label = { Text(if (isAdmin) "Пароль администратора" else "Личный код") },
            singleLine = true,
            enabled = !busy,
            isError = error != null,
            visualTransformation = if (isAdmin) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isAdmin) KeyboardType.Password else KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (isAdmin) {
            Text(
                "При самом первом входе введённый пароль станет паролем администратора",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = ::submit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Войти")
            }
        }

        if (role == Role.STUDENT) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRequest, enabled = !busy) {
                Text("Нет кода? Отправить заявку")
            }
        }
    }
}
