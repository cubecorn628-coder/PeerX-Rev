package com.example.ui.screen.register

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToContacts: () -> Unit,
    viewModel: RegisterViewModel = viewModel(
        factory = RegisterViewModel.Factory(
            (LocalContext.current.applicationContext as com.example.PeerXApp).accountDataStore
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var nameInput by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var rawPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            rawPhotoUri = uri
            photoBase64 = uriToBase64(context, uri)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is RegisterUiState.AlreadyRegistered) {
            onNavigateToContacts()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is RegisterUiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 450.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                text = "Selamat Datang di PeerX",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "PeerX adalah messenger serverless 100% P2P. Atur nama dan foto profil Anda untuk memancarkan identitas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            // Avatar Picker
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .clickable { photoPickerLauncher.launch("image/*") }
                                    .testTag("avatar_picker"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (rawPhotoUri != null) {
                                    AsyncImage(
                                        model = rawPhotoUri,
                                        contentDescription = "Foto profil",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Avatar Default",
                                        modifier = Modifier.fillMaxSize(0.85f),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = "Add Photo",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            // Nickname Input
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nama Lengkap / Nickname") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("username_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            // Submit Button
                            Button(
                                onClick = {
                                    if (nameInput.trim().isNotEmpty()) {
                                        viewModel.registerUser(nameInput.trim(), photoBase64)
                                    }
                                },
                                enabled = nameInput.trim().isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Mulai Chatting",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        
        // Scale down to max 200x200px to respect limits
        val maxDimension = 200
        val width = bitmap.width
        val height = bitmap.height
        val (scaledWidth, scaledHeight) = if (width > height) {
            val ratio = width.toFloat() / maxDimension
            Pair(maxDimension, (height / ratio).toInt())
        } else {
            val ratio = height.toFloat() / maxDimension
            Pair((width / ratio).toInt(), maxDimension)
        }
        
        val resized = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
