package com.encore.bedrock.leveldb.chunk

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtIO
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Helpers for constructing Bedrock Edition LevelDB chunk keys and payloads.
 */
object BedrockChunkHelper {

    const val TAG_SUBCHUNK_PREFIX: Byte = 0x2F
    const val TAG_DATA_2D: Byte = 0x2D
    const val TAG_VERSION: Byte = 0x76
    const val TAG_BLOCK_ENTITY: Byte = 0x31

    const val OVERWORLD_VERSION_VALUE: Byte = 40

    @JvmOverloads
    fun createChunkKey(chunkX: Int, chunkZ: Int, tag: Byte, dimensionId: Int = 0): ByteArray {
        return if (dimensionId == 0) {
            val key = ByteArray(9)
            writeIntLE(key, 0, chunkX)
            writeIntLE(key, 4, chunkZ)
            key[8] = tag
            key
        } else {
            val key = ByteArray(13)
            writeIntLE(key, 0, chunkX)
            writeIntLE(key, 4, chunkZ)
            writeIntLE(key, 8, dimensionId)
            key[12] = tag
            key
        }
    }

    @JvmOverloads
    fun createSubChunkKey(chunkX: Int, chunkZ: Int, subChunkY: Int, dimensionId: Int = 0): ByteArray {
        return if (dimensionId == 0) {
            val key = ByteArray(10)
            writeIntLE(key, 0, chunkX)
            writeIntLE(key, 4, chunkZ)
            key[8] = TAG_SUBCHUNK_PREFIX
            key[9] = subChunkY.toByte()
            key
        } else {
            val key = ByteArray(14)
            writeIntLE(key, 0, chunkX)
            writeIntLE(key, 4, chunkZ)
            writeIntLE(key, 8, dimensionId)
            key[12] = TAG_SUBCHUNK_PREFIX
            key[13] = subChunkY.toByte()
            key
        }
    }

    @JvmOverloads
    fun createChunkVersionKey(chunkX: Int, chunkZ: Int, dimensionId: Int = 0): ByteArray =
        createChunkKey(chunkX, chunkZ, TAG_VERSION, dimensionId)

    @JvmOverloads
    fun create2DDataKey(chunkX: Int, chunkZ: Int, dimensionId: Int = 0): ByteArray =
        createChunkKey(chunkX, chunkZ, TAG_DATA_2D, dimensionId)

    @JvmOverloads
    fun createBlockEntityKey(chunkX: Int, chunkZ: Int, dimensionId: Int = 0): ByteArray =
        createChunkKey(chunkX, chunkZ, TAG_BLOCK_ENTITY, dimensionId)

    fun createDefault2DData(surfaceHeight: Short = -60): ByteArray {
        val data = ByteArray(512 + 256)

        for (i in 0 until 256) {
            data[i * 2] = (surfaceHeight.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = ((surfaceHeight.toInt() ushr 8) and 0xFF).toByte()
        }

        for (i in 512 until 512 + 256) {
            data[i] = 1
        }

        return data
    }

    fun serializeBlockEntities(entities: List<NbtCompound>): ByteArray {
        if (entities.isEmpty()) return ByteArray(0)
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        for (entity in entities) {
            NbtIO.writeNbtFile(dos, NbtFile("", entity), compressed = false, littleEndian = true)
        }
        dos.flush()
        return baos.toByteArray()
    }

    private fun writeIntLE(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        array[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        array[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
