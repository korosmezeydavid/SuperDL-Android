package com.superdl.launcher.currency.compose

import android.graphics.RectF
import android.view.MotionEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.superdl.launcher.camera.CameraStabilityHelper
import com.superdl.launcher.gestures.SwipeGestureListener

@Composable
fun CurrencyRecognizerScreen(
    uiState: CurrencyRecognizerUiState,
    onPreviewViewReady: (PreviewView) -> Unit,
    onTouchEvent: (MotionEvent) -> Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CurrencyStatusBar(
                statusText = uiState.statusText.ifBlank { uiState.fatalError.orEmpty() },
                onExit = onExit
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            CameraStabilityHelper.configurePreviewView(previewView)
                            previewView.setOnTouchListener { _, event ->
                                onTouchEvent(event)
                            }
                            onPreviewViewReady(previewView)
                        }
                    },
                    update = { previewView ->
                        previewView.setOnTouchListener { _, event ->
                            onTouchEvent(event)
                        }
                    }
                )

                if (uiState.showDetectionOverlay && uiState.detectionBox != null) {
                    DetectionOverlay(box = uiState.detectionBox)
                }

                if (uiState.hintText.isNotBlank()) {
                    Text(
                        text = uiState.hintText,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyStatusBar(
    statusText: String,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 96.dp)
                .semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive
                    contentDescription = statusText
                }
        )
        Button(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .semantics {
                    contentDescription = "Kilépés a pénzfelismerőből"
                }
        ) {
            Text("Kilépés")
        }
    }
}

@Composable
private fun DetectionOverlay(box: RectF) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 3.dp.toPx())
        val left = box.left.coerceIn(0f, 1f) * size.width
        val top = box.top.coerceIn(0f, 1f) * size.height
        val width = box.width().coerceIn(0f, 1f) * size.width
        val height = box.height().coerceIn(0f, 1f) * size.height
        drawRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(left, top),
            size = Size(width, height),
            style = stroke
        )
    }
}

/** Helper kept near UI layer for gesture wiring from Activity. */
fun createCurrencyGestureListener(
    context: android.content.Context,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
): SwipeGestureListener = SwipeGestureListener(
    context = context,
    onSwipeUp = onSwipeUp,
    onSwipeDown = onSwipeDown,
    onSwipeRight = onSwipeRight,
    onSwipeLeft = onSwipeLeft
)