package com.example.swiftgrab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.swiftgrab.R
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.ui.theme.BrandCloudWhite
import com.example.swiftgrab.ui.theme.BrandEmberGlow
import com.example.swiftgrab.ui.theme.BrandObsidianInk
import com.example.swiftgrab.ui.theme.BrandSignalEmber
import com.example.swiftgrab.ui.theme.DarkOutline
import com.example.swiftgrab.ui.theme.SwiftGrabTheme

private const val WORDMARK_ASPECT_RATIO = 604f / 98f

/**
 * Dua-lapis simbol resmi SwiftGrab ("Meteor Catch"): busur penangkap dan komet 45°
 * yang menyesuaikan warna otomatis terhadap latar terang maupun gelap.
 */
@Composable
fun SwiftGrabSymbol(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    onDarkSurface: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
) {
    val arcColor = if (onDarkSurface) BrandCloudWhite else BrandObsidianInk
    val meteorColor = if (onDarkSurface) BrandEmberGlow else BrandSignalEmber

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_swiftgrab_symbol_arc),
            contentDescription = null,
            colorFilter = ColorFilter.tint(arcColor),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(id = R.drawable.ic_swiftgrab_symbol_meteor),
            contentDescription = null,
            colorFilter = ColorFilter.tint(meteorColor),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Ubin ikon aplikasi resmi berlatar Obsidian Ink (#111318) dengan border halus pada tema gelap
 * agar tetap terpisah tegas dari permukaan #0E1015.
 */
@Composable
fun SwiftGrabAppIconTile(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val borderColor = if (isDarkBackground) DarkOutline else Color.Transparent

    Surface(
        shape = RoundedCornerShape(size * 0.226f),
        color = BrandObsidianInk,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            SwiftGrabSymbol(
                size = size * 0.72f,
                onDarkSurface = true
            )
        }
    }
}

/**
 * Wordmark vektor resmi SwiftGrab (Montserrat Bold/Medium ber-kerning optis) dengan rasio aspek terkunci.
 */
@Composable
fun SwiftGrabWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Image(
        painter = painterResource(id = R.drawable.ic_swiftgrab_wordmark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(
            width = height * WORDMARK_ASPECT_RATIO,
            height = height
        )
    )
}

/**
 * Lockup horizontal resmi (Simbol + Wordmark) dengan semantik aksesibilitas heading tergabung.
 */
@Composable
fun SwiftGrabHorizontalLockup(
    modifier: Modifier = Modifier,
    symbolSize: Dp = 24.dp,
    wordmarkHeight: Dp = 16.dp,
    appName: String = stringResource(R.string.app_name)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {
            text = AnnotatedString(appName)
            heading()
        }
    ) {
        SwiftGrabSymbol(size = symbolSize)
        Spacer(modifier = Modifier.width(8.dp))
        SwiftGrabWordmark(height = wordmarkHeight)
    }
}

/**
 * Kartu baris pilihan preset unduhan ber-radio bersama untuk QuickFormatSheet dan SettingsBottomSheet.
 */
@Composable
fun SwiftGrabPresetOptionCard(
    preset: DownloadPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (preset) {
        DownloadPreset.BEST_VIDEO -> Icons.Rounded.HighQuality
        DownloadPreset.DATA_SAVER -> Icons.Rounded.Savings
        DownloadPreset.AUDIO_ONLY -> Icons.Rounded.Audiotrack
    }
    val cardShape = MaterialTheme.shapes.medium
    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderStroke = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val wellColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = cardShape,
        color = cardColor,
        border = borderStroke,
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(wellColor)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Preview(name = "BrandMark Light", showBackground = true)
@Composable
private fun BrandMarkLightPreview() {
    SwiftGrabTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                SwiftGrabHorizontalLockup()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwiftGrabAppIconTile()
                    SwiftGrabSymbol(size = 36.dp)
                    SwiftGrabWordmark(height = 18.dp)
                }
            }
        }
    }
}

@Preview(name = "BrandMark Dark", showBackground = true)
@Composable
private fun BrandMarkDarkPreview() {
    SwiftGrabTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                SwiftGrabHorizontalLockup()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwiftGrabAppIconTile()
                    SwiftGrabSymbol(size = 36.dp)
                    SwiftGrabWordmark(height = 18.dp)
                }
            }
        }
    }
}

@Preview(name = "SwiftGrabPresetOptionCard Light", showBackground = true)
@Composable
private fun SwiftGrabPresetOptionCardLightPreview() {
    SwiftGrabTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.BEST_VIDEO,
                    isSelected = true,
                    onClick = {}
                )
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.DATA_SAVER,
                    isSelected = false,
                    onClick = {}
                )
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.AUDIO_ONLY,
                    isSelected = false,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "SwiftGrabPresetOptionCard Dark", showBackground = true)
@Composable
private fun SwiftGrabPresetOptionCardDarkPreview() {
    SwiftGrabTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.BEST_VIDEO,
                    isSelected = true,
                    onClick = {}
                )
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.DATA_SAVER,
                    isSelected = false,
                    onClick = {}
                )
                SwiftGrabPresetOptionCard(
                    preset = DownloadPreset.AUDIO_ONLY,
                    isSelected = false,
                    onClick = {}
                )
            }
        }
    }
}
