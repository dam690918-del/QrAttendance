package com.college.qrattendance.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.college.qrattendance.data.Mark
import com.college.qrattendance.data.Repo
import com.college.qrattendance.data.User
import com.college.qrattendance.data.readable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private fun scanOptions() = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("Наведите камеру на кьюар-код на экране преподавателя")
    .setBeepEnabled(false)
    .setOrientationLocked(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentScreen(user: User, onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var askLogout by remember { mutableStateOf(false) }
    val history = rememberLoad(user.code) { Repo.studentMarks(user.code) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { scan ->
        val text = scan.contents ?: return@rememberLauncherForActivityResult
        busy = true
        result = null
        scope.launch {
            result = try {
                val subject = Repo.checkIn(user, text)
                true to "Готово! Вы отмечены на паре «$subject»"
            } catch (e: Exception) {
                false to e.readable()
            }
            busy = false
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanner.launch(scanOptions())
        else result = false to "Без доступа к камере отсканировать код не получится. Разрешите его в настройках"
    }

    fun scan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) scanner.launch(scanOptions()) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

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
                title = { Text("Посещаемость") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Группа ${user.group}", modifier = Modifier.padding(top = 4.dp))
                        Text(
                            "Код: ${user.code}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = ::scan,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Отсканировать кьюар-код", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            result?.let { (ok, text) ->
                item { MessageCard(text, error = !ok) }
            }

            item { SectionTitle("Мои отметки") }

            loadItems(history, empty = "Пока нет ни одной отметки", key = { it.id }) { mark ->
                MarkRow(mark)
            }
        }
    }
}

@Composable
private fun MarkRow(mark: Mark) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mark.subject, style = MaterialTheme.typography.titleSmall)
                Text(
                    mark.teacherName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(mark.time.pretty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
