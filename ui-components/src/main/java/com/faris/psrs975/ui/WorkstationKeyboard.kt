package com.faris.psrs975.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Playable 61-key multi-touch velocity-sensitive keyboard (MIDI 36 C2 .. MIDI 96 C7).
 * Supports multi-finger chording, sliding glissando, touch velocity by vertical key position,
 * split-point visualization, left-hand ACMP zone tinting, and configurable key zoom + panning.
 */
@Composable
fun WorkstationKeyboard(
  pressedNotes: Set<Int>,
  splitPointMidi: Int,
  acmpEnabled: Boolean,
  keySizeScale: Float,
  viewportOffset: Float,
  onKeySizeScaleChange: (Float) -> Unit,
  onViewportOffsetChange: (Float) -> Unit,
  onNoteDown: (midiNote: Int, velocity: Int) -> Unit,
  onNoteUp: (midiNote: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val startMidi = 36 // C2
  val endMidi = 96   // C7 (61 keys total: 36 white, 25 black)

  val whiteNotes = remember {
    (startMidi..endMidi).filter { !isBlackKey(it) }
  }
  val blackNotes = remember {
    (startMidi..endMidi).filter { isBlackKey(it) }
  }
  val totalWhiteKeys = whiteNotes.size // 36

  val whiteIndexOf = remember {
    val map = HashMap<Int, Int>()
    var wIdx = 0
    for (m in startMidi..endMidi) {
      if (!isBlackKey(m)) {
        map[m] = wIdx
        wIdx++
      }
    }
    map
  }

  val latestOnNoteDown by rememberUpdatedState(onNoteDown)
  val latestOnNoteUp by rememberUpdatedState(onNoteUp)
  val latestScale by rememberUpdatedState(keySizeScale.coerceIn(1.0f, 3.0f))
  val latestPan by rememberUpdatedState(viewportOffset.coerceIn(0f, 1f))
  val pointerNoteMap = remember { mutableStateMapOf<PointerId, Int>() }
  val textMeasurer = rememberTextMeasurer()

  Column(
    modifier = modifier
      .background(PsrColors.KeybedTray)
      .border(1.dp, PsrColors.ChassisBorder)
      .testTag("keyboard_61_container"),
  ) {
    // Compact Keybed Zoom / Pan / Split indicator bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(24.dp)
        .background(Color(0xFF12151C))
        .padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("KEY SIZE:", fontFamily = DisplayFont, fontSize = 8.sp, color = PsrColors.SilverMuted)
        listOf(1.0f to "61", 1.55f to "39", 2.2f to "28", 2.8f to "22").forEach { (sc, lbl) ->
          val active = kotlin.math.abs(keySizeScale - sc) < 0.18f
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(if (active) PsrColors.GoldAccent else Color(0xFF222733))
              .clickable { onKeySizeScaleChange(sc) }
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = lbl,
              fontFamily = MonoFont,
              fontSize = 8.sp,
              fontWeight = FontWeight.Bold,
              color = if (active) Color.Black else PsrColors.SilverAccent,
            )
          }
        }
      }

      if (keySizeScale > 1.05f) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        ) {
          Text("PAN", fontFamily = DisplayFont, fontSize = 8.sp, color = PsrColors.SilverMuted)
          Slider(
            value = viewportOffset,
            onValueChange = onViewportOffsetChange,
            modifier = Modifier.weight(1f).height(18.dp).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
              thumbColor = PsrColors.GoldAccent,
              activeTrackColor = PsrColors.GoldAccent,
              inactiveTrackColor = Color(0xFF2E3444),
            ),
          )
        }
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }

      Text(
        text = "SPLIT: ${midiNoteName(splitPointMidi)} | ${if (acmpEnabled) "ACMP ZONE" else "FULL KBD"}",
        fontFamily = MonoFont,
        fontSize = 8.sp,
        color = if (acmpEnabled) PsrColors.LedCyan else PsrColors.SilverMuted,
      )
    }

    // Keybed Canvas + Multi-touch handler
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(top = 2.dp, bottom = 2.dp),
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .semantics { contentDescription = "61-key touch keyboard" }
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent()
                val viewWidth = size.width.toFloat()
                val viewHeight = size.height.toFloat()
                if (viewWidth <= 0f || viewHeight <= 0f) continue

                val totalVirtualWidth = viewWidth * latestScale
                val maxScroll = (totalVirtualWidth - viewWidth).coerceAtLeast(0f)
                val scrollPx = maxScroll * latestPan
                val whiteKeyWidth = totalVirtualWidth / totalWhiteKeys.toFloat()

                for (change in event.changes) {
                  val pid = change.id
                  if (change.pressed) {
                    val x = change.position.x + scrollPx
                    val y = change.position.y
                    val hitMidi = hitTestKey(
                      x = x,
                      y = y,
                      viewHeight = viewHeight,
                      whiteKeyWidth = whiteKeyWidth,
                      whiteNotes = whiteNotes,
                      blackNotes = blackNotes,
                      whiteIndexOf = whiteIndexOf,
                    )
                    val velocity = ((y / viewHeight).coerceIn(0.15f, 1.0f) * 85f + 42f)
                      .roundToInt()
                      .coerceIn(30, 127)
                    val prev = pointerNoteMap[pid]
                    if (hitMidi != null && prev != hitMidi) {
                      if (prev != null) {
                        val stillHeldByOther = pointerNoteMap.any { (otherId, n) -> otherId != pid && n == prev }
                        if (!stillHeldByOther) latestOnNoteUp(prev)
                      }
                      pointerNoteMap[pid] = hitMidi
                      val alreadyHeld = pressedNotes.contains(hitMidi)
                      if (!alreadyHeld) {
                        latestOnNoteDown(hitMidi, velocity)
                      }
                    } else if (hitMidi == null && prev != null) {
                      pointerNoteMap.remove(pid)
                      val stillHeldByOther = pointerNoteMap.any { (_, n) -> n == prev }
                      if (!stillHeldByOther) latestOnNoteUp(prev)
                    }
                    change.consume()
                  } else {
                    val prev = pointerNoteMap.remove(pid)
                    if (prev != null) {
                      val stillHeldByOther = pointerNoteMap.any { (_, n) -> n == prev }
                      if (!stillHeldByOther) latestOnNoteUp(prev)
                    }
                    change.consume()
                  }
                }
              }
            }
          },
      ) {
        val totalVirtualWidth = size.width * latestScale
        val maxScroll = (totalVirtualWidth - size.width).coerceAtLeast(0f)
        val scrollPx = maxScroll * latestPan
        val whiteKeyWidth = totalVirtualWidth / totalWhiteKeys.toFloat()
        val keyHeight = size.height
        val blackKeyWidth = whiteKeyWidth * 0.60f
        val blackKeyHeight = keyHeight * 0.61f

        // 1. Draw White Keys
        whiteNotes.forEachIndexed { wIdx, midi ->
          val left = wIdx * whiteKeyWidth - scrollPx
          val right = left + whiteKeyWidth
          if (right < 0f || left > size.width) return@forEachIndexed

          val isPressed = midi in pressedNotes
          val inLeftZone = acmpEnabled && midi < splitPointMidi
          val topColor = when {
            isPressed -> PsrColors.IvoryPressed
            inLeftZone -> PsrColors.IvoryAcmpTint
            else -> PsrColors.IvoryTop
          }
          val bottomColor = when {
            isPressed -> Color(0xFF9AC0EE)
            inLeftZone -> Color(0xFFD5E3F5)
            else -> PsrColors.IvoryBottom
          }

          drawRoundRect(
            brush = Brush.verticalGradient(listOf(topColor, bottomColor)),
            topLeft = Offset(left + 0.8f, 0f),
            size = Size((whiteKeyWidth - 1.6f).coerceAtLeast(1f), keyHeight - 2f),
            cornerRadius = CornerRadius(3f, 3f),
          )
          // Top keybed shadow
          drawRect(
            brush = Brush.verticalGradient(listOf(Color(0x44000000), Color.Transparent)),
            topLeft = Offset(left + 0.8f, 0f),
            size = Size((whiteKeyWidth - 1.6f).coerceAtLeast(1f), 8.dp.toPx()),
          )

          // Label C notes
          if (midi % 12 == 0 && whiteKeyWidth >= 14f) {
            val octave = (midi / 12) - 1
            drawOctaveLabel(textMeasurer, "C$octave", left + whiteKeyWidth * 0.18f, keyHeight - 14.dp.toPx(), inLeftZone)
          }
        }

        // 2. Draw Split Point Divider
        val splitWhiteIdx = whiteNotes.indexOfFirst { it >= splitPointMidi }
        if (splitWhiteIdx > 0) {
          val splitX = splitWhiteIdx * whiteKeyWidth - scrollPx
          if (splitX in 0f..size.width) {
            drawLine(
              color = PsrColors.GoldAccent,
              start = Offset(splitX, 0f),
              end = Offset(splitX, keyHeight),
              strokeWidth = 2.dp.toPx(),
            )
          }
        }

        // 3. Draw Black Keys
        for (midi in blackNotes) {
          val leftWhiteIdx = whiteIndexOf[midi - 1] ?: continue
          val centerX = (leftWhiteIdx + 1) * whiteKeyWidth - scrollPx
          val left = centerX - blackKeyWidth / 2f
          val right = left + blackKeyWidth
          if (right < 0f || left > size.width) continue

          val isPressed = midi in pressedNotes
          drawRoundRect(
            brush = Brush.verticalGradient(
              if (isPressed) listOf(PsrColors.EbonyPressed, Color(0xFF1F3B5C))
              else listOf(PsrColors.EbonyTop, PsrColors.EbonyBottom),
            ),
            topLeft = Offset(left, 0f),
            size = Size(blackKeyWidth, blackKeyHeight),
            cornerRadius = CornerRadius(2.5f, 2.5f),
          )
          // Specular highlight on black key face
          drawRect(
            color = if (isPressed) Color(0x44FFFFFF) else Color(0x22FFFFFF),
            topLeft = Offset(left + 2f, 2f),
            size = Size((blackKeyWidth - 4f).coerceAtLeast(1f), blackKeyHeight * 0.72f),
          )
        }
      }
    }
  }
}

private fun hitTestKey(
  x: Float,
  y: Float,
  viewHeight: Float,
  whiteKeyWidth: Float,
  whiteNotes: List<Int>,
  blackNotes: List<Int>,
  whiteIndexOf: Map<Int, Int>,
): Int? {
  val blackKeyHeight = viewHeight * 0.61f
  val blackKeyWidth = whiteKeyWidth * 0.60f

  if (y <= blackKeyHeight) {
    for (midi in blackNotes) {
      val leftWhiteIdx = whiteIndexOf[midi - 1] ?: continue
      val centerX = (leftWhiteIdx + 1) * whiteKeyWidth
      val left = centerX - blackKeyWidth / 2f
      val right = left + blackKeyWidth
      if (x in left..right) return midi
    }
  }

  val wIdx = (x / whiteKeyWidth).toInt()
  return whiteNotes.getOrNull(wIdx)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOctaveLabel(
  measurer: TextMeasurer,
  text: String,
  x: Float,
  y: Float,
  inLeftZone: Boolean,
) {
  val result = measurer.measure(
    text = text,
    style = TextStyle(
      fontFamily = MonoFont,
      fontSize = 7.sp,
      fontWeight = FontWeight.Bold,
      color = if (inLeftZone) Color(0xFF1B4B8C) else Color(0xFF566070),
    ),
  )
  drawText(result, topLeft = Offset(x, y))
}

fun isBlackKey(midi: Int): Boolean = (midi % 12) in setOf(1, 3, 6, 8, 10)

fun midiNoteName(midi: Int): String {
  val names = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
  val oct = (midi / 12) - 1
  return "${names[Math.floorMod(midi, 12)]}$oct"
}
