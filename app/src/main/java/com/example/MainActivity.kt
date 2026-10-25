package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.image_compressor.ImageCompressorViewModel
import com.example.ui.image_compressor.SaveStatus
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PersianBlue
import com.example.ui.theme.PersianNavy
import com.example.ui.theme.PersianTeal

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ImageCompressorApp()
                    }
                }
            }
        }
    }
}

// Convert English digits to Persian Digits optimized character-by-character
fun String.toPersianDigits(): String = map { char ->
    if (char in '0'..'9') char + 1728 else char
}.joinToString("")

// Format byte size to Persian MB/KB representation
fun formatSizePersian(bytes: Long): String {
    if (bytes <= 0) return "۰ بایت"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.2f مگابایت", mb).toPersianDigits()
        kb >= 1.0 -> String.format("%.1f کیلوبایت", kb).toPersianDigits()
        else -> String.format("%d بایت", bytes).toPersianDigits()
    }
}

@Composable
fun ImageCompressorApp(
    viewModel: ImageCompressorViewModel = viewModel()
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val originalSize by viewModel.originalSize.collectAsState()
    val originalDimensions by viewModel.originalDimensions.collectAsState()
    val originalFileName by viewModel.originalFileName.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val compressedSize by viewModel.compressedSize.collectAsState()
    val compressedBitmap by viewModel.compressedBitmap.collectAsState()
    val isCompressing by viewModel.isCompressing.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectImage(uri)
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.saveCompressedImage()
        } else {
            Toast.makeText(context, "برای ذخیره فایل نیاز به دسترسی حافظه است.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            HeaderBar()
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedUri == null) {
                OnboardingScreen(
                    onSelectImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                CompressionWorkspace(
                    viewModel = viewModel,
                    selectedUri = selectedUri!!,
                    originalSize = originalSize,
                    originalDimensions = originalDimensions,
                    originalFileName = originalFileName,
                    quality = quality,
                    compressedSize = compressedSize,
                    compressedBitmap = compressedBitmap,
                    isCompressing = isCompressing,
                    onSelectAnotherImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSaveClicked = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val checkPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                            if (checkPermission == PackageManager.PERMISSION_GRANTED) {
                                viewModel.saveCompressedImage()
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        } else {
                            viewModel.saveCompressedImage()
                        }
                    }
                )
            }

            StatusOverlay(saveStatus = saveStatus, viewModel = viewModel, context = context)
        }
    }
}

@Composable
fun HeaderBar() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        tonalElevation = 0.dp,
        color = Color(0xFFFEF7FF)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE8DEF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoSizeSelectLarge,
                            contentDescription = null,
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "پیام نور",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20),
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "مدیریت و فشرده‌سازی آفلاین تصاویر",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF49454F),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                
                Surface(
                    color = Color(0xFFE8DEF8),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "آفلاین",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "۱۰۰٪ آفلاین",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4)
                            )
                        )
                    }
                }
            }
            HorizontalDivider(
                color = Color(0xFFCAC4D0),
                thickness = 1.dp
            )
        }
    }
}

@Composable
fun OnboardingScreen(onSelectImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .shadow(4.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = "تصویر راهنمای پیام نور",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "تصاویر خود را به راحتی بهینه‌سازی کنید!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                color = Color(0xFF1D1B20)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "با استفاده از پیام نور می‌توانید حجم تصاویر بزرگ را بدون افت محسوس کیفیت کاهش داده و فضای گوشی خود را آزاد کنید. تمامی فرآیندها کاملاً درون دستگاه شما و بدون نیاز به اینترنت انجام می‌شود.",
            style = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center,
                lineHeight = 26.sp,
                color = Color(0xFF49454F)
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onSelectImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("select_image_button_initial"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6750A4)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Collections,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "انتخاب تصویر از گالری",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun CompressionWorkspace(
    viewModel: ImageCompressorViewModel,
    selectedUri: Uri,
    originalSize: Long,
    originalDimensions: Pair<Int, Int>,
    originalFileName: String,
    quality: Float,
    compressedSize: Long,
    compressedBitmap: Bitmap?,
    isCompressing: Boolean,
    onSelectAnotherImage: () -> Unit,
    onSaveClicked: () -> Unit
) {
    var sliderPosition by remember(quality) { mutableFloatStateOf(quality) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Box A: Original metrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "حجم اصلی",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Normal
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSizePersian(originalSize),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1D1B20),
                            fontSize = 19.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${originalDimensions.first} × ${originalDimensions.second}".toPersianDigits() + " px",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F).copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Box B: Optimized metrics
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFE8DEF8), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حجم بهینه",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF49454F),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (originalSize > 0 && compressedSize > 0) {
                            val ratio = (1.0f - (compressedSize.toFloat() / originalSize.toFloat())) * 100
                            if (ratio > 0) {
                                Surface(
                                    color = Color(0xFF6750A4),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "${ratio.toInt()}٪-".toPersianDigits(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatSizePersian(compressedSize),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6750A4),
                            fontSize = 19.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val savedBytes = if (originalSize > compressedSize) originalSize - compressedSize else 0L
                    Text(
                        text = "کاهش حجم: ${formatSizePersian(savedBytes)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF6750A4).copy(alpha = 0.8f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Settings Slider Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "کیفیت فشرده‌سازی",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                        )
                    }

                    Surface(
                        color = Color(0xFFE8DEF8),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = "${(sliderPosition * 100).toInt()}٪".toPersianDigits(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4)
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { viewModel.setQuality(sliderPosition) },
                    valueRange = 0.05f..1.0f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFFEADDFF)
                    ),
                    modifier = Modifier.testTag("compression_quality_slider")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "بیشترین فشرده‌سازی",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F),
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "بالاترین کیفیت",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF49454F),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        // Image Preview Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.5.dp, Color(0xFFCAC4D0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "پیش‌نمایش تصویر",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20)
                        )
                    )
                    
                    if (isCompressing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF6750A4)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "در حال پردازش...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF6750A4)
                                )
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFE1D9F0),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "زنده",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF6750A4),
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (compressedBitmap != null) {
                        Image(
                            bitmap = compressedBitmap.asImageBitmap(),
                            contentDescription = "پیش‌نمایش تصویر فشرده",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "پیش‌نمایش موقت",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Button(
            onClick = onSaveClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("save_result_button"),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6750A4)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ذخیره خروجی",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }

        OutlinedButton(
            onClick = onSelectAnotherImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("select_another_image_button"),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF6750A4)
            ),
            border = BorderStroke(1.5.dp, Color(0xFF79747E))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "انتخاب تصویر جدید",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun StatusOverlay(
    saveStatus: SaveStatus,
    viewModel: ImageCompressorViewModel,
    context: Context
) {
    when (saveStatus) {
        is SaveStatus.Compressing -> {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "در حال کدگذاری و ذخیره‌سازی...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        is SaveStatus.Success -> {
            Dialog(onDismissRequest = { viewModel.selectImage(null) }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.testTag("success_saved_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(PersianTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PersianTeal,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "عملیات با موفقیت انجام شد!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = PersianTeal
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "تصویر فشرده شده در گالری ذخیره شد.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "نام فایل: ${saveStatus.fileName}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "آدرس ذخیره: پوشه Pictures/PayamNoor",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.selectImage(null) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "بستن تصویر",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, saveStatus.savedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصویر با:"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "اشتراک‌گذاری",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        is SaveStatus.Error -> {
            Dialog(onDismissRequest = { viewModel.selectImage(null) }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "بروز خطا در ذخیره‌سازی",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = saveStatus.errorMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.selectImage(null) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "تلاش مجدد")
                        }
                    }
                }
            }
        }

        else -> {}
    }
}
