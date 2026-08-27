package com.encore.bedrock.leveldb.model

enum class LevelDbCategory(val title: String, val description: String) {
    ALL("全部", "所有键值记录"),
    PLAYER("玩家", "本地房主玩家及远端联机玩家档案"),
    ENTITY("实体", "生物、投掷物与 Actor 数据"),
    BLOCK_ENTITY("方块实体", "箱子、熔炉、指令方块等方块实体"),
    WORLD("世界系统", "传送门、计分板、常加载区域、村庄等系统数据"),
    CHUNK("区块", "子区块几何、高度图、群系、版本刻数据"),
    OTHER("其他", "未分类的自定义或元数据")
}

enum class BedrockDimension(val id: Int, val displayName: String) {
    OVERWORLD(0, "主世界"),
    NETHER(1, "下界"),
    THE_END(2, "末地");

    companion object {
        fun fromId(id: Int): BedrockDimension = when (id) {
            1 -> NETHER
            2 -> THE_END
            else -> OVERWORLD
        }
    }
}
