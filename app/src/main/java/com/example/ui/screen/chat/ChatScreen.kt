package com.example.ui.screen.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ChatMessage
import com.example.data.model.MessageFrom
import com.example.data.model.MessageType
import com.example.ui.screen.contacts.ProfileImage
import io.noties.markwon.Markwon
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerHash: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(
        key = "chat_screen_$peerHash",
        factory = ChatViewModel.Factory(
            (LocalContext.current.applicationContext as com.example.PeerXApp).peerRepository,
            (LocalContext.current.applicationContext as com.example.PeerXApp).accountDataStore,
            (LocalContext.current.applicationContext as com.example.PeerXApp).signalingClient,
            (LocalContext.current.applicationContext as com.example.PeerXApp).peerConnectionManager
        )
    )
) {
    val context = LocalContext.current
    val contact by viewModel.contact.collectAsState()
    val rtcState by viewModel.rtcState.collectAsState()
    val listMessages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isPeerTyping.collectAsState()
    val peerDetailInfo by viewModel.peerDetailInfo.collectAsState()
    val fileProgress by viewModel.fileProgress.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showPeerDetailDialog by remember { mutableStateOf(false) }
    var isRecordingAudio by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, bytes) = readUriData(context, uri)
            if (bytes != null) {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                viewModel.sendFileInChunks(name, mime, bytes)
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecordingAudio = true
            viewModel.startVoiceRecording(context)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Emulate or request coordinates and send
            viewModel.shareLocation(-6.2000, 106.8166)
        }
    }

    LaunchedEffect(peerHash) {
        viewModel.initChat(peerHash)
    }

    // Scroll to bottom on new messages
    LaunchedEffect(listMessages.size, isTyping) {
        if (listMessages.isNotEmpty()) {
            listState.animateScrollToItem(listMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.requestPeerUserInfo()
                                showPeerDetailDialog = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileImage(
                            base64 = contact?.photoBase64,
                            name = contact?.name ?: "Peer",
                            size = 36.dp
                        )
                        Column {
                            Text(
                                text = contact?.name ?: "Peer $peerHash",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (rtcState) {
                                                PeerState.CONNECTED -> MaterialTheme.colorScheme.tertiary
                                                PeerState.CONNECTING -> Color(0xFFF2994A) // Orange
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                )
                                Text(
                                    text = when (rtcState) {
                                        PeerState.CONNECTED -> "Online"
                                        PeerState.CONNECTING -> "Menghubungkan..."
                                        else -> "Offline"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearChatHistory() }) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Chat Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listMessages, key = { it.msgId }) { message ->
                        MessageBubbleItem(
                            message = message,
                            onReplySwipe = { replyToMessage = message },
                            onMapClick = { lat, lng ->
                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Lokasi+Peer)"))
                                context.startActivity(mapIntent)
                            }
                        )
                    }
                    if (isTyping) {
                        item {
                            TypingIndicatorItem()
                        }
                    }
                }

                // File Upload progress indicator overlay
                fileProgress?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = progress.first.toFloat() / progress.second,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Mengirim Berkas: ${((progress.first.toFloat() / progress.second) * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Reply Banner Overlay
            AnimatedVisibility(visible = replyToMessage != null) {
                replyToMessage?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Reply, contentDescription = "Reply", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reply.senderName ?: "Seseorang", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (reply.type == MessageType.TEXT) reply.content else "[Media]",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { replyToMessage = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Input Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Tambatan")
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            viewModel.setTypingState(true)
                            // Simulate clear typing state after inactivity
                        },
                        placeholder = { Text("Ketik pesan...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input"),
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    if (textInput.trim().isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.sendTextMessage(textInput, replyToMessage)
                                textInput = ""
                                replyToMessage = null
                                viewModel.setTypingState(false)
                            },
                            modifier = Modifier.testTag("send_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "Kirim", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Voice Recorder trigger
                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    isRecordingAudio = false
                                    viewModel.stopAndSendVoiceRecording()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecordingAudio) Icons.Default.StopCircle else Icons.Default.Mic,
                                contentDescription = "Voice Message",
                                tint = if (isRecordingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Dynamic attach options bar
            AnimatedVisibility(visible = showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachIconItem(
                        icon = Icons.Default.Image,
                        label = "Berkas",
                        color = Color(0xFF2D9CDB),
                        onClick = {
                            showAttachMenu = false
                            filePickerLauncher.launch("*/*")
                        }
                    )
                    AttachIconItem(
                        icon = Icons.Default.MyLocation,
                        label = "Lokasi",
                        color = Color(0xFF27AE60),
                        onClick = {
                            showAttachMenu = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    )
                }
            }
        }
    }

    // Detail Dialog Sheet
    if (showPeerDetailDialog) {
        AlertDialog(
            onDismissRequest = { showPeerDetailDialog = false },
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                TextButton(onClick = { showPeerDetailDialog = false }) {
                    Text("Selesai")
                }
            },
            title = {
                Text(
                    text = "Profil Peer & Telemetri",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ProfileImage(base64 = contact?.photoBase64, name = contact?.name ?: "Peer", size = 72.dp)
                    Text(contact?.name ?: "Peer $peerHash", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = "ID: $peerHash",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    peerDetailInfo?.let { details ->
                        if (details.hideInfo) {
                            Text(
                                text = "Peer ini mengaktifkan Mode Sembunyi Telemetri.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            TelemetryRow(icon = Icons.Default.BatteryChargingFull, name = "Daya Baterai", valStr = details.battery)
                            TelemetryRow(icon = Icons.Default.NetworkWifi, name = "Provider Seluler", valStr = details.provider)
                            TelemetryRow(icon = Icons.Default.Public, name = "Zona Waktu", valStr = details.timezone)
                            TelemetryRow(icon = Icons.Default.Place, name = "Negara / Kota", valStr = details.location)
                        }
                    } ?: run {
                        if (rtcState == PeerState.CONNECTED) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Mengambil data telemetri...", fontSize = 12.sp)
                        } else {
                            Text("Hubungan P2P belum terjalin untuk membaca data.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun TelemetryRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    valStr: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valStr ?: "Tak Ditemukan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun AttachIconItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color)
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White)
        }
        Text(label, fontSize = 11.sp)
    }
}

@Composable
fun MessageBubbleItem(
    message: ChatMessage,
    onReplySwipe: () -> Unit,
    onMapClick: (Double, Double) -> Unit
) {
    val isMe = message.from == MessageFrom.ME
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Reply context header
            if (message.replyToId != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Reply, contentDescription = "", modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Balas ${message.replyToName}: ${message.replyToContent}",
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                ),
                modifier = Modifier.clickable { onReplySwipe() }
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (message.type) {
                        MessageType.TEXT -> {
                            Text(text = message.content, fontSize = 14.sp)
                        }
                        MessageType.IMAGE -> {
                            val bitmap = remember(message.content) {
                                base64ToBitmap(message.content)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Image message",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("[Berkas Gambar Terenkripsi]", fontSize = 12.sp)
                            }
                        }
                        MessageType.VIDEO -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(Color.Black, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(48.dp), tint = Color.White)
                                Text("Video", fontSize = 10.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                            }
                        }
                        MessageType.FILE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AttachFile, contentDescription = "File")
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(message.filename ?: "Berkas", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    message.fileSize?.let {
                                        Text("${it / 1024} KB", fontSize = 11.sp, color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        MessageType.GEO -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Place, contentDescription = "Place", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                }
                                Button(
                                    onClick = { onMapClick(message.geoLat ?: 0.0, message.geoLng ?: 0.0) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Lihat di Peta", fontSize = 11.sp, color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                        MessageType.VOICE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { playVoice(context, message.content) },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Dengarkan", tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary)
                                }
                                Text("Pesan Suara", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = message.timeFormatted,
                        fontSize = 9.sp,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Peer sedang mengetik...",
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun base64ToBitmap(dataUrl: String): Bitmap? {
    return try {
        val cleanBase64 = dataUrl.substringAfter("base64,")
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun playVoice(context: Context, base64Url: String) {
    try {
        val clean = base64Url.substringAfter("base64,")
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val temp = File.createTempFile("voice_", ".m4a", context.cacheDir)
        temp.writeBytes(bytes)

        MediaPlayer().apply {
            setDataSource(temp.absolutePath)
            prepare()
            start()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun readUriData(context: Context, uri: Uri): Pair<String, ByteArray?> {
    return try {
        var name = "file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        Pair(name, bytes)
    } catch (e: Exception) {
        e.printStackTrace()
        Pair("file", null)
    }
}
