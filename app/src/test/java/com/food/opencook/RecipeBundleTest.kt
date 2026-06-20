package com.food.opencook

import com.food.opencook.data.recipeimport.RecipeBundle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RecipeBundleTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private fun dataUri(bytes: ByteArray) = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)

    @Test
    fun embeddedDataUriIsDecoded() {
        val img = byteArrayOf(1, 2, 3, 4, 5)
        val out = RecipeBundle.read(
            """{"name":"A","recipeIngredient":["1 Ei"],"image":"${dataUri(img)}"}""".toByteArray(), json,
        )
        assertEquals(1, out.size)
        assertArrayEquals(img, out[0].imageBytes)
        assertNull(out[0].imageUrl)
    }

    @Test
    fun httpImageBecomesUrlNotBytes() {
        val out = RecipeBundle.read(
            """{"name":"A","recipeIngredient":["1 Ei"],"image":"https://host/i.jpg"}""".toByteArray(), json,
        )
        assertNull(out[0].imageBytes)
        assertEquals("https://host/i.jpg", out[0].imageUrl)
    }

    @Test
    fun multipleRecipesEachKeepTheirImage() {
        val out = RecipeBundle.read(
            """[{"name":"A","recipeIngredient":["x"],"image":"${dataUri(byteArrayOf(1))}"},
                {"name":"B","recipeIngredient":["y"],"image":"${dataUri(byteArrayOf(2))}"}]""".toByteArray(),
            json,
        )
        assertEquals(2, out.size)
        assertArrayEquals(byteArrayOf(1), out[0].imageBytes)
        assertArrayEquals(byteArrayOf(2), out[1].imageBytes)
    }

    @Test
    fun plainJsonWithoutImageHasNoBytes() {
        val out = RecipeBundle.read("""{"name":"A","recipeIngredient":["x"]}""".toByteArray(), json)
        assertNull(out[0].imageBytes)
        assertNull(out[0].imageUrl)
    }

    @Test
    fun zipBundleResolvesRelativeImagePath() {
        val dish = byteArrayOf(9, 8, 7, 6)
        val zip = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("recipes.json"))
                z.write("""[{"name":"Gericht","recipeIngredient":["1 Ei"],"image":"images/dish.jpg"}]""".toByteArray())
                z.closeEntry()
                z.putNextEntry(ZipEntry("images/dish.jpg")); z.write(dish); z.closeEntry()
            }
        }.toByteArray()
        val out = RecipeBundle.read(zip, json)
        assertEquals(1, out.size)
        assertEquals("Gericht", out[0].dto.name)
        assertArrayEquals(dish, out[0].imageBytes)
        assertNull(out[0].imageUrl)
    }
}
