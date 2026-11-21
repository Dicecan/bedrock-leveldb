package com.encore.bedrock.leveldb

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtInt
import br.com.gamemods.nbtmanipulator.NbtIO
import br.com.gamemods.nbtmanipulator.NbtString
import com.encore.bedrock.leveldb.chunk.BedrockBlockState
import com.encore.bedrock.leveldb.chunk.BedrockChunkHelper
import com.encore.bedrock.leveldb.chunk.BitArray
import com.encore.bedrock.leveldb.chunk.SubChunkEncoder
import com.encore.bedrock.leveldb.model.BedrockDimension
import com.encore.bedrock.leveldb.model.BedrockTagType
import com.encore.bedrock.leveldb.model.LevelDbCategory
import com.encore.bedrock.leveldb.nbt.BedrockNbtCodec
import org.iq80.leveldb.CompressionType
import org.iq80.leveldb.Options
import org.iq80.leveldb.impl.DbImpl
import org.iq80.leveldb.util.Slices
import org.iq80.leveldb.util.ZLib
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        val key = BedrockChunkHelper.createBlockEntityKey(0, 0)
        val record = BedrockKeyParser.parse(key, 256, byteArrayOf(0x0A))
        assertEquals(LevelDbCategory.BLOCK_ENTITY, record.category)
        assertEquals(BedrockTagType.BLOCK_ENTITY, record.tagType)
        assertEquals(0, record.chunkX)
        assertEquals(0, record.chunkZ)
        assertTrue(record.hasMultipleCompounds)
    }

    @Test
    fun testKeyParserModernVersionAndNetherSubChunk() {
        val versionKey = BedrockChunkHelper.createChunkVersionKey(-3, 7, dimensionId = 0)
        val versionRecord = BedrockKeyParser.parse(versionKey, 1)
        assertEquals(LevelDbCategory.CHUNK, versionRecord.category)
        assertEquals(BedrockTagType.VERSION_MODERN, versionRecord.tagType)
        assertEquals(-3, versionRecord.chunkX)
        assertEquals(7, versionRecord.chunkZ)
        assertEquals(BedrockDimension.OVERWORLD, versionRecord.dimension)

        val netherSubKey = BedrockChunkHelper.createSubChunkKey(2, -5, -1, dimensionId = 1)
        val netherRecord = BedrockKeyParser.parse(netherSubKey, 512)
        assertEquals(LevelDbCategory.CHUNK, netherRecord.category)
        assertEquals(BedrockTagType.SUB_CHUNK_PREFIX, netherRecord.tagType)
        assertEquals(BedrockDimension.NETHER, netherRecord.dimension)
        assertEquals(2, netherRecord.chunkX)
        assertEquals(-5, netherRecord.chunkZ)
        assertEquals(-1, netherRecord.subChunkIndex)
    }

    @Test
    fun testNbtEncodeDecodeSingleAndMultipleWithZeroPadding() {
        val compound = NbtCompound()
        compound["Author"] = NbtString("Encore")
        compound["Game"] = NbtString("Minecraft Bedrock")
        val nbtFile = NbtFile("Root", compound)

        val encodedBytes = BedrockNbtCodec.encode(nbtFile)
        assertTrue(encodedBytes.isNotEmpty())

        val decoded = BedrockNbtCodec.decode(encodedBytes)
        assertEquals("Root", decoded.name)
        val root = decoded.tag as NbtCompound
        assertEquals("Encore", (root["Author"] as? NbtString)?.value)
        assertEquals("Minecraft Bedrock", (root["Game"] as? NbtString)?.value)

        // Multiple roots with trailing zero padding
        val multiOut = ByteArrayOutputStream()
        val multiDos = java.io.DataOutputStream(multiOut)
        NbtIO.writeNbtFile(multiDos, NbtFile("first", NbtInt(1)), compressed = false, littleEndian = true)
        NbtIO.writeNbtFile(multiDos, NbtFile("second", NbtInt(2)), compressed = false, littleEndian = true)
        multiDos.flush()
        val multiWithPadding = multiOut.toByteArray() + ByteArray(5)

        val loadedMulti = BedrockNbtCodec.decode(multiWithPadding, isMultiple = true)
        val multiRoot = loadedMulti.tag as NbtCompound
        assertEquals(2, multiRoot.size)
        assertEquals(1, (multiRoot["first"] as NbtInt).value)
        assertEquals(2, (multiRoot["second"] as NbtInt).value)
    }

    @Test
    fun testNbtRejectsCorruptTailAndEmptyPayload() {
        val original = NbtFile("unused", NbtCompound("value" to NbtInt(1)))
        val corrupt = BedrockNbtCodec.encode(original, wasMultiple = true) + byteArrayOf(0x7F)
        assertThrows(IOException::class.java) {
            BedrockNbtCodec.decode(corrupt, isMultiple = true)
        }

        assertThrows(EOFException::class.java) {
            BedrockNbtCodec.decode(ByteArray(0), isMultiple = true)
        }
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

    @Test
    fun testChunkDeletionKeepsUnknownAndCoordinateOnlyKeys(@TempDir tempDir: File) {
        val folder = File(tempDir, "chunk_delete_db")
        val coordOnly = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(4).putInt(-7).array()
        val unknownTagKey = coordOnly + byteArrayOf(0x7E)
        val blockEntityKey = BedrockChunkHelper.createBlockEntityKey(4, -7)

        BedrockLevelDb(folder).use { db ->
            db.open(createIfMissing = true)
            db.put(blockEntityKey, byteArrayOf(1))
            db.put(unknownTagKey, byteArrayOf(2))
            db.put(coordOnly, byteArrayOf(3))

            assertEquals(1, db.deleteChunk(4, -7, dimensionId = 0))
            assertNull(db.get(blockEntityKey))
            assertNotNull(db.get(unknownTagKey))
            assertNotNull(db.get(coordOnly))
        }
    }

    @Test
    fun testBulkScanningSummaryAndRangeDeletion(@TempDir tempDir: File) {
        val folder = File(tempDir, "bulk_scan_db")
        DbImpl(Options().apply { createIfMissing(true); writeBufferSize(4 * 1024) }, folder.absolutePath, SafeEnv()).use { database ->
            for (i in 0 until 200) {
                val cx = i % 20
                val cz = i / 20
                database.put(BedrockChunkHelper.createBlockEntityKey(cx, cz), ByteArray(512) { (it % 128).toByte() })
            }
        }

        BedrockLevelDb(folder).use { db ->
            val records = db.getAllRecords()
            assertEquals(200, records.size)

            val summary = db.getChunkCoordinateSummary(0)
            assertEquals(200, summary.populated.size)
            assertEquals(200, summary.blockEntities.size)

            // Keep only chunks in [0..4, 0..4] (25 chunks), delete the other 175
            val deleted = db.deleteChunksOutsideRange(0, 4, 0, 4, dimensionId = 0)
            assertEquals(175, deleted)
            assertEquals(25, db.getPopulatedChunkCoordinates(0).size)
        }
    }

    @Test
    fun testSafeEnvAvoidsMmapAndReopensSstables(@TempDir tempDir: File) {
        val env = SafeEnv()
        val ioFile = File(tempDir, "sample.ldb")
        val envFile = env.toFile(ioFile.absolutePath)
        val writable = env.newWritableFile(envFile)
        writable.append(Slices.wrappedBuffer("hello-leveldb".toByteArray()))
        writable.close()

        val randomInput = env.newRandomAccessFile(envFile)
        try {
            assertFalse(writable::class.java.simpleName.startsWith("MM"))
            assertFalse(randomInput::class.java.simpleName.startsWith("MM"))
            // Reading past EOF clamps cleanly instead of over-allocating
            val slice = randomInput.read(6, 100)
            assertEquals(7, slice.remaining())
        } finally {
            randomInput.close()
        }

        val dbDir = File(tempDir, "sstable_db")
        val options = Options().apply {
            createIfMissing(true)
            compressionType(CompressionType.ZLIB_RAW)
            writeBufferSize(64 * 1024)
        }
        val payload = ByteArray(1024) { (it % 251).toByte() }
        DbImpl(options, dbDir.absolutePath, SafeEnv()).use { db ->
            val batch = db.createWriteBatch()
            for (i in 0 until 100) {
                batch.put("actorprefix_${i.toString().padStart(4, '0')}".toByteArray(), payload)
            }
            db.write(batch)
            batch.close()
        }

        DbImpl(options, dbDir.absolutePath, SafeEnv()).use { reopened ->
            assertArrayEquals(payload, reopened.get("actorprefix_0000".toByteArray()))
        }
    }

    @Test
    fun testZLibRawAndStandardRoundtrip() {
        val original = "Hello Minecraft Bedrock LevelDB ZLib compression!".toByteArray(Charsets.UTF_8)
        for (raw in listOf(true, false)) {
            val buf = ByteArray(original.size * 2 + 1024)
            val len = ZLib.compress(original, 0, original.size, buf, 0, raw)
            val decompressed = ZLib.uncompress(ByteBuffer.wrap(buf, 0, len), raw)
            val out = ByteArray(decompressed.remaining())
            decompressed.get(out)
            assertArrayEquals(original, out)
        }
    }

    @Test
    fun testBitArrayAndSubChunkEncoder() {
        val bitArray = BitArray(bitsPerBlock = 3, size = 4096)
        bitArray[0] = 5
        bitArray[4095] = 7
        assertEquals(5, bitArray[0])
        assertEquals(7, bitArray[4095])
        assertEquals(1, BitArray.getOptimalBitsPerBlock(2))
        assertEquals(4, BitArray.getOptimalBitsPerBlock(16))

        val blocks = Array(SubChunkEncoder.SUBCHUNK_SIZE) { BedrockBlockState.AIR }
        blocks[SubChunkEncoder.getLocalIndex(0, 0, 0)] = BedrockBlockState.BEDROCK
        blocks[SubChunkEncoder.getLocalIndex(1, 2, 3)] = BedrockBlockState.DIRT

        val encoded = SubChunkEncoder.encode(blocks)
        assertTrue(encoded.size > 10)
        assertEquals(SubChunkEncoder.DEFAULT_STORAGE_VERSION, encoded[0])
        assertEquals(1.toByte(), encoded[1])
        // 3 palette entries -> optimal bitsPerBlock = 2 -> layerHeader = (2 << 1) = 4
        assertEquals(4.toByte(), encoded[2])
    }
}
