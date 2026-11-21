package io.github.mbsantos97.camerasaovivo.player

import android.graphics.Matrix
import android.graphics.Outline
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView

@kotlinx.serialization.Serializable
enum class RenderMode { TEXTURE, SURFACE }

/**
 * Superfície de vídeo. Criada uma vez por fonte e nunca recriada por foco, overlay ou tela cheia:
 * só a matriz do TextureView muda (recorte, lente, zoom).
 */
@Composable
fun VideoSurface(
    player: StreamPlayer?,
    region: VideoRegion,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    renderMode: RenderMode = RenderMode.TEXTURE,
    /** Tamanho conhecido (ex.: resolução do perfil ONVIF) usado até o player informar o real. */
    fallbackSize: VideoSize? = null,
) {
    if (renderMode == RenderMode.SURFACE) {
        SurfaceVideo(player, modifier)
        return
    }
    val radiusPx = with(LocalDensity.current) { cornerRadius?.toPx() ?: 0f }
    val holder = remember { SurfaceState() }
    holder.player = player
    holder.region = region
    holder.radiusPx = radiusPx
    holder.fallbackSize = fallbackSize
    var view by remember { mutableStateOf<TextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                outlineProvider =
                    object : ViewOutlineProvider() {
                        override fun getOutline(
                            v: View,
                            outline: Outline,
                        ) = outline.setRoundRect(0, 0, v.width, v.height, holder.radiusPx)
                    }
                addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
                    if (r - l != or - ol || b - t != ob - ot) {
                        holder.player?.onOutputSize(r - l, b - t)
                        holder.applyTransform(v as TextureView)
                    }
                }
                view = this
            }
        },
        update = { tv ->
            val clip = holder.radiusPx > 0f
            if (tv.clipToOutline != clip) tv.clipToOutline = clip
            tv.invalidateOutline()
            holder.applyTransform(tv)
        },
    )

    val tv = view
    if (tv != null && player != null) {
        DisposableEffect(player, tv) {
            val out = VideoOutput.Texture(tv)
            player.attach(out)
            if (tv.width > 0) player.onOutputSize(tv.width, tv.height)
            onDispose { player.detach(out) }
        }
        LaunchedEffect(player, tv) {
            player.videoSize.collect { holder.applyTransform(tv) }
        }
    }
}

/** Estado mutável lido pelos callbacks da View (sem capturar valores antigos da composição). */
private class SurfaceState {
    var player: StreamPlayer? = null
    var region: VideoRegion = VideoRegion.FILL
    var radiusPx: Float = 0f
    var fallbackSize: VideoSize? = null
    private val matrix = Matrix()

    fun applyTransform(tv: TextureView) {
        val size = player?.videoSize?.value ?: fallbackSize
        val t =
            if (size == null) {
                Transform.IDENTITY
            } else {
                VideoTransform.compute(tv.width, tv.height, size.width, size.height, region)
            }
        matrix.setScale(t.scaleX, t.scaleY)
        matrix.postTranslate(t.translateX, t.translateY)
        tv.setTransform(matrix)
        tv.invalidate()
    }
}

@Composable
private fun SurfaceVideo(
    player: StreamPlayer?,
    modifier: Modifier,
) {
    var view by remember { mutableStateOf<SurfaceView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                    (v.tag as? StreamPlayer)?.onOutputSize(r - l, b - t)
                }
                view = this
            }
        },
    )
    val sv = view
    if (sv != null && player != null) {
        DisposableEffect(player, sv) {
            val out = VideoOutput.Surface(sv)
            sv.tag = player
            player.attach(out)
            if (sv.width > 0) player.onOutputSize(sv.width, sv.height)
            onDispose {
                player.detach(out)
                sv.tag = null
            }
        }
    }
}
