package app.snag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.snag.BuildConfig

internal object DesignStudy {
    val name = BuildConfig.SNAG_DESIGN
    val active = name != "classic"
    val paper = name == "paper"
    val cinema = name == "cinema"
    val signal = name == "signal"
    val label = when(name) { "paper" -> "PAPER"; "cinema" -> "CINEMA"; "signal" -> "SIGNAL"; else -> "" }
}

internal fun studyShape(default: Int) = RoundedCornerShape(
    (when { DesignStudy.paper -> 6; DesignStudy.cinema -> 24; DesignStudy.signal -> 2; else -> default }).dp)

@Composable internal fun StudyDialogBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    val light = MaterialTheme.colorScheme.background.luminance() > .5f
    androidx.compose.runtime.SideEffect {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        if (window != null) androidx.core.view.WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@Composable internal fun studyText(ru: String, en: String): String =
    if (LocalConfiguration.current.locales[0].language == "ru") ru else en

@Composable internal fun StudyHome(onPickVideo: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    when {
        DesignStudy.paper -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(studyText("Что\nотправим?", "What shall\nwe send?"),
                style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp)
            HorizontalDivider(color = cs.outlineVariant)
            Text(studyText("ВИДЕО С ТЕЛЕФОНА", "VIDEO FROM YOUR PHONE"),
                style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
            FilledTonalButton(onClick = onPickVideo, shape = studyShape(16),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("study_pick_video")) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(studyText("Уменьшить размер", "Make it smaller"), style = MaterialTheme.typography.titleMedium)
                    Text(studyText("Выбрать видео →", "Choose a video →"), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(28.dp))
            }
        }
        DesignStudy.cinema -> Surface(color = cs.surfaceContainer, shape = studyShape(24)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("SNAG / CINEMA", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(48.dp), tint = cs.primary)
                }
                Text(studyText("В кадре —\nваш момент.", "Your moment.\nIn focus."),
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                Text(studyText("Сожмите видео целиком или оставьте только нужное.", "Compress the whole video or keep just the moment."),
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Button(onClick = onPickVideo, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("study_pick_video")) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(10.dp))
                    Text(studyText("Открыть видео", "Open video"))
                }
            }
        }
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = cs.primary, contentColor = cs.onPrimary, shape = studyShape(2)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(studyText("МЕНЬШЕ.\nЛЕГЧЕ.", "SMALLER.\nLIGHTER."), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCut, null, Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(studyText("Ловите момент.\nОтправляйте дальше.", "Catch the moment.\nPass it on."), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Button(onClick = onPickVideo, shape = studyShape(2),
                colors = ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("study_pick_video")) {
                Text(studyText("ВЫБРАТЬ ВИДЕО", "CHOOSE VIDEO"), Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }
    }
}

@Composable internal fun StudySizeReadout(source: String, target: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(studyText("ИСХОДНИК → РЕЗУЛЬТАТ", "SOURCE → RESULT"), style = MaterialTheme.typography.labelSmall,
            fontFamily = if (DesignStudy.paper) FontFamily.Monospace else FontFamily.SansSerif, color = cs.onSurfaceVariant)
        if (DesignStudy.cinema) {
            Text("$source → $target", style = MaterialTheme.typography.headlineSmall, color = cs.primary)
        } else {
            Text(source, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            Text("→ $target", style = MaterialTheme.typography.headlineLarge, color = cs.primary,
                fontWeight = if (DesignStudy.signal) FontWeight.Black else FontWeight.SemiBold)
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = cs.outlineVariant)
    }
}
