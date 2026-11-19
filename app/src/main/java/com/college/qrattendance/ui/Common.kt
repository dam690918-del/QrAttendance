package com.college.qrattendance.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.qrattendance.data.readable
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val items: List<T>) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Composable
fun <T> rememberLoad(key: Any?, source: () -> Flow<List<T>>): Load<T> {
    val flow = remember(key) {
        source().map<List<T>, Load<T>> { Load.Ready(it) }.catch { emit(Load.Failed(it.readable())) }
    }
    return flow.collectAsState(initial = Load.Loading).value
}

fun <T> Load<T>.filter(predicate: (T) -> Boolean): Load<T> =
    if (this is Load.Ready) Load.Ready(items.filter(predicate)) else this

fun <T> LazyListScope.loadItems(
    load: Load<T>,
    empty: String,
    key: ((T) -> Any)? = null,
    content: @Composable LazyItemScope.(T) -> Unit
) {
    when (load) {
        Load.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is Load.Failed -> item { MessageCard(load.message, error = true) }
        is Load.Ready -> {
            if (load.items.isEmpty()) {
                item { EmptyText(empty) }
            } else {
                items(load.items, key = key, itemContent = content)
            }
        }
    }
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun MessageCard(text: String, error: Boolean, modifier: Modifier = Modifier) {
    val colors = if (error) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    } else {
        CardDefaults.cardColors(containerColor = Success, contentColor = androidx.compose.ui.graphics.Color.White)
    }
    Card(modifier = modifier.fillMaxWidth(), colors = colors) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun CodeDialog(title: String, code: String, hint: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(code, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Text(hint, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

private val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.forLanguageTag("ru"))
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("ru"))

fun Date?.pretty(): String = this?.let { dateFormat.format(it) } ?: "—"

fun Date?.clock(): String = this?.let { timeFormat.format(it) } ?: "—"

fun qrBitmap(text: String, size: Int = 640): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
