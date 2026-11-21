/*
 * Copyright (C) 2026 Dicecan
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.encore.bedrock.leveldb.model

data class LevelDbRecord(
    val key: ByteArray,
    val keyString: String,
    val displayName: String,
    val category: LevelDbCategory,
    val chunkX: Int? = null,
    val chunkZ: Int? = null,
    val dimension: BedrockDimension = BedrockDimension.OVERWORLD,
    val tagType: BedrockTagType = BedrockTagType.UNKNOWN,
    val subChunkIndex: Int? = null,
    val valueSize: Int,
    val isNbt: Boolean,
    val hasMultipleCompounds: Boolean = false
) {
    fun keyToHex(): String = key.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LevelDbRecord
        return key.contentEquals(other.key)
    }

    override fun hashCode(): Int = key.contentHashCode()

    override fun toString(): String = "LevelDbRecord($displayName, size=$valueSize bytes, category=$category)"
}
