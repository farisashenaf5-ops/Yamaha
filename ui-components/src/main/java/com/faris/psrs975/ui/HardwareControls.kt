package com.faris.psrs975.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HardwareButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  ledOn: Boolean = false,
  ledColor: Color = PsrColors.LedGreen,
  showLedBar: Boolean = true,
  isPrimaryAccent: Boolean = false,
  fontSize: TextUnit = 9.sp,
  height: Dp = 28.dp,
) {
  val shape = RoundedCornerShape(4.dp)
  val bgBrush = if (isPrimaryAccent) {
    Brush.verticalGradient(listOf(Color(0xFF423A24), Color(0xFF282315)))
  } else if (ledOn) {
    Brush.verticalGradient(listOf(Color(0xFF2C323F), Color(0xFF1D212B)))
  } else {
    Brush.verticalGradient(listOf(Color(0xFF282C36), Color(0xFF181B22)))
  }
  val borderColor = when {
    ledOn -> ledColor.copy(alpha = 0.85f)
    isPrimaryAccent -> PsrColors.GoldAccent.copy(alpha = 0.7f)
    else -> PsrColors.ChassisBorder
  }

  Box(
    modifier = modifier
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .height(height)
      .clip(shape)
      .background(bgBrush)
      .border(1.dp, borderColor, shape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label }
      .padding(horizontal = 4.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      if (showLedBar) {
        Box(
          modifier = Modifier
            .width(16.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(if (ledOn) ledColor else Color(0xFF101318)),
        )
        Spacer(modifier = Modifier.height(2.dp))
      }
      Text(
        text = label,
        fontFamily = DisplayFont,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = if (ledOn) Color.White else PsrColors.SilverAccent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        lineHeight = fontSize,
      )
    }
  }
}

@Composable
fun RotaryKnob(
  label: String,
  value: Float, // 0f..1f
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp,
  testTag: String? = null,
  accentColor: Color = PsrColors.GoldAccent,
  valueText: String? = null,
) {
  val latestValue by rememberUpdatedState(value.coerceIn(0f, 1f))
  val latestOnChange by rememberUpdatedState(onValueChange)

  Column(
    modifier = modifier
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .semantics { contentDescription = "$label knob" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = label,
      fontFamily = DisplayFont,
      fontSize = 8.sp,
      fontWeight = FontWeight.SemiBold,
      color = PsrColors.SilverMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .pointerInput(Unit) {
          detectDragGestures { change, dragAmount ->
            change.consume()
            val delta = (-dragAmount.y + dragAmount.x * 0.5f) / 140f
            latestOnChange((latestValue + delta).coerceIn(0f, 1f))
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Outer scale arc ticks
        for (i in 0..10) {
          val deg = 135f + i * 27f
          val rad = (deg * PI / 180.0).toFloat()
          val r0 = radius * 0.84f
          val r1 = radius * 0.98f
          val active = (i / 10f) <= latestValue
          drawLine(
            color = if (active) accentColor else Color(0xFF3A4152),
            start = Offset(center.x + cos(rad) * r0, center.y + sin(rad) * r0),
            end = Offset(center.x + cos(rad) * r1, center.y + sin(rad) * r1),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }

        // Brushed metallic knob body
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF424857), Color(0xFF1D2028), Color(0xFF111318)),
            center = center - Offset(radius * 0.15f, radius * 0.15f),
            radius = radius * 0.85f,
          ),
          radius = radius * 0.76f,
          center = center,
        )
        drawCircle(
          color = Color(0xFF5D667A),
          radius = radius * 0.76f,
          center = center,
          style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
        )

        // Indicator line
        val angleDeg = 135f + latestValue * 270f
        val angleRad = (angleDeg * PI / 180.0).toFloat()
        drawLine(
          color = accentColor,
          start = Offset(center.x + cos(angleRad) * radius * 0.25f, center.y + sin(angleRad) * radius * 0.25f),
          end = Offset(center.x + cos(angleRad) * radius * 0.68f, center.y + sin(angleRad) * radius * 0.68f),
          strokeWidth = 2.4.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }
    }
    if (valueText != null) {
      Text(
        text = valueText,
        fontFamily = MonoFont,
        fontSize = 8.sp,
        color = accentColor,
        maxLines = 1,
      )
    }
  }
}

@Composable
fun StereoVuMeter(
  peakLeft: Float,
  peakRight: Float,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .background(Color(0xFF0A0C10), RoundedCornerShape(4.dp))
      .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 3.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    VuBarRow(label = "L", level = peakLeft)
    VuBarRow(label = "R", level = peakRight)
  }
}

@Composable
private fun VuBarRow(label: String, level: Float) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Text(text = label, fontFamily = MonoFont, fontSize = 8.sp, color = PsrColors.SilverMuted)
    Spacer(modifier = Modifier.width(4.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(1.5.dp),
      modifier = Modifier.weight(1f),
    ) {
      val segments = 14
      val litCount = (level.coerceIn(0f, 1f) * segments).toInt()
      for (i in 0 until segments) {
        val color = when {
          i >= litCount -> Color(0xFF1D222B)
          i >= 12 -> PsrColors.LedRed
          i >= 9 -> PsrColors.LedAmber
          else -> PsrColors.LedGreen
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .height(4.dp)
            .background(color, RoundedCornerShape(1.dp)),
        )
      }
    }
  }
}

@Composable
fun PanelSectionFrame(
  title: String,
  modifier: Modifier = Modifier,
  accentColor: Color = PsrColors.GoldAccent,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .background(PsrColors.ChassisPanel, RoundedCornerShape(6.dp))
      .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
      .padding(6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
      Box(modifier = Modifier.weight(1f).height(1.dp).background(PsrColors.ChassisBorder))
      Text(
        text = " $title ",
        fontFamily = DisplayFont,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        color = accentColor,
        letterSpacing = 0.8.sp,
      )
      Box(modifier = Modifier.weight(1f).height(1.dp).background(PsrColors.ChassisBorder))
    }
    content()
  }
}

/**
 * Spring-loaded vertical Pitch Bend wheel (-maxSemitones..+maxSemitones).
 * Returns smoothly to center (0 st) when released, matching Yamaha PSR-S975 hardware behavior.
 */
@Composable
fun PitchBendWheel(
  pitchBendSemis: Float,
  onPitchBendChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  maxSemitones: Float = 2f,
  testTag: String = "pitch_bend_wheel",
) {
  val latestOnChange by rememberUpdatedState(onPitchBendChange)
  val clamped = pitchBendSemis.coerceIn(-maxSemitones, maxSemitones)
  val animatedSemis by animateFloatAsState(
    targetValue = clamped,
    animationSpec = spring(stiffness = Spring.StiffnessMedium),
    label = "pitch_bend_spring",
  )
  var dragAccumulator by remember { mutableFloatStateOf(0f) }

  Column(
    modifier = modifier
      .testTag(testTag)
      .semantics { contentDescription = "Pitch Bend wheel" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "PITCH",
      fontFamily = DisplayFont,
      fontSize = 7.5.sp,
      fontWeight = FontWeight.Bold,
      color = PsrColors.SilverMuted,
      maxLines = 1,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Box(
      modifier = Modifier
        .width(28.dp)
        .weight(1f)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF090B0F))
        .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
        .pointerInput(maxSemitones) {
          detectVerticalDragGestures(
            onDragStart = { dragAccumulator = clamped },
            onDragEnd = {
              dragAccumulator = 0f
              latestOnChange(0f)
            },
            onDragCancel = {
              dragAccumulator = 0f
              latestOnChange(0f)
            },
            onVerticalDrag = { change, dragAmount ->
              change.consume()
              val travelPx = (size.height.toFloat() * 0.45f).coerceAtLeast(32f)
              val delta = (-dragAmount / travelPx) * maxSemitones
              dragAccumulator = (dragAccumulator + delta).coerceIn(-maxSemitones, maxSemitones)
              latestOnChange(dragAccumulator)
            },
          )
        }
        .padding(3.dp),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val norm = (animatedSemis / maxSemitones).coerceIn(-1f, 1f) // +1 top, -1 bottom

        // Wheel recessed track
        drawRoundRect(
          brush = Brush.verticalGradient(
            listOf(Color(0xFF141821), Color(0xFF2B313F), Color(0xFF141821)),
          ),
          topLeft = Offset.Zero,
          size = Size(w, h),
          cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        // Active deflection glow from center
        val midY = h / 2f
        val thumbCenterY = midY - norm * (h * 0.36f)
        if (kotlin.math.abs(norm) > 0.03f) {
          val topY = kotlin.math.min(midY, thumbCenterY)
          val barH = kotlin.math.abs(thumbCenterY - midY)
          drawRect(
            color = PsrColors.LedCyan.copy(alpha = 0.38f),
            topLeft = Offset(2f, topY),
            size = Size((w - 4f).coerceAtLeast(1f), barH),
          )
        }

        // Ribbed horizontal grip lines
        val ribCount = 9
        for (i in 1 until ribCount) {
          val ry = (h / ribCount) * i
          drawLine(
            color = Color(0xFF0A0C10),
            start = Offset(3f, ry),
            end = Offset(w - 3f, ry),
            strokeWidth = 1.dp.toPx(),
          )
        }

        // Center 0-detent line
        drawLine(
          color = Color(0xFF4F596E),
          start = Offset(1f, midY),
          end = Offset(w - 1f, midY),
          strokeWidth = 1.dp.toPx(),
        )

        // Tactile glowing thumb notch
        val notchH = 8.dp.toPx()
        drawRoundRect(
          brush = Brush.verticalGradient(listOf(Color(0xFF566076), Color(0xFF282D38))),
          topLeft = Offset(1.dp.toPx(), thumbCenterY - notchH / 2f),
          size = Size((w - 2.dp.toPx()).coerceAtLeast(2f), notchH),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawLine(
          color = if (kotlin.math.abs(norm) > 0.04f) PsrColors.LedCyan else PsrColors.SilverAccent,
          start = Offset(4.dp.toPx(), thumbCenterY),
          end = Offset(w - 4.dp.toPx(), thumbCenterY),
          strokeWidth = 2.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }
    }
    Spacer(modifier = Modifier.height(2.dp))
    val label = if (kotlin.math.abs(clamped) < 0.05f) "0.0" else String.format(java.util.Locale.US, "%+.1f", clamped)
    Text(
      text = label,
      fontFamily = MonoFont,
      fontSize = 7.sp,
      color = if (kotlin.math.abs(clamped) >= 0.05f) PsrColors.LedCyan else PsrColors.SilverMuted,
      maxLines = 1,
    )
  }
}

/**
 * Vertical Modulation Wheel (0.0..1.0 LFO vibrato depth).
 * Holds its set position upon release; tapping the readout resets modulation to 0%.
 */
@Composable
fun ModulationWheel(
  modDepth: Float,
  onModDepthChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "modulation_wheel",
) {
  val latestDepth by rememberUpdatedState(modDepth.coerceIn(0f, 1f))
  val latestOnChange by rememberUpdatedState(onModDepthChange)

  Column(
    modifier = modifier
      .testTag(testTag)
      .semantics { contentDescription = "Modulation wheel" },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = "MOD",
      fontFamily = DisplayFont,
      fontSize = 7.5.sp,
      fontWeight = FontWeight.Bold,
      color = PsrColors.SilverMuted,
      maxLines = 1,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Box(
      modifier = Modifier
        .width(28.dp)
        .weight(1f)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFF090B0F))
        .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
        .pointerInput(Unit) {
          detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            val travelPx = (size.height.toFloat() * 0.8f).coerceAtLeast(36f)
            val delta = -dragAmount / travelPx
            latestOnChange((latestDepth + delta).coerceIn(0f, 1f))
          }
        }
        .padding(3.dp),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val norm = latestDepth.coerceIn(0f, 1f)

        drawRoundRect(
          brush = Brush.verticalGradient(
            listOf(Color(0xFF141821), Color(0xFF2B313F), Color(0xFF141821)),
          ),
          topLeft = Offset.Zero,
          size = Size(w, h),
          cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        val bottomY = h - 4.dp.toPx()
        val topY = 4.dp.toPx()
        val thumbY = bottomY - norm * (bottomY - topY)

        if (norm > 0.01f) {
          drawRect(
            color = PsrColors.GoldAccent.copy(alpha = 0.42f),
            topLeft = Offset(2f, thumbY),
            size = Size((w - 4f).coerceAtLeast(1f), (bottomY - thumbY).coerceAtLeast(1f)),
          )
        }

        val ribCount = 9
        for (i in 1 until ribCount) {
          val ry = (h / ribCount) * i
          drawLine(
            color = Color(0xFF0A0C10),
            start = Offset(3f, ry),
            end = Offset(w - 3f, ry),
            strokeWidth = 1.dp.toPx(),
          )
        }

        val notchH = 8.dp.toPx()
        drawRoundRect(
          brush = Brush.verticalGradient(listOf(Color(0xFF566076), Color(0xFF282D38))),
          topLeft = Offset(1.dp.toPx(), thumbY - notchH / 2f),
          size = Size((w - 2.dp.toPx()).coerceAtLeast(2f), notchH),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawLine(
          color = if (norm > 0.02f) PsrColors.GoldHighlight else PsrColors.SilverAccent,
          start = Offset(4.dp.toPx(), thumbY),
          end = Offset(w - 4.dp.toPx(), thumbY),
          strokeWidth = 2.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = "${(latestDepth * 100f).toInt()}%",
      fontFamily = MonoFont,
      fontSize = 7.sp,
      color = if (latestDepth > 0.02f) PsrColors.GoldHighlight else PsrColors.SilverMuted,
      maxLines = 1,
      modifier = Modifier.clickable { latestOnChange(0f) },
    )
  }
}

/**
 * Paired Pitch Bend + Modulation Wheel hardware module positioned beside the 61-key keybed.
 */
@Composable
fun PitchBendModDeck(
  pitchBendSemis: Float,
  modDepth: Float,
  onPitchBendChange: (Float) -> Unit,
  onModDepthChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .background(PsrColors.ChassisPanel, RoundedCornerShape(6.dp))
      .border(1.dp, PsrColors.ChassisBorder, RoundedCornerShape(6.dp))
      .padding(horizontal = 5.dp, vertical = 4.dp)
      .testTag("pitch_mod_deck"),
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PitchBendWheel(
      pitchBendSemis = pitchBendSemis,
      onPitchBendChange = onPitchBendChange,
      modifier = Modifier.weight(1f).fillMaxHeight(),
    )
    ModulationWheel(
      modDepth = modDepth,
      onModDepthChange = onModDepthChange,
      modifier = Modifier.weight(1f).fillMaxHeight(),
    )
  }
}
