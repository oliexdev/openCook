package com.food.opencook

import com.food.opencook.sync.Crdt
import com.food.opencook.sync.Hlc
import com.food.opencook.sync.MaterializedStore
import com.food.opencook.sync.Merkle
import com.food.opencook.sync.MerkleTrie
import com.food.opencook.sync.Message
import com.food.opencook.sync.unsignedHash
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Verifies the Kotlin engine against the same vectors the Python engine uses. */
class SharedVectorsTest {

    private class Store : MaterializedStore {
        private val fields = HashMap<Triple<String, String, String>, Pair<String, String>>()
        override fun fieldClock(dataset: String, rowId: String, column: String) =
            fields[Triple(dataset, rowId, column)]?.second
        override fun applyField(dataset: String, rowId: String, column: String, value: String, timestamp: String) {
            fields[Triple(dataset, rowId, column)] = value to timestamp
        }
        fun value(dataset: String, rowId: String, column: String) =
            fields[Triple(dataset, rowId, column)]?.first
    }

    private val root = Json.parseToJsonElement(findVectors().readText()).jsonObject

    @Test
    fun packVectors() {
        root["pack"]!!.jsonArray.forEach { case ->
            val o = case.jsonObject
            val hlc = Hlc(o["millis"]!!.jsonPrimitive.long, o["counter"]!!.jsonPrimitive.int, o["node"]!!.jsonPrimitive.content)
            assertEquals(o["packed"]!!.jsonPrimitive.content, hlc.pack())
        }
    }

    @Test
    fun orderVectors() {
        root["order"]!!.jsonArray.forEach { case ->
            val o = case.jsonObject
            val a = o["a"]!!.jsonPrimitive.content
            val b = o["b"]!!.jsonPrimitive.content
            assertEquals(o["cmp"]!!.jsonPrimitive.int, Integer.signum(a.compareTo(b)))
        }
    }

    @Test
    fun mergeVectors() {
        root["merge"]!!.jsonArray.forEach { case ->
            val o = case.jsonObject
            val messages = o["messages"]!!.jsonArray.map { m ->
                val mo = m.jsonObject
                Message(
                    timestamp = mo["timestamp"]!!.jsonPrimitive.content,
                    dataset = mo["dataset"]!!.jsonPrimitive.content,
                    rowId = mo["rowId"]!!.jsonPrimitive.content,
                    column = mo["column"]!!.jsonPrimitive.content,
                    value = mo["value"]!!.jsonPrimitive.content,
                )
            }
            val store = Store()
            Crdt.applyAll(store, messages)
            o["expected"]!!.jsonObject.forEach { (key, expected) ->
                val (d, r, c) = key.split("|")
                assertEquals("vector '${o["name"]?.jsonPrimitive?.content}' key $key",
                    expected.jsonPrimitive.content, store.value(d, r, c))
            }
        }
    }

    @Test
    fun merkleBuildVectors() {
        root["merkle"]!!.jsonObject["build"]!!.jsonArray.forEach { case ->
            val o = case.jsonObject
            val timestamps = o["timestamps"]!!.jsonArray.map { it.jsonPrimitive.content }
            val trie = MerkleTrie.build(timestamps)
            assertEquals(o["rootHash"]!!.jsonPrimitive.long, trie.unsignedHash())
        }
    }

    @Test
    fun merkleDiffVectors() {
        root["merkle"]!!.jsonObject["diff"]!!.jsonArray.forEach { case ->
            val o = case.jsonObject
            val a: Merkle = MerkleTrie.build(o["a"]!!.jsonArray.map { it.jsonPrimitive.content })
            val b: Merkle = MerkleTrie.build(o["b"]!!.jsonArray.map { it.jsonPrimitive.content })
            val expected = o["expectedMillis"]!!.jsonPrimitive.let { if (it.content == "null") null else it.long }
            assertEquals(expected, MerkleTrie.diff(a, b))
        }
    }

    private fun findVectors(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(6) {
            val f = File(dir, "server/tests/fixtures/sync-vectors.json")
            if (f.exists()) return f
            dir = dir?.parentFile
        }
        error("server/tests/fixtures/sync-vectors.json not found from ${System.getProperty("user.dir")}")
    }
}
