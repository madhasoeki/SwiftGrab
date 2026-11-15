package com.example.swiftgrab.data

import com.example.swiftgrab.data.prefs.DownloadPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPresetTest {

    @Test
    fun fromName_validNames_returnsExpectedPreset() {
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromName("BEST_VIDEO"))
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromName("DATA_SAVER"))
        assertEquals(DownloadPreset.AUDIO_ONLY, DownloadPreset.fromName("AUDIO_ONLY"))
    }

    @Test
    fun fromName_invalidOrNull_defaultsToBestVideo() {
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromName(null))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromName("UNKNOWN_PRESET"))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromName(""))
    }

    @Test
    fun fromTask_audioOnlyTrue_returnsAudioOnly() {
        assertEquals(DownloadPreset.AUDIO_ONLY, DownloadPreset.fromTask("MP4 Terbaik", audioOnly = true))
        assertEquals(DownloadPreset.AUDIO_ONLY, DownloadPreset.fromTask(null, audioOnly = true))
    }

    @Test
    fun fromTask_720pLabel_returnsDataSaver() {
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromTask("MP4 720p", audioOnly = false))
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromTask("720p HD", audioOnly = false))
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromTask("480p", audioOnly = false))
    }

    @Test
    fun fromTask_otherLabels_returnsBestVideo() {
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromTask("MP4 Terbaik", audioOnly = false))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromTask("1080p 60fps", audioOnly = false))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromTask(null, audioOnly = false))
    }

    @Test
    fun presetProperties_areConsistent() {
        assertFalse(DownloadPreset.BEST_VIDEO.audioOnly)
        assertFalse(DownloadPreset.DATA_SAVER.audioOnly)
        assertTrue(DownloadPreset.AUDIO_ONLY.audioOnly)

        assertTrue(DownloadPreset.BEST_VIDEO.formatSelector?.contains("bestvideo") == true)
        assertTrue(DownloadPreset.DATA_SAVER.formatSelector?.contains("720") == true)
        assertNull(DownloadPreset.AUDIO_ONLY.formatSelector)
    }
}
