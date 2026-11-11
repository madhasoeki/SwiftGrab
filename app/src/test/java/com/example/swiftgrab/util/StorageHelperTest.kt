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
import java.net.URI
import java.net.URLDecoder

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
    fun `resolvePathFromDocumentId preserves already decoded docId including literal percent sequences`() {
        val primaryRoot = tempFolder.newFolder("emulated_0_literal")

        // Model picker URI returned by OpenDocumentTree:
        // content://com.android.externalstorage.documents/tree/primary%3ADownload%2F100%252Ftest
        // DocumentsContract.getTreeDocumentId() decodes the path segment once into "primary:Download/100%2Ftest"
        val pickerRawSegment = URI(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2F100%252Ftest"
        ).rawPath.substringAfterLast('/')
        val docIdFromDocumentsContract = URLDecoder.decode(pickerRawSegment.replace("+", "%2B"), Charsets.UTF_8.name())
        assertEquals("primary:Download/100%2Ftest", docIdFromDocumentsContract)

        val resolvedLiteralFolder = StorageHelper.resolvePathFromDocumentId(
            docId = docIdFromDocumentsContract,
            primaryStorageRoot = primaryRoot
        )
        assertEquals(File(primaryRoot, "Download/100%2Ftest").absolutePath, resolvedLiteralFolder)

        val pathWithPlus = StorageHelper.resolvePathFromDocumentId(
            docId = "primary:Music/Rock+Metal",
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
