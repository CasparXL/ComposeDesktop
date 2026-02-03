package com.caspar.compose.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/* =============== Logger 获取扩展 =============== */
/**
 * 实例级 Logger（自动使用当前实例的精确类名）
 * ✅ 使用：private val logger = this.logger （类内缓存）
 */
val Any.logger: Logger
    get() = LoggerFactory.getLogger(this.javaClass)

/**
 * KClass Logger（伴生对象/单例专用）
 * ✅ 使用：private val logger = MyClass::class.logger
 */
val KClass<*>.logger: Logger
    get() = LoggerFactory.getLogger(this.java)

// ===== 无异常版本：仅当级别启用时执行 lambda =====
inline fun Logger.trace(msg: () -> String) { if (isTraceEnabled) trace(msg()) }
inline fun Logger.debug(msg: () -> String) { if (isDebugEnabled) debug(msg()) }
inline fun Logger.info(msg: () -> String)  { if (isInfoEnabled)  info(msg()) }
inline fun Logger.warn(msg: () -> String)  { if (isWarnEnabled)  warn(msg()) }
inline fun Logger.error(msg: () -> String) { if (isErrorEnabled) error(msg()) }

// ===== 带异常版本：异常参数前置，支持 Kotlin 括号外 lambda =====
inline fun Logger.trace(t: Throwable, msg: () -> String) { if (isTraceEnabled) trace(msg(), t) }
inline fun Logger.debug(t: Throwable, msg: () -> String) { if (isDebugEnabled) debug(msg(), t) }
inline fun Logger.info(t: Throwable, msg: () -> String)  { if (isInfoEnabled)  info(msg(), t) }
inline fun Logger.warn(t: Throwable, msg: () -> String)  { if (isWarnEnabled)  warn(msg(), t) }
inline fun Logger.error(t: Throwable, msg: () -> String) { if (isErrorEnabled) error(msg(), t) }

// ===== 仅异常版本（自动提取异常消息）=====
fun Logger.trace(t: Throwable) { if (isTraceEnabled) trace(t.message ?: "Trace exception", t) }
fun Logger.debug(t: Throwable) { if (isDebugEnabled) debug(t.message ?: "Debug exception", t) }
fun Logger.info(t: Throwable)  { if (isInfoEnabled)  info(t.message ?: "Info exception", t) }
fun Logger.warn(t: Throwable)  { if (isWarnEnabled)  warn(t.message ?: "Warning", t) }
fun Logger.error(t: Throwable) { if (isErrorEnabled) error(t.message ?: "Unexpected error", t) }