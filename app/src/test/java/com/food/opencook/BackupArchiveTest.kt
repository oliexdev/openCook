/*
 *  openCook
 *  Copyright (C) 2026 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.food.opencook

import com.food.opencook.data.backup.BackupCounts
import com.food.opencook.data.backup.BackupFormat
import com.food.opencook.data.backup.BackupManifest
import com.food.opencook.data.backup.BackupReader
import com.food.opencook.data.backup.BackupRejected
import com.food.opencook.data.backup.BackupRejectedException
import com.food.opencook.data.recipeimport.RecipeBundle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Archive-level behaviour: what the reader accepts, and what it refuses. */
class BackupArchiveTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val reader = BackupReader(json)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun manifestBytes(version: Int = BackupFormat.VERSION) = json.encodeToString(
        BackupManifest.serializer(),
        BackupManifest(
            formatVersion = version,
            createdAt = "2026-07-19T14:03:11Z",
            householdName = "Familie Meier",
            counts = BackupCounts(recipes = 2, images = 1),
        ),
    ).toByteArray()

    @Test
    fun readsTheManifest() {
        val bytes = zip(
            BackupFormat.RECIPES to "[]".toByteArray(),
            BackupFormat.MANIFEST to manifestBytes(),
        )
        val manifest = reader.readManifest(ByteArrayInputStream(bytes))
        assertEquals("Familie Meier", manifest.householdName)
        assertEquals(2, manifest.counts.recipes)
    }

    @Test
    fun refusesAnArchiveFromANewerVersion() {
        val bytes = zip(BackupFormat.MANIFEST to manifestBytes(version = BackupFormat.VERSION + 1))
        val error = assertThrows(BackupRejectedException::class.java) {
            reader.readManifest(ByteArrayInputStream(bytes))
        }
        assertTrue(error.reason is BackupRejected.TooNew)
    }

    @Test
    fun refusesSomethingThatIsNotABackup() {
        val error = assertThrows(BackupRejectedException::class.java) {
            reader.readManifest(ByteArrayInputStream("not a zip at all".toByteArray()))
        }
        assertEquals(BackupRejected.NotABackup, error.reason)
    }

    @Test
    fun rejectsPathTraversalEntryNames() {
        assertFalse(BackupFormat.isAllowedEntry("../../etc/passwd"))
        assertFalse(BackupFormat.isAllowedEntry("images/../../evil.jpg"))
        assertFalse(BackupFormat.isAllowedEntry("/etc/passwd"))
        assertFalse(BackupFormat.isAllowedEntry("""images\evil.jpg"""))
        assertFalse(BackupFormat.isAllowedEntry("images/nested/evil.jpg"))
        assertFalse(BackupFormat.isAllowedEntry("evil.sh"))
    }

    @Test
    fun allowsTheKnownLayout() {
        assertTrue(BackupFormat.isAllowedEntry(BackupFormat.MANIFEST))
        assertTrue(BackupFormat.isAllowedEntry(BackupFormat.RECIPES))
        assertTrue(BackupFormat.isAllowedEntry(BackupFormat.SHOPPING))
        assertTrue(BackupFormat.isAllowedEntry("images/recipe-1.jpg"))
    }

    @Test
    fun traversalEntryAbortsTheWalk() = runTest {
        val bytes = zip(
            BackupFormat.MANIFEST to manifestBytes(),
            "../evil.jpg" to byteArrayOf(1, 2, 3),
        )
        assertThrows(BackupRejectedException::class.java) {
            kotlinx.coroutines.runBlocking {
                reader.forEachEntry(ByteArrayInputStream(bytes)) { _, _ -> }
            }
        }
    }

    @Test
    fun unknownEntriesAreSkippedNotFatal() = runTest {
        val bytes = zip(
            BackupFormat.MANIFEST to manifestBytes(),
            "extra/futureThing.txt" to "hello".toByteArray(),
            BackupFormat.RECIPES to "[]".toByteArray(),
        )
        val seen = mutableListOf<String>()
        reader.forEachEntry(ByteArrayInputStream(bytes)) { name, stream ->
            stream.readBytes()
            seen += name
        }
        assertEquals(listOf(BackupFormat.MANIFEST, BackupFormat.RECIPES), seen)
    }

    /**
     * The archive layout is deliberately the one [RecipeBundle] already imports, so a
     * backup can also be fed to the ordinary "import recipes" button (lists ignored).
     */
    @Test
    fun aBackupIsAlsoAReadableRecipeBundle() {
        val recipes = """[{"name":"Pfannkuchen","recipeIngredient":["250 g Mehl"],"image":["images/recipe-1.jpg"]}]"""
        val bytes = zip(
            BackupFormat.MANIFEST to manifestBytes(),
            BackupFormat.RECIPES to recipes.toByteArray(),
            "images/recipe-1.jpg" to byteArrayOf(9, 8, 7),
        )
        val imported = RecipeBundle.read(bytes, json)
        assertEquals(1, imported.size)
        assertEquals("Pfannkuchen", imported[0].dto.name)
        assertArrayEqualsOrNull(byteArrayOf(9, 8, 7), imported[0].imageBytes)
    }

    private fun assertArrayEqualsOrNull(expected: ByteArray, actual: ByteArray?) {
        assertTrue("image bytes were not resolved from the archive", actual != null)
        assertEquals(expected.toList(), actual!!.toList())
    }
}
