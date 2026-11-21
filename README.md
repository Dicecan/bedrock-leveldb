# bedrock-leveldb

[English](#english) | [中文说明](#chinese)

---

<a name="english"></a>
## English

A pure Java/Kotlin LevelDB library designed for reading, modifying, and analyzing **Minecraft Bedrock Edition** world databases, with platform-specific optimizations for Android ART runtimes and standard JVM environments.

Based on [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) and [iq80/leveldb](https://github.com/iq80/leveldb).

---

### Motivation & Background

Existing Java LevelDB implementations for Bedrock Edition (such as `iq80/leveldb` and its derivative forks) were primarily built for server-side or desktop JVM environments (e.g., Nukkit, Cloudburst, Geyser). When integrated into Android applications (such as mobile world editors, backup tools, and map analyzers), several platform-specific limitations arise:

1. **`MappedByteBuffer` and ART Incompatibility**:
   Upstream default `MMRandomInputFile` relies on `FileChannel.map()`. On Android's ART runtime, differences in `java.nio.Buffer` method signatures across Android API levels and Java 9+ bytecode frequently cause `NoSuchMethodError` runtime exceptions. In addition, memory-mapped files can exhaust virtual address space on low-memory or 32-bit processes.
2. **File Locking in Android Storage**:
   Standard Java `FileLock` behavior frequently conflicts with Android's sandboxed storage and Scoped Storage (SAF) semantics.
3. **GC Overhead During Bulk Decompression**:
   Iterating over large world databases generates thousands of chunk decompression operations. Allocating unpooled `Inflater` and `Deflater` instances per block creates high object churn and triggers frequent garbage collection pauses on mobile devices.
4. **Lack of Bedrock Protocol Abstractions**:
   Upstream LevelDB engines operate strictly on raw `byte[]` keys and values. Developers must manually reverse-engineer Mojang's binary key format (Little-Endian coordinate packing, dimension offsets, sub-chunk Y indices, actor IDs, and concatenated multi-compound NBT byte streams).

`bedrock-leveldb` addresses these problems by providing an Android-safe I/O backend, pooled decompression, and high-level Bedrock domain models.

---

### Comparison with Upstream (`leveldb-mcpe-java`)

| Feature / Component | Upstream (`leveldb-mcpe-java`) | `bedrock-leveldb` |
| :--- | :--- | :--- |
| **I/O Subsystem** | `FileChannel.map` (`MappedByteBuffer`) | `SafeEnv` (`MmapLimiter(0)` + bounds-clamped `RandomAccessFile`, avoiding ART mmap crashes on both reads and writes) |
| **ZLib Engine** | Allocates new `Inflater`/`Deflater` per block | `ThreadLocal` pooled `Inflater`/`Deflater` with auto-resizing buffer |
| **Key Recognition** | Raw `ByteArray` only | `BedrockKeyParser` (zero-allocation LE parser for Chunk `[X, Z]`, SubChunk Y, Dimensions, 1.18–1.21+ tags, Actors, Players, World systems) |
| **NBT Codec** | External NBT parser required, no multi-compound handling | `BedrockNbtCodec` with trailing zero-padding handling, strict corrupt-tail detection, and multi-compound streams |
| **Chunk Management** | Raw key iteration only | `getChunkCoordinateSummary`, `deleteChunk`/`deleteChunks`/`deleteChunksOutsideRange`, plus `SubChunkEncoder`, `BitArray`, `BedrockBlockState`, `BedrockChunkHelper` |
| **Progress Reporting** | Basic `DBIterator` | Thread-safe `getAllRecords(onProgress)` with `cacheSize(0)` / `fillCache(false)` to prevent OOM during bulk scans |
| **Native Binaries** | None (Pure Java) | None (Pure Java/Kotlin, compatible with all Android ABIs) |

---

### Dependency Setup

#### Gradle (JitPack)

```groovy
// settings.gradle or root build.gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// module build.gradle
dependencies {
    implementation 'com.github.Dicecan:bedrock-leveldb:v1.1.0'
}
```

---

### Usage

#### Opening a Database and Reading Records

```kotlin
import com.encore.bedrock.leveldb.BedrockLevelDb
import java.io.File

val db = BedrockLevelDb(File("/path/to/world/db"))

// Read all indexed records with progress feedback
val records = db.getAllRecords { scannedCount ->
    println("Scanned: $scannedCount records")
}

records.forEach { record ->
    println("${record.displayName} | Category: ${record.category} | Size: ${record.valueSize} bytes")
}
```

#### Reading and Modifying NBT Data

```kotlin
import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtString

val playerRecord = records.firstOrNull { it.keyString == "~local_player" }

if (playerRecord != null) {
    val nbtFile = db.getNbt(playerRecord)
    val rootCompound = nbtFile?.tag as? NbtCompound

    // Modify NBT tag
    rootCompound?.put("CustomData", NbtString("SampleValue"))

    // Write back to LevelDB
    if (nbtFile != null) {
        db.putNbt(playerRecord, nbtFile)
    }
}

db.close()
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

`bedrock-leveldb` 是一个用于读取、修改和分析 **Minecraft 基岩版 (Bedrock Edition)** LevelDB 世界存档的纯 Java/Kotlin 库，针对 Android ART 运行环境和标准 JVM 平台进行了底层适配与性能优化。

本项目基于 [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) 与 [iq80/leveldb](https://github.com/iq80/leveldb) 进行重构与扩展。

---

### 编写背景与原因

现有的基岩版 Java LevelDB 实现主要面向桌面 JVM 和独立服务器（如 Nukkit、Cloudburst、Geyser 等）。在 Android 移动端（如存档编辑器、备份工具、辅助启动器）中使用这些库时，通常会遇到以下问题：

1. **`MappedByteBuffer` 在 ART 环境下的兼容性问题**：
   原版默认的 `MMRandomInputFile` 依赖 `FileChannel.map()`。在 Android 的 ART 虚拟机上，Java 9+ 的 `java.nio.Buffer` 协变返回类型差异容易在特定 Android 版本上引发 `NoSuchMethodError`；此外，内存映射在大存档遍历时极易耗尽 32 位进程或低内存设备的虚拟内存地址空间。
2. **Android 存储沙盒与文件锁冲突**：
   标准 Java `FileLock` 在 Android 的分区存储（Scoped Storage / SAF）或多线程/多进程访问时容易发生冲突或异常。
3. **海量数据解压导致的 GC 压力**：
   扫描包含数万个区块的世界存档时，频繁创建和销毁 `Inflater` / `Deflater` 实例会产生大量的对象内存碎片，在移动设备上引起频繁的垃圾回收卡顿。
4. **缺乏基岩版业务层协议封装**：
   原版 LevelDB 驱动仅提供底层的 `byte[] -> byte[]` 键值映射。上层应用必须自行实现小端坐标解包、维度偏移、子区块 Y 轴层级、Actor 实体 ID 以及基岩版特有的**连续多 Compound NBT 复合二进制流**的反序列化逻辑。

`bedrock-leveldb` 针对上述问题，提供了适配 Android 的安全 I/O 环境、对象池化压缩引擎以及基岩版专用的结构化数据模型。

---

### 与原版 (`leveldb-mcpe-java`) 的技术对比与优化

| 模块 / 特性 | 原版 (`leveldb-mcpe-java`) | `bedrock-leveldb` (本项目) |
| :--- | :--- | :--- |
| **底层 I/O 引擎** | `FileChannel.map` (`MappedByteBuffer`) | `SafeEnv`（`MmapLimiter(0)` + 越界保护分段 `RandomAccessFile`，读写全链路禁用 mmap，规避 ART 崩溃） |
| **ZLib 压缩/解压** | 每个数据块独立创建 `Inflater`/`Deflater` | `ThreadLocal` 实例池 + 自适应扩容缓冲机制 |
| **Key 格式识别** | 仅支持原始 `byte[]` | `BedrockKeyParser`（零分配小端解析，支持 1.18–1.21+ 现代区块标签、子区块 Y、多维度、实体、玩家、世界系统） |
| **NBT 编解码** | 需自行引入外部 NBT 库，不支持多 Compound 流 | 内置 `BedrockNbtCodec`，支持单/多 Compound 复合流、尾部零填充兼容与损坏尾部严格校验 |
| **区块管理与编码** | 仅支持底层键值遍历 | 提供 `getChunkCoordinateSummary`、批量/范围区块清理，以及 `SubChunkEncoder`/`BitArray`/`BedrockBlockState`/`BedrockChunkHelper` |
| **遍历与进度** | 仅提供标准 `DBIterator` | 线程安全 `getAllRecords(onProgress)`，默认禁用块缓存（`cacheSize(0)` + `fillCache(false)`）防止大存档扫描 OOM |
| **原生动态库依赖** | 无（纯 Java） | 无（纯 Java/Kotlin，全架构 ABI 兼容） |

---

### 快速接入

#### Gradle (JitPack)

```groovy
// settings.gradle 或根目录 build.gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// 模块 build.gradle
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

val db = BedrockLevelDb(File("/path/to/world/db"))

// 扫描所有记录并实时接收进度反馈
val records = db.getAllRecords { scannedCount ->
    println("已扫描: $scannedCount 条记录")
}

records.forEach { record ->
    println("${record.displayName} | 分类: ${record.category} | 大小: ${record.valueSize} 字节")
}
```

#### 读取与修改玩家 NBT 数据

```kotlin
import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtString

val playerRecord = records.firstOrNull { it.keyString == "~local_player" }

if (playerRecord != null) {
    val nbtFile = db.getNbt(playerRecord)
    val rootCompound = nbtFile?.tag as? NbtCompound

    // 修改 NBT 属性
    rootCompound?.put("CustomData", NbtString("SampleValue"))

    // 写回数据库
    if (nbtFile != null) {
        db.putNbt(playerRecord, nbtFile)
    }
}

db.close()
```

---

### 开源许可证与致谢

本项目采用 **GNU General Public License v3.0 (GPLv3)** 许可证开源。

本项目包含基于 Apache-2.0 及 BSD-3-Clause 许可证的衍生组件，在此感谢以下开源项目的贡献：
- [HiveGamesOSS/leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) (Apache-2.0)
- [iq80/leveldb](https://github.com/iq80/leveldb) (Apache-2.0)
- [google/leveldb](https://github.com/google/leveldb) (BSD-3-Clause)
- [Mojang Studios / Microsoft](https://www.minecraft.net)