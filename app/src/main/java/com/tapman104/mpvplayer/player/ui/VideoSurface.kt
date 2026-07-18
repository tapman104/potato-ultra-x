package com.tapman104.mpvplayer.player.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tapman104.mpvplayer.player.core.MpvController

@Composable
fun VideoSurface(
    controller: MpvController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { surfaceView ->
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        controller.attach(holder.surface)
                    }
                    override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        controller.detach()
                    }
                })
            }
        },
        modifier = modifier
    )
}
