package com.example.ui.image_compressor

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed interface SaveStatus {
    object Idle : SaveStatus
    object Compressing : SaveStatus
    data class Success(val savedUri: Uri, val fileName: String) : SaveStatus
    data class Error(val errorMessage: String) : SaveStatus
}

class ImageCompressorViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _selectedUri = MutableStateFlow<Uri?>(null)
    val selectedUri = _selectedUri.asStateFlow()

    private val _originalSize = MutableStateFlow<Long>(0L)
    val originalSize = _originalSize.asStateFlow()

    private val _originalDimensions = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val originalDimensions = _originalDimensions.asStateFlow()

    private val _originalFileName = MutableStateFlow<String>("")
    val originalFileName = _originalFileName.asStateFlow()

    private val _quality = MutableStateFlow<Float>(0.75f) // default 75% quality
    val quality = _quality.asStateFlow()

    private val _compressedSize = MutableStateFlow<Long>(0L)
    val compressedSize = _compressedSize.asStateFlow()

    private val _compressedBitmap = MutableStateFlow<Bitmap?>(null)
    val compressedBitmap = _compressedBitmap.asStateFlow()

    private val _compressedBytes = MutableStateFlow<ByteArray?>(null)
    val compressedBytes = _compressedBytes.asStateFlow()

    private val _isCompressing = MutableStateFlow<Boolean>(false)
    val isCompressing = _isCompressing.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus = _saveStatus.asStateFlow()

    private var compressionJob: Job? = null

    fun selectImage(uri: Uri?) {
        _selectedUri.value = uri
        _saveStatus.value = SaveStatus.Idle
        if (uri != null) {
            queryOriginalImageInfo(uri)
            triggerCompression()
        } else {
            _originalSize.value = 0L
            _originalDimensions.value = Pair(0, 0)
            _originalFileName.value = ""
            _compressedSize.value = 0L
            _compressedBitmap.value = null
            _compressedBytes.value = null
        }
    }

    fun setQuality(newQuality: Float) {
        _quality.value = newQuality
        triggerCompression()
    }

    private fun queryOriginalImageInfo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            // Find file size
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (size == 0L) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                        size = fd.length
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _originalSize.value = size

            // Find file name
            var name = "tasvir.jpg"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val cursorName = cursor.getString(nameIndex)
                        if (!cursorName.isNullOrEmpty()) {
                            name = cursorName
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _originalFileName.value = name

            // Find dimensions
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(input, null, options)
                    _originalDimensions.value = Pair(options.outWidth, options.outHeight)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _originalDimensions.value = Pair(0, 0)
            }
        }
    }

    private fun triggerCompression() {
        val uri = _selectedUri.value ?: return
        val currentQualityPercent = (_quality.value * 100).toInt().coerceIn(5, 100)

        compressionJob?.cancel()
        compressionJob = viewModelScope.launch(Dispatchers.IO) {
            _isCompressing.value = true
            try {
                // Decode with safe inSampleSize bounds to prevent memory pressure
                val bitmapSrc = decodeBitmapSafe(context, uri, 1500)
                if (bitmapSrc != null) {
                    val stream = ByteArrayOutputStream()
                    bitmapSrc.compress(Bitmap.CompressFormat.JPEG, currentQualityPercent, stream)
                    val bytes = stream.toByteArray()

                    // Decode bytes into a fast, manageable preview bitmap
                    val compressedPreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    _compressedBytes.value = bytes
                    _compressedSize.value = bytes.size.toLong()
                    _compressedBitmap.value = compressedPreview
                } else {
                    _compressedBytes.value = null
                    _compressedSize.value = 0L
                    _compressedBitmap.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _compressedBytes.value = null
                _compressedSize.value = 0L
                _compressedBitmap.value = null
            } finally {
                _isCompressing.value = false
            }
        }
    }

    fun saveCompressedImage() {
        val bytes = _compressedBytes.value ?: return
        val originalName = _originalFileName.value.ifEmpty { "tasvir.jpeg" }
        val currentQualityPercent = (_quality.value * 100).toInt().coerceIn(5, 100)

        viewModelScope.launch(Dispatchers.IO) {
            _saveStatus.value = SaveStatus.Compressing
            
            val resolver = context.contentResolver
            val baseName = originalName.substringBeforeLast(".")
            val ext = if (originalName.contains(".")) originalName.substringAfterLast(".") else "jpg"
            val newFileName = "Resized_${baseName}_q${currentQualityPercent}_" + System.currentTimeMillis() + "." + ext
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PayamNoor")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri == null) {
                _saveStatus.value = SaveStatus.Error("خطا در ایجاد فایل در گالری")
                return@launch
            }

            try {
                resolver.openOutputStream(imageUri)?.use { out ->
                    out.write(bytes)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                _saveStatus.value = SaveStatus.Success(imageUri, newFileName)
            } catch (e: Exception) {
                e.printStackTrace()
                // Clean up invalid slot
                resolver.delete(imageUri, null, null)
                _saveStatus.value = SaveStatus.Error("خطا در ذخیره‌سازی داده‌های تصویر: ${e.localizedMessage}")
            }
        }
    }

    private fun decodeBitmapSafe(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            // 1. Just decode bounds
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            // 2. Compute inSampleSize
            var inSampleSize = 1
            if (width > maxDimension || height > maxDimension) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while ((halfWidth / inSampleSize) >= maxDimension || (halfHeight / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 3. Perform actual lightweight decode
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
