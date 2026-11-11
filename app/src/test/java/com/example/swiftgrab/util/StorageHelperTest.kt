package com.example.swiftgrab.util

import com.example.swiftgrab.data.prefs.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolvePathFromDocumentId resolves primary storage paths correctly`() {
        val primaryRoot = tempFolder.newFolder("emulated_0")

        val pathDownload = StorageHelper.resolvePathFromDocumentId(
            docId = "primary:Download/SwiftGrab",
            primaryStorageRoot = primaryRoot
        )
        assertEquals(File(primaryRoot, "Download/SwiftGrab").absolutePath, pathDownload)

        val pathRoot = StorageHelper.resolvePathFromDocumentId(
            docId = "primary:",
            primaryStorageRoot = primaryRoot
        )
        assertEquals(primaryRoot.absolutePath, pathRoot)
    }

    @Test
    fun `resolvePathFromDocumentId handles percent-encoded characters`() {
        val primaryRoot = tempFolder.newFolder("emulated_0_percent")

        val pathWithEncodedSlash = StorageHelper.resolvePathFromDocumentId(
            docId = "primary:Download%2FSwiftGrab%20Folder",
            primaryStorageRoot = primaryRoot
        )
        assertEquals(File(primaryRoot, "Download/SwiftGrab Folder").absolutePath, pathWithEncodedSlash)

        val pathWithPlus = StorageHelper.resolvePathFromDocumentId(
            docId = "primary:Music%2FRock+Metal",
            primaryStorageRoot = primaryRoot
        )
        assertEquals(File(primaryRoot, "Music/Rock+Metal").absolutePath, pathWithPlus)
    }

    @Test
    fun `resolvePathFromDocumentId resolves volume ID via external storage dirs`() {
        val primaryRoot = tempFolder.newFolder("primary_root")
        val sdCardRoot = tempFolder.newFolder("storage_9876-5432")
        val appSpecificSdDir = File(sdCardRoot, "Android/data/com.example.swiftgrab/files").apply { mkdirs() }

        val resolvedSdPath = StorageHelper.resolvePathFromDocumentId(
            docId = "9876-5432:Movies/Saved",
            primaryStorageRoot = primaryRoot,
            externalStorageDirs = listOf(appSpecificSdDir)
        )
        assertEquals(File(sdCardRoot, "Movies/Saved").absolutePath, resolvedSdPath)
    }

    @Test
    fun `resolvePathFromDocumentId returns null for invalid non-matching document IDs`() {
        val primaryRoot = tempFolder.newFolder("dummy_root")

        val cloudDoc = StorageHelper.resolvePathFromDocumentId(
            docId = "com.google.android.apps.docs.storage/doc123",
            primaryStorageRoot = primaryRoot
        )
        assertNull(cloudDoc)
    }

    @Test
    fun `decodePercent decodes correctly while preserving plus characters`() {
        assertEquals("Hello World", StorageHelper.decodePercent("Hello%20World"))
        assertEquals("A+B/C", StorageHelper.decodePercent("A+B%2FC"))
        assertEquals("SwiftGrab/1080p", StorageHelper.decodePercent("SwiftGrab%2F1080p"))
    }

    @Test
    fun `isExternalStorageAuthority allows only external storage provider`() {
        assertTrue(StorageHelper.isExternalStorageAuthority(StorageHelper.EXTERNAL_STORAGE_AUTHORITY))
        assertFalse(StorageHelper.isExternalStorageAuthority("com.google.android.apps.docs.storage"))
        assertFalse(StorageHelper.isExternalStorageAuthority("com.android.providers.downloads.documents"))
        assertFalse(StorageHelper.isExternalStorageAuthority(null))
    }

    @Test
    fun `needsLegacyWritePermissionForApi enforces runtime check only below API 29 without SAF`() {
        assertTrue(
            StorageHelper.needsLegacyWritePermissionForApi(
                sdkInt = 28,
                hasCustomSafUri = false,
                hasWritePermission = false
            )
        )
        assertFalse(
            StorageHelper.needsLegacyWritePermissionForApi(
                sdkInt = 28,
                hasCustomSafUri = false,
                hasWritePermission = true
            )
        )
        assertFalse(
            StorageHelper.needsLegacyWritePermissionForApi(
                sdkInt = 28,
                hasCustomSafUri = true,
                hasWritePermission = false
            )
        )
        assertFalse(
            StorageHelper.needsLegacyWritePermissionForApi(
                sdkInt = 29,
                hasCustomSafUri = false,
                hasWritePermission = false
            )
        )
    }

    @Test
    fun `shouldReleasePreviousUri skips unchanged or absent grants and releases replaced or cleared grants`() {
        val uriA = "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        val uriB = "content://com.android.externalstorage.documents/tree/primary%3AMovies"

        assertFalse(UserPreferences.shouldReleasePreviousUri(null, uriA))
        assertFalse(UserPreferences.shouldReleasePreviousUri("", uriA))
        assertFalse(UserPreferences.shouldReleasePreviousUri(uriA, uriA))
        assertTrue(UserPreferences.shouldReleasePreviousUri(uriA, uriB))
        assertTrue(UserPreferences.shouldReleasePreviousUri(uriA, null))
    }
}
