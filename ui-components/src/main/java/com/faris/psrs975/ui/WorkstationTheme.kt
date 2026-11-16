package com.faris.psrs975.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PsrColors {
  // Brushed Black Chassis & Hardware Surfaces
  val ChassisBlack = Color(0xFF0E1014)
  val ChassisPanel = Color(0xFF171A21)
  val ChassisElevated = Color(0xFF21252F)
  val ChassisBorder = Color(0xFF343B4B)
  val KeybedTray = Color(0xFF0A0B0E)

  // Metallic Accents
  val GoldAccent = Color(0xFFD4AF37)
  val GoldHighlight = Color(0xFFF3D36B)
  val SilverAccent = Color(0xFFD8E0EC)
  val SilverMuted = Color(0xFF919BAC)

  // Yamaha PSR-S975 LCD Screen Palette
  val LcdTopBar = Color(0xFF0B192E)
  val LcdSurfaceTop = Color(0xFFDCE7F5)
  val LcdSurfaceBottom = Color(0xFFB5C7DF)
  val LcdCardBg = Color(0xFFEDF3FA)
  val LcdCardActive = Color(0xFFCDE1FA)
  val LcdBorder = Color(0xFF6B84A6)
  val LcdTextDark = Color(0xFF0F1B2B)
  val LcdTextMuted = Color(0xFF3E5573)
  val LcdHighlightBlue = Color(0xFF1A62B8)

  // Hardware LEDs
  val LedGreen = Color(0xFF2EE56B)
  val LedGreenDark = Color(0xFF144726)
  val LedRed = Color(0xFFFF4B4B)
  val LedAmber = Color(0xFFFFB74D)
  val LedCyan = Color(0xFF4FC3F7)

  // Piano Keys
  val IvoryTop = Color(0xFFFAFBFD)
  val IvoryBottom = Color(0xFFE7EBF2)
  val IvoryPressed = Color(0xFFCFE2FA)
  val IvoryAcmpTint = Color(0xFFE7F0FC)
  val EbonyTop = Color(0xFF252932)
  val EbonyBottom = Color(0xFF0C0E12)
  val EbonyPressed = Color(0xFF3B669C)
}

val DisplayFont = FontFamily(Font(R.font.chakra_petch, FontWeight.Normal))
val BodyFont = FontFamily(Font(R.font.ibm_plex_sans, FontWeight.Normal))
val MonoFont = FontFamily(Font(R.font.roboto_mono, FontWeight.Normal))

val WorkstationTypography = Typography(
  displaySmall = TextStyle(
    fontFamily = DisplayFont,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.8.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = DisplayFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.4.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = MonoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.3.sp,
  ),
)

private val WorkstationColorScheme = darkColorScheme(
  primary = PsrColors.GoldAccent,
  onPrimary = Color(0xFF141106),
  secondary = PsrColors.LedCyan,
  onSecondary = Color(0xFF08141E),
  tertiary = PsrColors.LedGreen,
  background = PsrColors.ChassisBlack,
  onBackground = PsrColors.SilverAccent,
  surface = PsrColors.ChassisPanel,
  onSurface = PsrColors.SilverAccent,
  surfaceVariant = PsrColors.ChassisElevated,
  onSurfaceVariant = PsrColors.SilverMuted,
  outline = PsrColors.ChassisBorder,
  error = PsrColors.LedRed,
)

@Composable
fun PsrWorkstationTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = WorkstationColorScheme,
    typography = WorkstationTypography,
    content = content,
  )
}
