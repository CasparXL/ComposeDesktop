package com.caspar.compose.util

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 本地持久化键值对存储
 */
@OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
class OptimizedKeyValueStore(
    baseDir: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    },
    private val writeDelay: Duration = 3.seconds,
    private val enableCompression: Boolean = true,
    private val shardCount: Int = 16,
    private val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
) {
    private val logHelper = this.logger
    private val shards = List(shardCount) { shardIndex ->
        Shard(
            file = File(baseDir, "prefs_shard_${shardIndex}.json${if (enableCompression) ".gz" else ""}"),
            json = json,
            enableCompression = enableCompression,
            writeDelay = writeDelay,
            coroutineContext = coroutineContext + CoroutineName("Shard-$shardIndex")
        )
    }
    private val scope = CoroutineScope(coroutineContext + CoroutineName("StoreManager"))
    private var isClosed = false
    private val initJob: Deferred<Unit>

    init {
        baseDir.mkdirs()
        initJob = scope.async(Dispatchers.IO) {
            // 并行加载所有分片
            shards.map { shard -> async { shard.loadAsync() } }.awaitAll()

            // ✅ 安全获取总项数（在协程内调用 suspend 函数）
            val totalItems = shards.map { shard ->
                async { shard.getCacheSize() }
            }.awaitAll().sum()

            logHelper.info("✅ 存储初始化完成 | 分片: $shardCount | 总项数: $totalItems | 路径: ${baseDir.absolutePath}")
        }
    }

    private fun getShard(key: String) = shards[abs(key.hashCode()) % shardCount]

    /** 从内存缓存读取（自动等待初始化） */
    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? {
        if (isClosed) return null
        initJob.await()
        return getShard(key).get(key, serializer)
    }

    /** 写入内存缓存 + 智能延迟持久化 */
    suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>): Boolean {
        if (isClosed) return false
        initJob.await()
        return getShard(key).put(key, value, serializer)
    }

    suspend fun delete(key: String): Boolean {
        if (isClosed) return false
        initJob.await()
        return getShard(key).delete(key)
    }

    suspend fun clear() {
        if (isClosed) return
        initJob.await()
        shards.forEach { it.clear() }
    }

    /** 立即持久化所有分片（关键操作后调用） */
    suspend fun flush(): Boolean {
        if (isClosed) return false
        return withContext(Dispatchers.IO) {
            shards.map { shard -> async { shard.flush() } }.awaitAll().all { it }
        }
    }

    /**
     * 仅刷新包含指定 Key 的分片（智能跳过无变化分片）
     * 适用场景：修改单个 Key 后需立即持久化，避免全量 flush
     */
    suspend fun flushForKey(key: String): Boolean {
        if (isClosed) return false
        initJob.await()
        return getShard(key).flush() // 内部已含脏检查
    }

    /**
     * 批量刷新指定 Keys 所在分片（自动去重分片）
     */
    suspend fun flushForKeys(keys: Collection<String>): Boolean {
        if (isClosed || keys.isEmpty()) return true
        initJob.await()
        val shardsToFlush = keys.map { getShard(it) }.distinct()
        return withContext(Dispatchers.IO) {
            shardsToFlush.map { async { it.flush() } }.awaitAll().all { it }
        }
    }

    /** 安全关闭（应用退出前务必调用） */
    suspend fun close() {
        if (isClosed) return
        isClosed = true
        try {
            flush()
        } finally {
            shards.forEach { it.close() }
            scope.coroutineContext[Job]?.cancel()
        }
    }
}

// =============== 内部：分片实现（锁与协程安全设计） ===============
private class Shard(
    private val file: File,
    private val json: Json,
    private val enableCompression: Boolean,
    private val writeDelay: Duration,
    private val coroutineContext: CoroutineContext
) {
    private val logHelper = this.logger
    // ===== 脏标记（关键优化）=====
    private var isDirty = false // 标记缓存是否需持久化
    private val cache = mutableMapOf<String, JsonElement>()
    private val mutex = Mutex()
    private val scope = CoroutineScope(coroutineContext)
    private var pendingWriteJob: Job? = null
    private var isClosed = false

    /** 异步加载分片数据 */
    suspend fun loadAsync() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            mutex.withLock { isDirty = false }
            return@withContext
        }
        try {
            val content = if (enableCompression) {
                GZIPInputStream(file.inputStream()).use { it.reader().readText() }
            } else {
                file.readText()
            }
            if (content.isNotBlank()) {
                mutex.withLock {
                    cache.clear()
                    cache.putAll(json.decodeFromString(content))
                    isDirty = false // ✅ 加载完成 = 与磁盘一致
                    logHelper.info("📦 分片加载: ${file.name} | 项数: ${cache.size}")
                }
            }
        } catch (e: Exception) {
            logHelper.error("⚠️ 分片加载失败 [${file.name}]: ${e.message} | 文件已清理")
            file.delete()
            mutex.withLock { isDirty = false } // 失败后视为干净（空缓存）
        }
    }

    /** 安全获取缓存大小（需在协程中调用） */
    suspend fun getCacheSize(): Int = mutex.withLock { cache.size }

    /** 非阻塞调试用（允许轻微不一致，绝不阻塞线程） */
    val unsafeCacheSize: Int
        get() = cache.size // 仅用于日志/监控，标注"unsafe"

    suspend fun <T> get(key: String, serializer: KSerializer<T>): T? =
        mutex.withLock { cache[key]?.let { json.decodeFromJsonElement(serializer, it) } }

    suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>): Boolean =
        mutex.withLock {
            val oldValue = cache[key]
            val newValue = json.encodeToJsonElement(serializer, value)
            // 仅当值真正变化时标记脏位（避免无意义写入）
            if (oldValue != newValue) {
                cache[key] = newValue
                isDirty = true
                scheduleWrite()
                true
            } else {
                false // 值未变，跳过
            }
        }

    suspend fun delete(key: String): Boolean =
        mutex.withLock {
            if (cache.remove(key) != null) {
                isDirty = true
                scheduleWrite()
                true
            } else false
        }

    suspend fun clear() = mutex.withLock {
        if (cache.isNotEmpty()) {
            cache.clear()
            isDirty = true
            scheduleWrite()
        }
    }

    suspend fun flush(): Boolean = mutex.withLock {
        if (isClosed || !isDirty) {
            if (!isDirty) logHelper.debug("⏭️ 跳过写入 [${file.name}]：缓存未变化")
            return@withLock true // 无变化视为成功
        }
        pendingWriteJob?.cancelAndJoin()
        pendingWriteJob = null
        val success = writeSnapshotLocked(cache.toMap())
        if (success) isDirty = false
        success
    }

    // ===== 调度写入：前置脏检查 =====
    private fun scheduleWrite() {
        if (isClosed || !isDirty) return // ✅ 无变化不调度
        pendingWriteJob?.cancel()
        pendingWriteJob = scope.launch {
            try {
                delay(writeDelay)
                if (isClosed) return@launch
                mutex.withLock {
                    if (isClosed || !isDirty) return@launch // 延迟期间可能已被 flush
                    val success = writeSnapshotLocked(cache.toMap())
                    if (success) isDirty = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logHelper.error("❌ 分片写入异常 [${file.name}]: ${e.message}")
            }
        }
    }


    fun close() {
        isClosed = true
        pendingWriteJob?.cancel()
    }

    // ✅ 锁内专用写入（由 mutex 保护调用）
    private suspend fun writeSnapshotLocked(snapshot: Map<String, JsonElement>): Boolean =
        withContext(Dispatchers.IO) {
            if (isClosed) return@withContext false
            try {
                val tempFile = File("${file.absolutePath}.tmp")
                val content = json.encodeToString(snapshot)

                if (enableCompression) {
                    GZIPOutputStream(tempFile.outputStream()).use {
                        it.write(content.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    tempFile.writeText(content, Charsets.UTF_8)
                }

                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                val sizeKB = file.length() / 1024
                logHelper.info("分片持久化: ${file.name} | 项数: ${snapshot.size} | 大小: ${if (sizeKB > 0) "${sizeKB}KB" else "<1KB"}")
                true
            } catch (e: Exception) {
                logHelper.error("磁盘写入失败 [${file.name}]: ${e.message}")
                false
            }
        }
}