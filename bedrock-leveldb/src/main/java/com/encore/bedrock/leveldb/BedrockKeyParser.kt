package com.encore.bedrock.leveldb

import com.encore.bedrock.leveldb.model.BedrockDimension
import com.encore.bedrock.leveldb.model.BedrockTagType
import com.encore.bedrock.leveldb.model.LevelDbCategory
import com.encore.bedrock.leveldb.model.LevelDbRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser and categorizer for raw Minecraft Bedrock LevelDB Keys.
 */
object BedrockKeyParser {

    fun parse(key: ByteArray, valueSize: Int, valueSample: ByteArray? = null): LevelDbRecord {
        val keyStr = try {
            String(key, Charsets.UTF_8)
        } catch (e: Exception) {
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
            val actorId = try {
                if (key.size >= 19) ByteBuffer.wrap(key, 11, 8).order(ByteOrder.LITTLE_ENDIAN).long else null
            } catch (e: Exception) { null }
            val title = if (actorId != null) "实体生物 (ID: $actorId)" else "实体生物 ($keyStr)"
            return LevelDbRecord(key, keyStr, title, LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = true)
        }
        if (keyStr.startsWith("digp")) {
            return LevelDbRecord(key, keyStr, "Digp 实体生物 ($keyStr)", LevelDbCategory.ENTITY, valueSize = valueSize, isNbt = true)
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

        // 4. 2D / 3D Chunk Keys (8 or 12 coordinate prefix + tag byte)
        if (key.size in 9..14 || key.size in 4..8) {
            try {
                val buffer = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
                if (key.size >= 8) {
                    val chunkX = buffer.getInt(0)
                    val chunkZ = buffer.getInt(4)
                    val hasDim = key.size >= 13
                    val dimId = if (hasDim) buffer.getInt(8) else 0
                    val dimension = BedrockDimension.fromId(dimId)
                    val tagByte = if (hasDim && key.size >= 13) key[12].toInt() and 0xFF else if (key.size >= 9) key[8].toInt() and 0xFF else -1
                    val tagType = BedrockTagType.fromByte(tagByte)

                    val dimSuffix = if (dimension != BedrockDimension.OVERWORLD) " [${dimension.displayName}]" else ""

                    when (tagType) {
                        BedrockTagType.BLOCK_ENTITY -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 方块实体", LevelDbCategory.BLOCK_ENTITY, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true, hasMultipleCompounds = true)
                        BedrockTagType.ENTITY -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 实体数据", LevelDbCategory.ENTITY, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true, hasMultipleCompounds = true)
                        BedrockTagType.SUB_CHUNK_PREFIX -> {
                            val subIndex = if (hasDim && key.size >= 14) key[13].toInt() else if (key.size >= 10) key[9].toInt() else 0
                            return LevelDbRecord(key, keyStr, "子区块 [$chunkX, $chunkZ]$dimSuffix (Y: $subIndex)", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, subChunkIndex = subIndex, valueSize = valueSize, isNbt = false)
                        }
                        BedrockTagType.VERSION -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 版本信息", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                        BedrockTagType.DATA_2D -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 2D 数据 (群系/高度)", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                        BedrockTagType.PENDING_TICKS -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 待处理方块刻", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = true)
                        BedrockTagType.BLOCK_EXTRA_DATA -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 额外方块数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                        BedrockTagType.BIOME_STATE -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 群系状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                        BedrockTagType.FINALIZED_STATE -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 生成状态", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = false)
                        BedrockTagType.CONVERSION_DATA -> return LevelDbRecord(key, keyStr, "区块 [$chunkX, $chunkZ]$dimSuffix 刷怪点数据", LevelDbCategory.CHUNK, chunkX, chunkZ, dimension, tagType, valueSize = valueSize, isNbt = isNbt)
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Fallthrough to general key
            }
        }

        val fallbackName = if (keyStr.isNotEmpty() && keyStr.all { it.isLetterOrDigit() || it in "_-~.: " }) {
            keyStr
        } else {
            "0x" + key.take(8).joinToString("") { "%02x".format(it) } + if (key.size > 8) "..." else ""
        }
        return LevelDbRecord(key, keyStr, fallbackName, LevelDbCategory.OTHER, valueSize = valueSize, isNbt = isNbt)
    }
}
