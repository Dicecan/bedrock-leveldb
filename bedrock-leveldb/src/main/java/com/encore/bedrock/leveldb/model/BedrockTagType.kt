package com.encore.bedrock.leveldb.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bedrock Chunk Tag Byte Identifiers according to Bedrock World Specification.
 */
enum class BedrockTagType(val tagByte: Int, val description: String) {
    DATA_2D(0x2D, "2D 数据 (群系与高度图)"),
    DATA_2D_LEGACY(0x2E, "旧版 2D 数据"),
    SUB_CHUNK_PREFIX(0x2F, "子区块地形切片 (Y: index)"),
    LEGACY_TERRAIN(0x30, "旧版 1.0 地形"),
    BLOCK_ENTITY(0x31, "方块实体 (多 Compound 连续流)"),
    ENTITY(0x32, "实体生物 (多 Compound 连续流)"),
    PENDING_TICKS(0x33, "待处理方块刻调度 (Tick NBT)"),
    BLOCK_EXTRA_DATA(0x34, "额外方块属性数据"),
    BIOME_STATE(0x35, "3D 动态生物群系调色板"),
    FINALIZED_STATE(0x36, "区块生成阶段状态"),
    CONVERSION_DATA(0x37, "刷怪点与生成器转换数据"),
    VERSION(0x2C, "区块数据版本号"),
    UNKNOWN(-1, "未知标签");

    companion object {
        fun fromByte(b: Int): BedrockTagType {
            return values().firstOrNull { it.tagByte == (b and 0xFF) } ?: UNKNOWN
        }
    }
}
