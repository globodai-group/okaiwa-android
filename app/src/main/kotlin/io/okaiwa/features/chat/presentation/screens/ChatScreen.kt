package io.okaiwa.features.chat.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.okaiwa.R
import io.okaiwa.core.theme.OkaiwaColors
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.entities.MessageStatus
import io.okaiwa.features.chat.domain.entities.MessageType
import io.okaiwa.features.chat.presentation.viewmodels.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat screen — renders a single conversation.
 *
 * Observes messages from the `ChatRepository` via `ChatViewModel` and
 * draws bubbles per message type: text, image, voice note, and crypto
 * payment card. Outgoing bubbles are aligned right with the lime
 * primary; incoming bubbles are aligned left with the elevated canvas.
 */
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the latest message when the thread grows.
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OkaiwaColors.Black)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ChatTopBar(
            title = uiState.title,
            subtitle = stringResource(
                if (uiState.isVerified) R.string.chat_header_subtitle_verified
                else R.string.chat_header_subtitle_encrypted,
            ),
            onNavigateBack = onNavigateBack,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                EncryptionBanner(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            }
            items(items = uiState.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isOutgoing = message.senderId == uiState.currentUserId,
                )
            }
        }

        MessageInputBar(
            value = messageInput,
            onValueChange = { messageInput = it },
            onSend = {
                if (messageInput.isNotBlank()) {
                    viewModel.sendMessage(messageInput)
                    messageInput = ""
                }
            },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OkaiwaColors.Black)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = OkaiwaColors.White,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase(),
                color = OkaiwaColors.Lime,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = OkaiwaColors.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = OkaiwaColors.Lime,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = subtitle,
                    color = OkaiwaColors.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.78f
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isOutgoing) 18.dp else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else 18.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        when (message.type) {
            MessageType.Text -> TextBubble(message, isOutgoing, maxBubbleWidth, shape)
            MessageType.Image -> ImageBubble(message, isOutgoing, maxBubbleWidth, shape)
            MessageType.VoiceNote -> VoiceNoteBubble(message, isOutgoing, maxBubbleWidth, shape)
            MessageType.CryptoPayment -> CryptoPaymentBubble(message, isOutgoing, maxBubbleWidth, shape)
            else -> TextBubble(message, isOutgoing, maxBubbleWidth, shape)
        }
    }
}

@Composable
private fun TextBubble(
    message: Message,
    isOutgoing: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(if (isOutgoing) OkaiwaColors.Lime else OkaiwaColors.BlackElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message.plaintextContent.orEmpty(),
            color = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
        MessageMeta(message, isOutgoing)
    }
}

@Composable
private fun ImageBubble(
    message: Message,
    isOutgoing: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(if (isOutgoing) OkaiwaColors.Lime else OkaiwaColors.BlackElevated),
    ) {
        // Placeholder image area — the real image loading lands with
        // Coil + the attachment API. Until then we draw a gradient tile
        // with an Image icon so the layout is testable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(OkaiwaColors.BlackCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = stringResource(R.string.chat_image_cd),
                tint = OkaiwaColors.Muted,
                modifier = Modifier.size(48.dp),
            )
        }
        if (!message.plaintextContent.isNullOrBlank()) {
            Text(
                text = message.plaintextContent.orEmpty(),
                color = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column { MessageMeta(message, isOutgoing) }
        }
    }
}

@Composable
private fun VoiceNoteBubble(
    message: Message,
    isOutgoing: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
) {
    val duration = message.attachments.firstOrNull()?.durationMs?.let {
        "0:${(it / 1000).toString().padStart(2, '0')}"
    } ?: "0:14"

    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(if (isOutgoing) OkaiwaColors.Lime else OkaiwaColors.BlackElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isOutgoing) OkaiwaColors.Black.copy(alpha = 0.15f)
                    else OkaiwaColors.Lime.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.chat_play_cd),
                tint = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.Lime,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // Simple waveform placeholder — 16 bars of varied heights.
        Row(
            modifier = Modifier.height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val heights = listOf(6, 12, 8, 16, 10, 14, 6, 18, 12, 8, 14, 10, 16, 6, 12, 8)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isOutgoing) OkaiwaColors.Black.copy(alpha = 0.5f)
                            else OkaiwaColors.WhiteDim
                        ),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.WhiteDim,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = duration,
                    color = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.WhiteDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            MessageMeta(message, isOutgoing)
        }
    }
}

@Composable
private fun CryptoPaymentBubble(
    message: Message,
    isOutgoing: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
) {
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .clip(shape)
            .background(if (isOutgoing) OkaiwaColors.Lime else OkaiwaColors.BlackElevated)
            .border(
                width = 1.dp,
                color = if (isOutgoing) OkaiwaColors.Black.copy(alpha = 0.15f) else OkaiwaColors.Lime.copy(alpha = 0.4f),
                shape = shape,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.Lime,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isOutgoing) R.string.chat_crypto_sent
                    else R.string.chat_crypto_received,
                ),
                color = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.Lime,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message.plaintextContent.orEmpty(),
            color = if (isOutgoing) OkaiwaColors.Black else OkaiwaColors.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.chat_crypto_confirmed),
            color = if (isOutgoing) OkaiwaColors.Black.copy(alpha = 0.7f) else OkaiwaColors.Muted,
            fontSize = 11.sp,
        )
        MessageMeta(message, isOutgoing)
    }
}

@Composable
private fun MessageMeta(message: Message, isOutgoing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.sentAt)),
            style = TextStyle(fontSize = 10.sp),
            color = if (isOutgoing) OkaiwaColors.Black.copy(alpha = 0.7f) else OkaiwaColors.Muted,
        )
        if (isOutgoing) {
            Spacer(modifier = Modifier.width(4.dp))
            when (message.status) {
                MessageStatus.Sent -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = OkaiwaColors.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                MessageStatus.Delivered, MessageStatus.Read -> Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    tint = if (message.status == MessageStatus.Read) OkaiwaColors.Black else OkaiwaColors.Black.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun EncryptionBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OkaiwaColors.Lime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = OkaiwaColors.Lime,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_encryption_banner_title),
            color = OkaiwaColors.WhiteDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.chat_encryption_banner_body),
            color = OkaiwaColors.Muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(OkaiwaColors.Black)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = { /* TODO: attach */ }) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = stringResource(R.string.chat_attach_cd),
                tint = OkaiwaColors.Muted,
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_message_placeholder), color = OkaiwaColors.Placeholder) },
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = RoundedCornerShape(24.dp),
            textStyle = TextStyle(color = OkaiwaColors.White, fontSize = 15.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OkaiwaColors.Lime,
                unfocusedBorderColor = OkaiwaColors.BlackBorder,
                cursorColor = OkaiwaColors.Lime,
                focusedContainerColor = OkaiwaColors.BlackElevated,
                unfocusedContainerColor = OkaiwaColors.BlackElevated,
            ),
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onSend, enabled = value.isNotBlank()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (value.isNotBlank()) OkaiwaColors.Lime else OkaiwaColors.BlackElevated
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send_cd),
                    tint = if (value.isNotBlank()) OkaiwaColors.Black else OkaiwaColors.Muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
