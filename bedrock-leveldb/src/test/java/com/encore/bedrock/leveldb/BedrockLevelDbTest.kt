package com.encore.bedrock.leveldb

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtString
import com.encore.bedrock.leveldb.model.BedrockDimension
import com.encore.bedrock.leveldb.model.BedrockTagType
import com.encore.bedrock.leveldb.model.LevelDbCategory
import com.encore.bedrock.leveldb.nbt.BedrockNbtCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BedrockLevelDbTest {

    @Test
    fun testKeyParserLocalPlayer() {
        val key = "~local_player".toByteArray(Charsets.UTF_8)
        val record = BedrockKeyParser.parse(key, 128, byteArrayOf(0x0A))
        assertEquals(LevelDbCategory.PLAYER, record.category)
        assertTrue(record.isNbt)
    }

    @Test
    fun testKeyParserChunkBlockEntity() {
        val key = byteArrayOf(
            0x00, 0x00, 0x00, 0x00, // ChunkX: 0
            0x00, 0x00, 0x00, 0x00, // ChunkZ: 0
            0x31                    // Tag: BLOCK_ENTITY (0x31)
        )
        val record = BedrockKeyParser.parse(key, 256, byteArrayOf(0x0A))
        assertEquals(LevelDbCategory.BLOCK_ENTITY, record.category)
        assertEquals(BedrockTagType.BLOCK_ENTITY, record.tagType)
        assertEquals(0, record.chunkX)
        assertEquals(0, record.chunkZ)
        assertTrue(record.hasMultipleCompounds)
    }

    @Test
    fun testNbtEncodeDecode() {
        val compound = NbtCompound()
        compound["Author"] = NbtString("Encore")
        compound["Game"] = NbtString("Minecraft Bedrock")
        val nbtFile = NbtFile("Root", compound)

        val encodedBytes = BedrockNbtCodec.encode(nbtFile)
        assertNotNull(encodedBytes)
        assertTrue(encodedBytes.isNotEmpty())

        val decoded = BedrockNbtCodec.decode(encodedBytes)
        val root = decoded.tag as NbtCompound
        assertEquals("Encore", (root["Author"] as? NbtString)?.value)
        assertEquals("Minecraft Bedrock", (root["Game"] as? NbtString)?.value)
    }

    @Test
    fun testDatabaseCreateReadWrite(@TempDir tempDir: File) {
        val dbFolder = File(tempDir, "test_db")
        val bedrockDb = BedrockLevelDb(dbFolder)

        val key = "~local_player".toByteArray(Charsets.UTF_8)
        val value = "PlayerNbtBinary".toByteArray(Charsets.UTF_8)

        bedrockDb.open(createIfMissing = true)
        bedrockDb.put(key, value)

        val readBytes = bedrockDb.get(key)
        assertNotNull(readBytes)
        assertArrayEquals(value, readBytes)

        val records = bedrockDb.getAllRecords()
        assertEquals(1, records.size)
        assertEquals(LevelDbCategory.PLAYER, records[0].category)

        bedrockDb.close()
    }
}