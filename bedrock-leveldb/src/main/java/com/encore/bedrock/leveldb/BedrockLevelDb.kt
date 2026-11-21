/*
 * Copyright (C) 2026 Dicecan
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.encore.bedrock.leveldb

import br.com.gamemods.nbtmanipulator.NbtFile
import com.encore.bedrock.leveldb.model.BedrockTagType
import com.encore.bedrock.leveldb.model.LevelDbRecord
import com.encore.bedrock.leveldb.nbt.BedrockNbtCodec
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.ReadOptions
import org.iq80.leveldb.WriteBatch
import java.io.Closeable
import java.io.File

class BedrockLevelDb(val dbFolder: File) : Closeable, AutoCloseable {

    data class ChunkCoordinateSummary(
        val populated: Set<Pair<Int, Int>>,
        val blockEntities: Set<Pair<Int, Int>>
    )

    private var db: DB? = null
    private val dbLock = Any()

    private val noCacheReadOptions = ReadOptions().apply {
        fillCache(false)
        verifyChecksums(false)
    }

    @JvmOverloads
    fun open(createIfMissing: Boolean = false, cacheSize: Long = 0L): DB {
        synchronized(dbLock) {
            if (db == null) {
                val options = Options().apply {
                    this.createIfMissing(createIfMissing)
                    this.cacheSize(cacheSize)
                }
                db = org.iq80.leveldb.impl.DbImpl(options, dbFolder.absolutePath, SafeEnv())
            }
            return db!!
        }
    }

    fun get(key: ByteArray): ByteArray? {
        return synchronized(dbLock) { open().get(key, noCacheReadOptions) }
    }

    fun getNbt(record: LevelDbRecord): NbtFile? {
        val bytes = get(record.key) ?: return null
        return BedrockNbtCodec.decode(bytes, record.hasMultipleCompounds)
    }

    fun put(key: ByteArray, value: ByteArray) {
        synchronized(dbLock) { open().put(key, value) }
    }

    fun putNbt(record: LevelDbRecord, nbtFile: NbtFile) {
        val bytes = BedrockNbtCodec.encode(nbtFile, record.hasMultipleCompounds)
        put(record.key, bytes)
    }

    fun delete(key: ByteArray) {
        synchronized(dbLock) { open().delete(key) }
    }

    fun getAllRecords(onProgress: ((scannedCount: Int) -> Unit)? = null): List<LevelDbRecord> {
        val list = mutableListOf<LevelDbRecord>()
        synchronized(dbLock) {
            val database = open()
            val iterator = database.iterator(noCacheReadOptions)
            var count = 0
            try {
                iterator.seekToFirst()
                while (iterator.hasNext()) {
                    val entry = iterator.next()

                    val key = entry.key.copyOf()
                    val value = entry.value
                    list.add(BedrockKeyParser.parse(key, value.size, value))
                    count++
                    if (count % 250 == 0) {
                        onProgress?.invoke(count)
                    }
                }
                onProgress?.invoke(count)
            } finally {
                iterator.close()
            }
        }
        return list
    }

    fun deleteChunk(chunkX: Int, chunkZ: Int, dimensionId: Int? = null): Int {
        return deleteChunkRecords { info ->
            BedrockKeyParser.isKnownChunkTag(info.tagByte) &&
                info.chunkX == chunkX && info.chunkZ == chunkZ &&
                (dimensionId == null || info.dimensionId == dimensionId)
        }
    }

    fun deleteChunks(chunkCoords: Collection<Pair<Int, Int>>, dimensionId: Int? = null): Int {
        val coordSet = chunkCoords.toSet()
        return deleteChunkRecords { info ->
            BedrockKeyParser.isKnownChunkTag(info.tagByte) &&
                coordSet.contains(Pair(info.chunkX, info.chunkZ)) &&
                (dimensionId == null || info.dimensionId == dimensionId)
        }
    }

    fun deleteChunksOutsideRange(
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int,
        dimensionId: Int? = null
    ): Int {
        return deleteChunkRecords { info ->
            BedrockKeyParser.isKnownChunkTag(info.tagByte) &&
                (dimensionId == null || info.dimensionId == dimensionId) &&
                (info.chunkX < minChunkX || info.chunkX > maxChunkX ||
                    info.chunkZ < minChunkZ || info.chunkZ > maxChunkZ)
        }
    }

    private fun deleteChunkRecords(predicate: (ChunkKeyInfo) -> Boolean): Int {
        synchronized(dbLock) {
            val database = open()
            val keys = mutableListOf<ByteArray>()
            val iterator = database.iterator(noCacheReadOptions)
            try {
                iterator.seekToFirst()
                while (iterator.hasNext()) {
                    val key = iterator.next().key
                    val info = BedrockKeyParser.parseChunkKey(key)
                    if (info != null && predicate(info)) {
                        keys += key.copyOf()
                    }
                }
            } finally {
                iterator.close()
            }

            if (keys.isEmpty()) return 0
            val batch = database.createWriteBatch()
            try {
                keys.forEach(batch::delete)
                database.write(batch)
            } finally {
                batch.close()
            }
            return keys.size
        }
    }

    fun getPopulatedChunkCoordinates(dimensionId: Int = 0): Set<Pair<Int, Int>> {
        return scanChunkCoordinates(dimensionId).populated
    }

    fun getBlockEntityChunkCoordinates(dimensionId: Int = 0): Set<Pair<Int, Int>> {
        return scanChunkCoordinates(dimensionId).blockEntities
    }

    fun getChunkCoordinateSummary(dimensionId: Int = 0): ChunkCoordinateSummary {
        return scanChunkCoordinates(dimensionId)
    }

    private fun scanChunkCoordinates(dimensionId: Int): ChunkCoordinateSummary {
        val populated = mutableSetOf<Pair<Int, Int>>()
        val blockEntities = mutableSetOf<Pair<Int, Int>>()
        synchronized(dbLock) {
            val iterator = open().iterator(noCacheReadOptions)
            try {
                iterator.seekToFirst()
                while (iterator.hasNext()) {
                    val info = BedrockKeyParser.parseChunkKey(iterator.next().key) ?: continue
                    if (info.dimensionId != dimensionId) continue
                    if (!BedrockKeyParser.isKnownChunkTag(info.tagByte)) continue
                    val coordinate = Pair(info.chunkX, info.chunkZ)
                    populated += coordinate
                    if (info.tagByte == BedrockTagType.BLOCK_ENTITY.tagByte) {
                        blockEntities += coordinate
                    }
                }
            } finally {
                iterator.close()
            }
        }
        return ChunkCoordinateSummary(populated, blockEntities)
    }

    fun writeBatch(action: (WriteBatch) -> Unit) {
        synchronized(dbLock) {
            val database = open()
            val batch = database.createWriteBatch()
            try {
                action(batch)
                database.write(batch)
            } finally {
                batch.close()
            }
        }
    }

    override fun close() {
        synchronized(dbLock) {
            try {
                db?.close()
            } catch (_: Exception) {
            } finally {
                db = null
            }
        }
    }
}
