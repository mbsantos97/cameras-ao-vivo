package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Diálogo do DS. O foco inicial fica sempre no botão seguro (cancelar). */
@Composable
fun AoVivoDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "Cancelar",
    danger: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val safe = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface(modifier) {
            Text(title, style = AoVivoTheme.type.headline, color = AoVivoTheme.colors.textPrimary)
            if (message != null) {
                Text(message, style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textSecondary)
            }
            content?.invoke()
            Row(
                Modifier.fillMaxWidth().padding(top = AoVivoTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm, Alignment.End),
            ) {
                AoVivoButton(dismissText, onDismiss, kind = ButtonKind.SECONDARY, modifier = Modifier.focusRequester(safe))
                if (confirmText != null && onConfirm != null) {
                    AoVivoButton(confirmText, onConfirm, kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY)
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { safe.requestFocus() } }
}

@Composable
fun DialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = AoVivoTheme.colors
    Column(
        modifier
            .width(AoVivoTheme.sizes.dialogWidth)
            .background(c.surfaceRaised, AoVivoTheme.shapes.radiusLg)
            .border(AoVivoTheme.sizes.hairline, c.outline, AoVivoTheme.shapes.radiusLg)
            .padding(AoVivoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm),
    ) { content() }
}

enum class TextInputKind { TEXT, PASSWORD, NUMBER, PIN, IP }

/** Entrada de texto pelo teclado da TV. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    kind: TextInputKind = TextInputKind.TEXT,
    hint: String? = null,
    maxLength: Int = 64,
) {
    val c = AoVivoTheme.colors
    var value by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogSurface {
            Text(title, style = AoVivoTheme.type.headline, color = c.textPrimary)
            if (hint != null) Text(hint, style = AoVivoTheme.type.caption, color = c.textSecondary)
            BasicTextField(
                value = value,
                onValueChange = { v -> value = filterInput(v, kind).take(maxLength) },
                singleLine = true,
                textStyle = AoVivoTheme.type.title.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                visualTransformation =
                    if (kind == TextInputKind.PASSWORD || kind == TextInputKind.PIN) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            when (kind) {
                                TextInputKind.PASSWORD -> KeyboardType.Password
                                TextInputKind.NUMBER, TextInputKind.IP -> KeyboardType.Decimal
                                TextInputKind.PIN -> KeyboardType.NumberPassword
                                TextInputKind.TEXT -> KeyboardType.Text
                            },
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { onConfirm(value) }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .background(c.surfaceHigh, AoVivoTheme.shapes.radiusMd)
                        .border(AoVivoTheme.sizes.hairline, c.focus, AoVivoTheme.shapes.radiusMd)
                        .padding(AoVivoTheme.spacing.md),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm, Alignment.End)) {
                AoVivoButton("Cancelar", onDismiss, kind = ButtonKind.SECONDARY)
                AoVivoButton("Salvar", { onConfirm(value) })
            }
        }
    }
    LaunchedEffect(Unit) {
        runCatching {
            field.requestFocus()
            keyboard?.show()
        }
    }
}

private fun filterInput(
    v: String,
    kind: TextInputKind,
): String =
    when (kind) {
        TextInputKind.NUMBER, TextInputKind.PIN -> v.filter { it.isDigit() }
        TextInputKind.IP -> v.filter { it.isDigit() || it == '.' || it == ':' || it.isLetter() || it == '-' }
        else -> v
    }

/** Estado global do toast (topo central, 3 s). */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial by mutableLongStateOf(0L)

    fun show(text: String) {
        message = text
        serial++
    }

    internal val key: Long get() = serial

    internal fun clear() {
        message = null
    }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

@Composable
fun BoxScope.TvToastHost(state: ToastState) {
    val c = AoVivoTheme.colors
    val millis = AoVivoTheme.motion.toastMillis
    val msg = state.message
    LaunchedEffect(state.key) {
        if (state.message != null) {
            delay(millis)
            state.clear()
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn(AoVivoTheme.motion.medium()),
        exit = fadeOut(AoVivoTheme.motion.medium()),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = AoVivoTheme.spacing.safeVertical),
    ) {
        Box(
            Modifier
                .background(c.surfaceRaised, AoVivoTheme.shapes.pill)
                .border(AoVivoTheme.sizes.hairline, c.outline, AoVivoTheme.shapes.pill)
                .padding(horizontal = AoVivoTheme.spacing.lg, vertical = AoVivoTheme.spacing.sm),
        ) {
            Text(msg.orEmpty(), style = AoVivoTheme.type.body, color = c.textPrimary)
        }
    }
}

/** Estado vazio: ícone, título, mensagem e ação opcional. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AoVivoTheme.colors
    Column(
        modifier.padding(AoVivoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = c.textSecondary, modifier = Modifier.size(AoVivoTheme.sizes.ptzArrow))
        }
        Text(title, style = AoVivoTheme.type.title, color = c.textPrimary, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = AoVivoTheme.type.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            AoVivoButton(actionText, onAction, modifier = Modifier.padding(top = AoVivoTheme.spacing.sm))
        }
    }
}

/** Bloco estático de carregamento (sem shimmer). */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    Box(modifier.background(AoVivoTheme.colors.surfaceRaised, AoVivoTheme.shapes.radiusMd))
}
