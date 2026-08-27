package com.encore.bedrock.leveldb.nbt

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtFile
import br.com.gamemods.nbtmanipulator.NbtIO
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object BedrockNbtCodec {

    fun decode(bytes: ByteArray, isMultiple: Boolean = false): NbtFile {
        if (!isMultiple) {
            try {
                val stream = ByteArrayInputStream(bytes)
                return NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
            } catch (e: Exception) {
                return decodeMultipleCompounds(bytes)
            }
        } else {
            return decodeMultipleCompounds(bytes)
        }
    }

    private fun decodeMultipleCompounds(bytes: ByteArray): NbtFile {
        val rootCompound = NbtCompound()
        val stream = ByteArrayInputStream(bytes)
        var index = 0
        while (stream.available() > 0) {
            try {
                val nbt = NbtIO.readNbtFile(stream, compressed = false, littleEndian = true, readHeaders = false)
                val name = if (nbt.name.isNotEmpty()) nbt.name else "entry_$index"
                rootCompound[name] = nbt.tag
                index++
            } catch (e: Exception) {
                break
            }
        }
        if (index == 0) {
            val fallbackStream = ByteArrayInputStream(bytes)
            return NbtIO.readNbtFile(fallbackStream, compressed = false, littleEndian = true, readHeaders = false)
        }
        return NbtFile("Root", rootCompound)
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