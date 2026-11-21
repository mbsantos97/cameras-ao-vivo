package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** Base comum das linhas de configuração: fundo surfaceRaised, focado surfaceHigh + escala 1.02. */
@Composable
private fun RowShell(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    @DrawableRes icon: Int?,
    title: String,
    subtitle: String?,
    titleColor: Color? = null,
    iconColor: Color? = null,
    trailing: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val shape = AoVivoTheme.shapes.radiusMd
    var focused by remember { mutableStateOf(false) }
    val clickable =
        if (onClick != null) Modifier.aoVivoClickable(enabled = enabled, onClick = onClick) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickable)
                .aoVivoFocus(shape, FocusStyle.ROW, onFocusChange = { focused = it }, enabled = enabled)
                .heightIn(min = AoVivoTheme.sizes.rowHeight)
                .background(if (focused) c.surfaceHigh else c.surfaceRaised, shape)
                .padding(horizontal = s.md, vertical = s.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.sm),
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                null,
                tint = iconColor ?: if (enabled) c.textSecondary else c.textDisabled,
                modifier = Modifier.size(AoVivoTheme.sizes.iconBar),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AoVivoTheme.type.body, color = titleColor ?: if (enabled) c.textPrimary else c.textDisabled, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = AoVivoTheme.type.caption, color = c.textSecondary, maxLines = 2)
            }
        }
        trailing(focused)
    }
}

/** Linha de navegação: valor atual + seta. */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    RowShell(modifier, onClick, enabled, icon, title, subtitle) {
        if (value != null) Text(value, style = AoVivoTheme.type.label, color = AoVivoTheme.colors.textSecondary, maxLines = 1)
        Icon(painterResource(AoVivoIcons.Chevron), null, tint = AoVivoTheme.colors.textSecondary, modifier = Modifier.size(AoVivoTheme.sizes.iconTile))
    }
}

/** Linha com alternância (switch). OK alterna. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    RowShell(modifier, { onCheckedChange(!checked) }, enabled, icon, title, subtitle) {
        AoVivoSwitch(checked)
    }
}

/** Seletor inline: ←/→ trocam o valor sem abrir outra tela. OK avança para o próximo. */
@Composable
fun SelectorRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val idx = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    val keys =
        Modifier.onPreviewKeyEvent { e ->
            if (!enabled || options.isEmpty()) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> {
                    if (e.type == KeyEventType.KeyDown) onSelect((idx - 1 + options.size) % options.size)
                    true
                }
                Key.DirectionRight -> {
                    if (e.type == KeyEventType.KeyDown) onSelect((idx + 1) % options.size)
                    true
                }
                else -> {
                    false
                }
            }
        }
    RowShell(keys.then(modifier), { onSelect((idx + 1) % options.size.coerceAtLeast(1)) }, enabled, icon, title, subtitle) { focused ->
        val c = AoVivoTheme.colors
        Text(if (focused) "‹" else "", style = AoVivoTheme.type.title, color = c.textSecondary)
        Text(options.getOrElse(idx) { "" }, style = AoVivoTheme.type.label, color = if (focused) c.textPrimary else c.textSecondary, maxLines = 1)
        Text(if (focused) "›" else "", style = AoVivoTheme.type.title, color = c.textSecondary)
    }
}

/** Linha de entrada de texto: mostra o valor (ou ••• para senha); OK abre o teclado. */
@Composable
fun TextEntryRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    placeholder: String = "",
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val shown =
        when {
            value.isEmpty() -> placeholder
            masked -> "•".repeat(value.length.coerceAtMost(MAX_MASK))
            else -> value
        }
    RowShell(modifier, onClick, enabled, icon, title, null) {
        Text(shown, style = AoVivoTheme.type.label, color = AoVivoTheme.colors.textSecondary, maxLines = 1)
    }
}

/** Ação perigosa (remover, limpar dados): ícone em danger (texto danger não atinge 4,5:1 sobre surfaceRaised). */
@Composable
fun DangerRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = AoVivoIcons.Delete,
) {
    RowShell(modifier, onClick, true, icon, title, subtitle, iconColor = AoVivoTheme.colors.danger) {}
}

/** Linha só informativa (não focável). */
@Composable
fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AoVivoTheme.colors.textSecondary,
) {
    val s = AoVivoTheme.spacing
    Row(
        modifier.fillMaxWidth().padding(horizontal = s.md, vertical = s.xs),
        horizontalArrangement = Arrangement.spacedBy(s.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, style = AoVivoTheme.type.label.tabular(), color = valueColor)
    }
}

/** Título de seção dentro das telas de configuração. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = AoVivoTheme.type.title,
        color = AoVivoTheme.colors.textSecondary,
        modifier = modifier.padding(top = AoVivoTheme.spacing.lg, bottom = AoVivoTheme.spacing.xs),
    )
}

/** Switch desenhado em um único Canvas (sem componentes extras). */
@Composable
fun AoVivoSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = AoVivoTheme.colors
    val sizes = AoVivoTheme.sizes
    val pos by animateFloatAsState(if (checked) 1f else 0f, AoVivoTheme.motion.fast(), label = "switch")
    Canvas(modifier.size(sizes.switchWidth, sizes.switchHeight)) {
        val h = size.height
        drawRoundRect(
            color = if (checked) c.accent else c.outline,
            cornerRadius = CornerRadius(h / 2, h / 2),
        )
        val knob = h - h / 4
        val x = h / 8 + (size.width - knob - h / 4) * pos
        drawRoundRect(
            color = if (checked) c.onAccent else c.textSecondary,
            topLeft = Offset(x, h / 8),
            size = Size(knob, knob),
            cornerRadius = CornerRadius(knob / 2, knob / 2),
        )
    }
}

private const val MAX_MASK = 12

@Preview(widthDp = 720, heightDp = 420)
@Composable
private fun RowsPreview() {
    AoVivoTheme {
        Column(
            Modifier.background(AoVivoTheme.colors.surface).padding(AoVivoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs),
        ) {
            NavigationRow("Câmeras", {}, value = "2")
            ToggleRow("Mostrar relógio", true, {})
            SelectorRow("Latência", listOf("Baixa", "Normal", "Estável"), 1, {})
            TextEntryRow("Senha", "segredo", {}, masked = true)
            DangerRow("Remover câmera", {})
        }
    }
}
