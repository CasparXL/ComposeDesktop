package com.caspar.compose.util

import java.io.File
import kotlin.time.Duration.Companion.seconds

object AppStorage {
    val store by lazy {
        OptimizedKeyValueStore(
            baseDir = File(System.getProperty("user.dir"), "app_data"),
            enableCompression = true,  // 大文件必开
            shardCount = 16,           // 10-100MB 数据推荐值
            writeDelay = 3.seconds
        )
    }
}