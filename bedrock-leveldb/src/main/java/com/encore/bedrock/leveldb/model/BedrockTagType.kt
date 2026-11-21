/*
 * Copyright (C) 2026 Dicecan
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.encore.bedrock.leveldb.model

enum class BedrockTagType(val tagByte: Int, val description: String) {
    DATA_3D(0x2B, "3D 高度图与生物群系 (1.18+)"),
    VERSION(0x2C, "区块数据版本号"),
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
    BORDER_BLOCKS(0x38, "边界方块数据"),
    HARDCODED_SPAWNERS(0x39, "结构硬编码刷怪区"),
    RANDOM_TICKS(0x3A, "随机刻调度"),
    CHECK_SUMS(0x3B, "区块校验和"),
    GENERATION_SEED(0x3C, "区块生成种子"),
    META_DATA_HASH(0x3F, "元数据哈希"),
    BLENDING_DATA(0x40, "区块混合数据"),
    ACTOR_DIGEST_VERSION(0x41, "实体摘要版本"),
    VERSION_MODERN(0x76, "现代区块版本号 (1.16.100+)"),
    UNKNOWN(-1, "未知标签");

    companion object {
        private val LOOKUP = Array(256) { UNKNOWN }.apply {
            for (tag in entries) {
                if (tag.tagByte in 0..255) {
                    this[tag.tagByte] = tag
                }
            }
        }

        fun fromByte(b: Int): BedrockTagType {
            if (b < 0) return UNKNOWN
            return LOOKUP[b and 0xFF]
        }
    }
}
