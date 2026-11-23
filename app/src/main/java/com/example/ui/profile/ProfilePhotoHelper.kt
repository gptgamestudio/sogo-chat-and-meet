package com.example.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.BurgundyPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldLight
import com.example.ui.theme.MatteBlackSurface
import com.example.ui.theme.MatteBlackSurfaceVariant
import com.example.ui.theme.OutlineGold
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object ProfilePhotoHelper {
    const val MAX_FIRESTORE_PHOTO_URI_LENGTH = 60_000
    private const val TAG = "ProfilePhotoHelper"

    fun createCameraOutputUri(context: Context): Uri {
        val photosDir = File(context.cacheDir, "profile_photos").apply {
            if (!exists()) mkdirs()
        }
        val imageFile = File(photosDir, "profile_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    suspend fun processImageUriToDataUri(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                } ?: return@withContext null

                if (rawBytes.isEmpty()) return@withContext null
                encodeBytesToCompactDataUri(rawBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to process picked/captured photo URI: ${t.message}")
                null
            }
        }
    }

    internal fun encodeBytesToCompactDataUri(rawBytes: ByteArray): String? {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(rawBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxSide > 640) {
            while ((maxSide / (sampleSize * 2)) >= 320) {
                sampleSize *= 2
            }
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return null

        val oriented = applyExifOrientation(decoded, orientation)
        if (oriented !== decoded) {
            decoded.recycle()
        }

        val maxDimension = 320
        val scale = (maxDimension.toFloat() / maxOf(oriented.width, oriented.height)).coerceAtMost(1f)
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== oriented) {
            oriented.recycle()
        }

        val baos = ByteArrayOutputStream()
        var quality = 75
        var encoded: String
        do {
            baos.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            encoded = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            quality -= 15
        } while (encoded.length > MAX_FIRESTORE_PHOTO_URI_LENGTH && quality >= 25)
        scaled.recycle()

        return if (encoded.length <= MAX_FIRESTORE_PHOTO_URI_LENGTH) encoded else null
    }

    internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }
}

class ProfilePhotoPickerController(
    val isProcessing: Boolean,
    val errorMessage: String?,
    val showSourceChooser: Boolean,
    val openChooser: () -> Unit,
    val dismissChooser: () -> Unit,
    val clearError: () -> Unit,
    val launchGalleryPicker: () -> Unit,
    val launchCameraCapture: () -> Unit
)

@Composable
fun rememberProfilePhotoPickerController(
    onPhotoReady: (String) -> Unit
): ProfilePhotoPickerController {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSourceChooser by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val handleResultUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessing = true
                errorMessage = null
                val compressedUri = ProfilePhotoHelper.processImageUriToDataUri(context, uri)
                if (compressedUri != null) {
                    onPhotoReady(compressedUri)
                } else {
                    errorMessage = "Could not load selected photo. Please try another photo."
                }
                isProcessing = false
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = handleResultUri
    )

    val fallbackGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handleResultUri
    )

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val capturedUri = pendingCameraUri
            pendingCameraUri = null
            if (success && capturedUri != null) {
                handleResultUri(capturedUri)
            }
        }
    )

    val startCameraCaptureInternal: () -> Unit = {
        try {
            val outputUri = ProfilePhotoHelper.createCameraOutputUri(context)
            pendingCameraUri = outputUri
            takePictureLauncher.launch(outputUri)
        } catch (t: Throwable) {
            pendingCameraUri = null
            Log.w("ProfilePhotoPicker", "Camera capture failed to launch: ${t.message}")
            errorMessage = "Unable to open camera on this device. Please choose a photo from your gallery."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                startCameraCaptureInternal()
            } else {
                errorMessage = "Camera permission is required to take a photo. You can still choose a photo from your gallery."
            }
        }
    )

    val launchGalleryPicker: () -> Unit = {
        showSourceChooser = false
        errorMessage = null
        try {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (t: Throwable) {
            Log.w("ProfilePhotoPicker", "PickVisualMedia unavailable, falling back to GetContent: ${t.message}")
            try {
                fallbackGalleryLauncher.launch("image/*")
            } catch (_: Throwable) {
                errorMessage = "Unable to open photo gallery on this device."
            }
        }
    }

    val launchCameraCapture: () -> Unit = {
        showSourceChooser = false
        errorMessage = null
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            startCameraCaptureInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    return ProfilePhotoPickerController(
        isProcessing = isProcessing,
        errorMessage = errorMessage,
        showSourceChooser = showSourceChooser,
        openChooser = { showSourceChooser = true },
        dismissChooser = { showSourceChooser = false },
        clearError = { errorMessage = null },
        launchGalleryPicker = launchGalleryPicker,
        launchCameraCapture = launchCameraCapture
    )
}

@Composable
fun ProfilePhotoSourceDialog(
    onDismiss: () -> Unit,
    onChooseGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatteBlackSurface,
        title = {
            Text(
                text = "Update Profile Picture",
                color = GoldLight,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Upload an existing picture from your gallery or snap a fresh selfie with your camera.",
                    color = TextMuted,
                    fontSize = 13.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MatteBlackSurfaceVariant,
                    border = BorderStroke(1.dp, OutlineGold.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onChooseGallery() }
                        .testTag("photo_source_gallery_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Choose from Gallery",
                            tint = GoldAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Choose from Gallery",
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Uses Android Photo Picker (no storage permission needed)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BurgundyPrimary.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTakePhoto() }
                        .testTag("photo_source_camera_option")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take a Photo",
                            tint = GoldLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Take a Photo",
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Open camera and save straight to your profile",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
