package com.encore.bedrock.leveldb

import com.encore.bedrock.leveldb.model.BedrockDimension
import com.encore.bedrock.leveldb.model.BedrockTagType
import com.encore.bedrock.leveldb.model.LevelDbCategory
import com.encore.bedrock.leveldb.model.LevelDbRecord

/**
 * Parsed coordinates and tag metadata for a Bedrock LevelDB chunk key.
 */
data class ChunkKeyInfo(
    val chunkX: Int,
    val chunkZ: Int,
    val dimensionId: Int,
    val tagByte: Int,
    val subChunkIndex: Int? = null
)

/**
 * Parser and categorizer for raw Minecraft Bedrock LevelDB Keys.
 */
object BedrockKeyParser {

    fun isKnownChunkTag(tagByte: Int): Boolean {
        return BedrockTagType.fromByte(tagByte) != BedrockTagType.UNKNOWN
    }

    /**
     * Parses a binary Bedrock chunk key (9/10 bytes for Overworld, 13/14 bytes for Nether/End).
     * Returns null if the key length or structure does not match a valid chunk key.
     */
    fun parseChunkKey(key: ByteArray): ChunkKeyInfo? {
        return when (key.size) {
            9 -> {
                val chunkX = readLittleEndianInt(key, 0)
                val chunkZ = readLittleEndianInt(key, 4)
                val tagByte = key[8].toInt() and 0xFF
                ChunkKeyInfo(chunkX, chunkZ, 0, tagByte)
            }
            10 -> {
                val tagByte = key[8].toInt() and 0xFF
                if (tagByte != BedrockTagType.SUB_CHUNK_PREFIX.tagByte) return null
                val chunkX = readLittleEndianInt(key, 0)
                val chunkZ = readLittleEndianInt(key, 4)
                ChunkKeyInfo(chunkX, chunkZ, 0, tagByte, subChunkIndex = key[9].toInt())
            }
            13 -> {
                val chunkX = readLittleEndianInt(key, 0)
                val chunkZ = readLittleEndianInt(key, 4)
                val dimensionId = readLittleEndianInt(key, 8)
                val tagByte = key[12].toInt() and 0xFF
                ChunkKeyInfo(chunkX, chunkZ, dimensionId, tagByte)
            }
            14 -> {
                val tagByte = key[12].toInt() and 0xFF
                if (tagByte != BedrockTagType.SUB_CHUNK_PREFIX.tagByte) return null
                val chunkX = readLittleEndianInt(key, 0)
                val chunkZ = readLittleEndianInt(key, 4)
                val dimensionId = readLittleEndianInt(key, 8)
                ChunkKeyInfo(chunkX, chunkZ, dimensionId, tagByte, subChunkIndex = key[13].toInt())
            }
            else -> null
        }
    }

    fun parse(key: ByteArray, valueSize: Int, valueSample: ByteArray? = null): LevelDbRecord {
        val keyStr = try {
            String(key, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }

        val isNbt = valueSample != null && valueSample.isNotEmpty() && (
            valueSample[0] == 0x0A.toByte() ||
            valueSample[0] == 0x09.toByte() ||
            valueSample[0] == 0x08.toByte()
        )

        // 1. Player Profiles
        if (keyStr == "~local_player") {
            return LevelDbRecord(key, keyStr, "本地房主玩家 (~local_player)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("player_server_")) {
            val uuid = keyStr.removePrefix("player_server_")
            return LevelDbRecord(key, keyStr, "服务器玩家 ($uuid)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("player_")) {
            val uuid = keyStr.removePrefix("player_")
            return LevelDbRecord(key, keyStr, "玩家数据 ($uuid)", LevelDbCategory.PLAYER, valueSize = valueSize, isNbt = true)
        }

        // 2. Entity & Actor Keys
        if (key.size >= 11 && keyStr.startsWith("actorprefix")) {
            val actorId = if (key.size >= 19) readLittleEndianLong(key, 11) else null
            val title = if (actorId != null) "实体生物 (ID: $actorId)" else "实体生物 ($keyStr)"
            return LevelDbRecord(key, keyStr, title, LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("digp")) {
            return LevelDbRecord(key, keyStr, "Digp 实体索引 ($keyStr)", LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = isNbt)
        }

        // 3. World Systems & Portals
        if (keyStr == "portals") {
            return LevelDbRecord(key, keyStr, "下界传送门索引 (portals)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr == "scoreboard") {
            return LevelDbRecord(key, keyStr, "计分板数据 (scoreboard)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr == "autonomousentities") {
            return LevelDbRecord(key, keyStr, "自主活动实体 (autonomousentities)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr == "Overworld" || keyStr == "Nether" || keyStr == "TheEnd") {
            return LevelDbRecord(key, keyStr, "维度全局数据 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("map_")) {
            return LevelDbRecord(key, keyStr, "地图数据 (${keyStr.removePrefix("map_")})", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = isNbt)
        }
        if (keyStr.startsWith("village_") || keyStr.startsWith("VILLAGE_")) {
            return LevelDbRecord(key, keyStr, "村庄数据 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("tickingarea_")) {
            return LevelDbRecord(key, keyStr, "常加载区域 ($keyStr)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = true)
        }
        if (keyStr == "schedulerWT") {
            return LevelDbRecord(key, keyStr, "世界任务调度器 (schedulerWT)", LevelDbCategory.WORLD, valueSize = valueSize, isNbt = isNbt)
        }

        // 4. 2D / 3D Chunk Keys (8 or 12 byte coordinate prefix + tag byte [+ subChunk Y])
        parseChunkKey(key)?.let { info ->
            val chunkX = info.chunkX
            val chunkZ = info.chunkZ
            val dimension = BedrockDimension.fromId(info.dimensionId)
            val tagType = BedrockTagType.fromByte(info.tagByte)
            val dimSuffix = if (dimension != BedrockDimension.OVERWORLD) " [${dimension.displayName}]" else ""

            when (tagType) {
                BedrockTagType.BLOCK_ENTITY -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 方块实体", LevelDbCategory.BLOCK_ENTITY, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true, hasMultipleCompounds = true)
                BedrockTagType.ENTITY -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 实体数据", LevelDbCategory.ENTITY, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true, hasMultipleCompounds = true)
                BedrockTagType.SUB_CHUNK_PREFIX -> {
                    val subIndex = info.subChunkIndex ?: 0
                    return LevelDbRecord(key, keyStr, "子区块 [$chunkX, $chunkZ]$dimSuffix (Y: $subIndex)", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, subChunkIndex = subIndex, valueSize = valueSize, isNbt = false)
                }
                BedrockTagType.VERSION, BedrockTagType.VERSION_MODERN -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 版本信息", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.DATA_3D -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 3D 数据 (群系/高度)", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.DATA_2D, BedrockTagType.DATA_2D_LEGACY -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 2D 数据 (群系/高度)", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.LEGACY_TERRAIN -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 旧版地形", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.PENDING_TICKS -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 待处理方块刻", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true)
                BedrockTagType.RANDOM_TICKS -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 随机方块刻", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                BedrockTagType.BLOCK_EXTRA_DATA -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 额外方块数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                BedrockTagType.BIOME_STATE -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 群系状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.FINALIZED_STATE -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 生成状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                BedrockTagType.CONVERSION_DATA -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 刷怪点数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                BedrockTagType.BORDER_BLOCKS,
                BedrockTagType.HARDCODED_SPAWNERS,
                BedrockTagType.CHECK_SUMS,
                BedrockTagType.GENERATION_SEED,
                BedrockTagType.META_DATA_HASH,
                BedrockTagType.BLENDING_DATA,
                BedrockTagType.ACTOR_DIGEST_VERSION -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix ${tagType.description}", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                BedrockTagType.UNKNOWN -> {}
            }
        }

        val fallbackName = if (keyStr.isNotEmpty() && keyStr.all { it.isLetterOrDigit() || it in "_-~.: " }) {
            keyStr
        } else {
            "0x" + key.take(8).joinToString("") { "%02x".format(it) } + if (key.size > 8) "..." else ""
        }
        return LevelDbRecord(key, keyStr, fallbackName, LevelDbCategory.OTHER, valueSize = valueSize, isNbt = isNbt)
    }

    private fun readLittleEndianInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            (bytes[offset + 3].toInt() shl 24)
    }

    private fun readLittleEndianLong(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 4].toLong() and 0xFFL) shl 32) or
            ((bytes[offset + 5].toLong() and 0xFFL) shl 40) or
            ((bytes[offset + 6].toLong() and 0xFFL) shl 48) or
            ((bytes[offset + 7].toLong() and 0xFFL) shl 56)
    }
}
