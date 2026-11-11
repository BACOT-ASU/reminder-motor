package com.bacad.remindermotor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bacad.remindermotor.R
import com.bacad.remindermotor.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Menampilkan foto motor. [source] bisa Uri (foto baru yang belum disimpan) atau null.
 * Tanpa foto: tampil ikon motor sebagai pengganti.
 */
@Composable
fun MotorPhoto(source: Uri?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, source) {
        value = source?.let { uri ->
            withContext(Dispatchers.IO) { PhotoStore.loadBitmap(context, uri, sizePx * 2)?.asImageBitmap() }
        }
    }
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.TwoWheeler,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/** Uri untuk foto yang sudah tersimpan di aplikasi. */
fun photoUri(path: String?): Uri? = path?.let { Uri.fromFile(File(it)) }

/**
 * Bagian form untuk memilih foto: dari galeri atau langsung dari kamera.
 * [current] = foto yang tampil sekarang (baru dipilih atau yang lama).
 */
@Composable
fun PhotoPickerField(
    current: Uri?,
    onPicked: (Uri) -> Unit,
    onRemove: (() -> Unit)?
) {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) onPicked(uri)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MotorPhoto(source = current, size = 140.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.photo_gallery))
            }
            OutlinedButton(
                onClick = {
                    val uri = PhotoStore.newCameraUri(context)
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.photo_camera))
            }
        }
        if (current != null && onRemove != null) {
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
