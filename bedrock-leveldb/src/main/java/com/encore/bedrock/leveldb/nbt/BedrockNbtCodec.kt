package com.encore.bedrock.leveldb.nbt

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtIO
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

object BedrockNbtCodec {

    @Throws(IOException::class)
    fun decode(bytes: ByteArray, isMultiple: Boolean = false): NbtFile {
        if (!isMultiple) {
            try {
                return readBedrockRoot(ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                return decodeMultipleCompounds(bytes, e)
            }
        }
        return decodeMultipleCompounds(bytes, null)
    }

    private fun readBedrockRoot(stream: ByteArrayInputStream): NbtFile {
        return NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
    }

    @Throws(IOException::class)
    private fun decodeMultipleCompounds(bytes: ByteArray, firstFailure: Exception?): NbtFile {
        val rootCompound = NbtCompound()
        val stream = ByteArrayInputStream(bytes)
        var index = 0

        while (stream.available() > 0) {
            if (isZeroPadding(stream)) break
            val availableBefore = stream.available()
            try {
                val nbt = readBedrockRoot(stream)
                val name = if (nbt.name.isNotEmpty()) nbt.name else "entry_$index"
                rootCompound[name] = nbt.tag
                index++
            } catch (e: Exception) {
                throw IOException("Invalid Bedrock NBT payload", e)
            }
            if (stream.available() >= availableBefore) {
                throw IOException("Bedrock NBT parser made no progress")
            }
        }

        if (index == 0) {
            throw firstFailure ?: EOFException("Empty Bedrock NBT payload")
        }
        return NbtFile("Root", rootCompound)
    }

    private fun isZeroPadding(stream: ByteArrayInputStream): Boolean {
        stream.mark(stream.available())
        if (stream.read() != 0) {
            stream.reset()
            return false
        }
        var value = stream.read()
        while (value == 0) {
            value = stream.read()
        }
        val allZero = value < 0
        stream.reset()
        return allZero
    }

    fun encode(nbtFile: NbtFile, wasMultiple: Boolean = false): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        if (!wasMultiple) {
            NbtIO.writeNbtFile(dos, nbtFile, compressed = false, littleEndian = true)
        } else {
            val tag = nbtFile.tag
            if (tag is NbtCompound) {
                tag.forEach { (_, childTag) ->
                    val singleFile = NbtFile("", childTag)
                    NbtIO.writeNbtFile(dos, singleFile, compressed = false, littleEndian = true)
                }
            } else {
                NbtIO.writeNbtFile(dos, nbtFile, compressed = false, littleEndian = true)
            }
        }
        dos.flush()
        return baos.toByteArray()
    }
}