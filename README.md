# bedrock-leveldb

[English](#english) | [中文说明](#chinese)

---

<a name="english"></a>
## English

A pure Java/Kotlin LevelDB library designed for reading, modifying, encoding, and analyzing **Minecraft Bedrock Edition** world databases, with platform-specific optimizations for Android ART runtimes and standard JVM environments.

Based on [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) and [iq80/leveldb](https://github.com/iq80/leveldb).

---

### Motivation & Background

Existing Java LevelDB implementations for Bedrock Edition were primarily built for server-side or desktop JVM environments. When integrated into Android applications (such as mobile world editors, backup tools, and map analyzers), several platform-specific limitations arise:

1. **`MappedByteBuffer` and ART Incompatibility**:
   Upstream default mmap I/O relies on `FileChannel.map()`. On Android's ART runtime, differences in `java.nio.Buffer` method signatures across Android API levels and Java 9+ bytecode frequently cause `NoSuchMethodError` runtime exceptions, and memory-mapped files can exhaust virtual address space on low-memory devices.
2. **Memory Pressure During Bulk Scans**:
   Scanning large worlds with default LevelDB block caching and per-block `Inflater`/`Deflater` allocation creates severe heap pressure and GC pauses on mobile devices.
3. **Lack of Bedrock Protocol & Chunk Encoding Abstractions**:
   Upstream LevelDB engines operate strictly on raw `byte[]` keys and values without Bedrock key parsing, modern 1.18–1.21+ chunk tags, concatenated multi-compound NBT handling, or sub-chunk palette encoding.

`bedrock-leveldb` addresses these problems by providing an Android-safe non-mmap I/O backend (`SafeEnv`), pooled ZLib compression, thread-safe no-cache scanning, chunk coordinate management, and sub-chunk palette encoding.

---

### Comparison with Upstream (`leveldb-mcpe-java`)

| Feature / Component | Upstream (`leveldb-mcpe-java`) | `bedrock-leveldb` (v1.1.0) |
| :--- | :--- | :--- |
| **I/O Subsystem** | `FileChannel.map` (`MappedByteBuffer`) | `SafeEnv` (`MmapLimiter(0)` + bounds-clamped `RandomAccessFile`, avoiding ART mmap crashes on both reads and writes) |
| **ZLib Engine** | Allocates new `Inflater`/`Deflater` per block | `ThreadLocal` pooled `Inflater`/`Deflater` with auto-resizing buffer |
| **Key Recognition** | Raw `ByteArray` only | `BedrockKeyParser` (zero-allocation LE parser for Chunk `[X, Z]`, SubChunk Y, Dimensions, 1.18–1.21+ tags, Actors, Players, World systems) |
| **NBT Codec** | External NBT parser required, no multi-compound handling | `BedrockNbtCodec` with trailing zero-padding handling, strict corrupt-tail detection, and multi-compound streams |
| **Chunk Management & Encoding** | Raw key iteration only | `getChunkCoordinateSummary`, `deleteChunk`/`deleteChunks`/`deleteChunksOutsideRange`, plus `SubChunkEncoder`, `BitArray`, `BedrockBlockState`, `BedrockChunkHelper` |
| **Scanning & Thread Safety** | Basic `DBIterator` | Thread-safe `getAllRecords(onProgress)` with `cacheSize(0)` / `fillCache(false)` to prevent OOM during bulk scans |
| **Native Binaries** | None (Pure Java) | None (Pure Java/Kotlin, compatible with all Android ABIs) |

---

### Dependency Setup

#### Gradle (JitPack)

```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Dicecan:bedrock-leveldb:v1.1.0'
}
```

---

### Usage

#### Opening a Database and Scanning Records

```kotlin
import com.encore.bedrock.leveldb.BedrockLevelDb
import java.io.File

BedrockLevelDb(File("/path/to/world/db")).use { db ->
    val records = db.getAllRecords { scannedCount ->
        println("Scanned: $scannedCount records")
    }

    records.forEach { record ->
        println("${record.displayName} | Category: ${record.category} | Size: ${record.valueSize} bytes")
    }
}
```

#### Chunk Coordinate Summary & Range Pruning

```kotlin
BedrockLevelDb(File("/path/to/world/db")).use { db ->
    val summary = db.getChunkCoordinateSummary(dimensionId = 0)
    println("Populated chunks: ${summary.populated.size}, BlockEntity chunks: ${summary.blockEntities.size}")

    // Prune chunks outside [-16..16, -16..16] while preserving unknown and non-chunk keys
    val deletedKeys = db.deleteChunksOutsideRange(-16, 16, -16, 16, dimensionId = 0)
    println("Deleted $deletedKeys chunk records")
}
```

#### Encoding Sub-Chunks & Writing Chunk Keys

```kotlin
import com.encore.bedrock.leveldb.chunk.BedrockBlockState
import com.encore.bedrock.leveldb.chunk.BedrockChunkHelper
import com.encore.bedrock.leveldb.chunk.SubChunkEncoder

val blocks = Array(SubChunkEncoder.SUBCHUNK_SIZE) { BedrockBlockState.AIR }
blocks[SubChunkEncoder.getLocalIndex(0, 0, 0)] = BedrockBlockState.BEDROCK

val subChunkBytes = SubChunkEncoder.encode(blocks)
val subChunkKey = BedrockChunkHelper.createSubChunkKey(chunkX = 0, chunkZ = 0, subChunkY = 0, dimensionId = 0)
```

#### Reading and Modifying NBT Data

```kotlin
import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtString

val playerRecord = records.firstOrNull { it.keyString == "~local_player" }
if (playerRecord != null) {
    val nbtFile = db.getNbt(playerRecord)
    val rootCompound = nbtFile?.tag as? NbtCompound
    rootCompound?.put("CustomData", NbtString("SampleValue"))
    if (nbtFile != null) {
        db.putNbt(playerRecord, nbtFile)
    }
}
```

---

### License & Attribution

This project is licensed under the **GNU General Public License v3.0 (GPLv3)**.

Contains derivative works based on components originally licensed under the Apache License 2.0 and BSD 3-Clause License:
- [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) (Apache-2.0)
- [iq80/leveldb](https://github.com/iq80/leveldb) (Apache-2.0)
- [google/leveldb](https://github.com/google/leveldb) (BSD-3-Clause)

---

<a name="chinese"></a>
## 中文说明

`bedrock-leveldb` 是一个用于读取、修改、编码和分析 **Minecraft 基岩版 (Bedrock Edition)** LevelDB 世界存档的纯 Java/Kotlin 库，针对 Android ART 运行环境和标准 JVM 平台进行了底层适配与性能优化。

本项目基于 [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) 与 [iq80/leveldb](https://github.com/iq80/leveldb) 进行重构与扩展。

---

### 与原版 (`leveldb-mcpe-java`) 的技术对比与优化

| 模块 / 特性 | 原版 (`leveldb-mcpe-java`) | `bedrock-leveldb` (v1.1.0) |
| :--- | :--- | :--- |
| **底层 I/O 引擎** | `FileChannel.map` (`MappedByteBuffer`) | `SafeEnv`（`MmapLimiter(0)` + 越界保护分段 `RandomAccessFile`，读写全链路禁用 mmap，规避 ART 崩溃） |
| **ZLib 压缩/解压** | 每个数据块独立创建 `Inflater`/`Deflater` | `ThreadLocal` 实例池 + 自适应扩容缓冲机制 |
| **Key 格式识别** | 仅支持原始 `byte[]` | `BedrockKeyParser`（零分配小端解析，支持 1.18–1.21+ 现代区块标签、子区块 Y、多维度、实体、玩家、世界系统） |
| **NBT 编解码** | 需自行引入外部 NBT 库，不支持多 Compound 流 | 内置 `BedrockNbtCodec`，支持单/多 Compound 复合流、尾部零填充兼容与损坏尾部严格校验 |
| **区块管理与编码** | 仅支持底层键值遍历 | 提供 `getChunkCoordinateSummary`、批量/范围区块清理，以及 `SubChunkEncoder`/`BitArray`/`BedrockBlockState`/`BedrockChunkHelper` |
| **遍历与线程安全** | 仅提供标准 `DBIterator` | 线程安全 `getAllRecords(onProgress)`，默认禁用块缓存（`cacheSize(0)` + `fillCache(false)`）防止大存档扫描 OOM |
| **原生动态库依赖** | 无（纯 Java） | 无（纯 Java/Kotlin，全架构 ABI 兼容） |

---

### 快速接入

#### Gradle (JitPack)

```groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.Dicecan:bedrock-leveldb:v1.1.0'
}
```

---

### 使用示例

#### 打开数据库并遍历索引

```kotlin
import com.encore.bedrock.leveldb.BedrockLevelDb
import java.io.File

BedrockLevelDb(File("/path/to/world/db")).use { db ->
    val records = db.getAllRecords { scannedCount ->
        println("已扫描: $scannedCount 条记录")
    }

    records.forEach { record ->
        println("${record.displayName} | 分类: ${record.category} | 大小: ${record.valueSize} 字节")
    }
}
```

#### 区块坐标统计与范围清理

```kotlin
BedrockLevelDb(File("/path/to/world/db")).use { db ->
    val summary = db.getChunkCoordinateSummary(dimensionId = 0)
    println("已生成区块: ${summary.populated.size}, 含方块实体区块: ${summary.blockEntities.size}")

    val deleted = db.deleteChunksOutsideRange(-16, 16, -16, 16, dimensionId = 0)
    println("已清理 $deleted 条区块记录")
}
```

#### 子区块调色板编码与区块 Key 构建

```kotlin
import com.encore.bedrock.leveldb.chunk.BedrockBlockState
import com.encore.bedrock.leveldb.chunk.BedrockChunkHelper
import com.encore.bedrock.leveldb.chunk.SubChunkEncoder

val blocks = Array(SubChunkEncoder.SUBCHUNK_SIZE) { BedrockBlockState.AIR }
blocks[SubChunkEncoder.getLocalIndex(0, 0, 0)] = BedrockBlockState.BEDROCK

val subChunkBytes = SubChunkEncoder.encode(blocks)
val subChunkKey = BedrockChunkHelper.createSubChunkKey(chunkX = 0, chunkZ = 0, subChunkY = 0, dimensionId = 0)
```

---

### 开源许可证与致谢

本项目采用 **GNU General Public License v3.0 (GPLv3)** 许可证开源。

本项目包含基于 Apache-2.0 及 BSD-3-Clause 许可证的衍生组件，在此感谢以下开源项目的贡献：
- [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) (Apache-2.0)
- [iq80/leveldb](https://github.com/iq80/leveldb) (Apache-2.0)
- [google/leveldb](https://github.com/google/leveldb) (BSD-3-Clause)
- [Mojang Studios / Microsoft](https://www.minecraft.net)
