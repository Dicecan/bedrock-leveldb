package com.encore.bedrock.leveldb

import br.com.gamemods.nbtmanipulator.NbtFile
import com.encore.bedrock.leveldb.model.LevelDbRecord
import com.encore.bedrock.leveldb.nbt.BedrockNbtCodec
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteBatch
import java.io.Closeable
import java.io.File

class BedrockLevelDb(val dbFolder: File) : Closeable, AutoCloseable {

    private var db: DB? = null

    fun open(createIfMissing: Boolean = false): DB {
        if (db == null) {
            val options = Options().apply {
                this.createIfMissing(createIfMissing)
            }
            db = org.iq80.leveldb.impl.DbImpl(options, dbFolder.absolutePath, SafeEnv())
        }
        return db!!
    }

    fun get(key: ByteArray): ByteArray? {
        return open().get(key)
    }

    fun getNbt(record: LevelDbRecord): NbtFile? {
        val bytes = get(record.key) ?: return null
        return BedrockNbtCodec.decode(bytes, record.hasMultipleCompounds)
    }

    fun put(key: ByteArray, value: ByteArray) {
        open().put(key, value)
    }

    fun putNbt(record: LevelDbRecord, nbtFile: NbtFile) {
        val bytes = BedrockNbtCodec.encode(nbtFile, record.hasMultipleCompounds)
        put(record.key, bytes)
    }

    fun delete(key: ByteArray) {
        open().delete(key)
    }

    fun getAllRecords(onProgress: ((scannedCount: Int) -> Unit)? = null): List<LevelDbRecord> {
        val database = open()
        val list = mutableListOf<LevelDbRecord>()
        val iterator = database.iterator()
        var count = 0
        try {
            iterator.seekToFirst()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val key = entry.key
                val value = entry.value
                val sample = if (value.size > 16) value.copyOfRange(0, 16) else value
                list.add(BedrockKeyParser.parse(key, value.size, sample))
                count++
                if (count % 250 == 0) {
                    onProgress?.invoke(count)
                }
            }
            onProgress?.invoke(count)
        } finally {
            iterator.close()
        }
        return list
    }

    fun writeBatch(action: (WriteBatch) -> Unit) {
        val batch = open().createWriteBatch()
        try {
            action(batch)
            open().write(batch)
        } finally {
            batch.close()
        }
    }

    override fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db = null
        }
    }
}