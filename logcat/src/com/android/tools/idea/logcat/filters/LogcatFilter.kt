/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.PackageNamesProvider
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.ASSERT
import com.android.tools.idea.logcat.message.LogLevel.ERROR
import com.android.tools.idea.logcat.message.LogcatMessage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.PsiErrorElementImpl
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.regex.PatternSyntaxException

/**
 * The top level filter that prepares and executes a [LogcatFilter]
 */
internal class LogcatMasterFilter(private val logcatFilter: LogcatFilter?) {

  fun filter(messages: List<LogcatMessage>, zoneId: ZoneId = ZoneId.systemDefault()): List<LogcatMessage> {
    if (logcatFilter == null) {
      return messages
    }
    logcatFilter.prepare()
    return messages.filter { it.header === SYSTEM_HEADER || logcatFilter.matches(LogcatMessageWrapper(it, zoneId)) }
  }
}

/**
 * Matches a [LogcatMessage]
 */
internal abstract class LogcatFilter(open val textRange: TextRange) {
  /**
   * Prepare the filter.
   *
   * Some filters need to perform some initial setup before running. To avoid doing the setup for each message, the [LogcatMasterFilter]
   * wil call [#prepare] once for each batch of messages.
   */
  open fun prepare() {}

  abstract fun matches(message: LogcatMessageWrapper): Boolean

  open fun getFilterName(): String? = null

  open fun findFilterForOffset(offset: Int): LogcatFilter? {
    return if (textRange.contains(offset)) this else null
  }

  companion object {
    const val MY_PACKAGE = "package:mine"
  }
}

internal abstract class ParentFilter(open val filters: List<LogcatFilter>)
  : LogcatFilter(TextRange(filters.first().textRange.startOffset, filters.last().textRange.endOffset)) {
  override fun prepare() {
    filters.forEach(LogcatFilter::prepare)
  }

  override fun getFilterName(): String? = filters.mapNotNull { it.getFilterName() }.lastOrNull()

  override fun findFilterForOffset(offset: Int): LogcatFilter? {
    return if (textRange.contains(offset)) filters.firstNotNullOfOrNull { it.findFilterForOffset(offset) } else null
  }
}

internal data class AndLogcatFilter(override val filters: List<LogcatFilter>) : ParentFilter(filters) {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.all { it.matches(message) }
}

internal data class OrLogcatFilter(override val filters: List<LogcatFilter>) : ParentFilter(filters) {
  constructor(vararg filters: LogcatFilter) : this(filters.asList())

  override fun matches(message: LogcatMessageWrapper) = filters.any { it.matches(message) }
}

internal enum class LogcatFilterField {
  TAG {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.tag
  },
  APP {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.applicationId
  },
  MESSAGE {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.message
  },
  LINE {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  },
  IMPLICIT_LINE {
    override fun getValue(message: LogcatMessageWrapper) = message.logLine
  },
  PROCESS {
    override fun getValue(message: LogcatMessageWrapper) = message.logcatMessage.header.processName
  },
  ;

  abstract fun getValue(message: LogcatMessageWrapper): String
}

internal data class StringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message).contains(string, ignoreCase = true)
}

internal data class NegatedStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) = !field.getValue(message).contains(string, ignoreCase = true)
}

internal data class ExactStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) == string
}

internal data class NegatedExactStringFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) = field.getValue(message) != string
}

internal data class RegexFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = regex.containsMatchIn(field.getValue(message))
}

internal data class NegatedRegexFilter(
  val string: String,
  val field: LogcatFilterField,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  private val regex = try {
    string.toRegex()
  }
  catch (e: PatternSyntaxException) {
    throw LogcatFilterParseException(PsiErrorElementImpl("Invalid regular expression: $string"))
  }

  override fun matches(message: LogcatMessageWrapper) = !regex.containsMatchIn(field.getValue(message))
}

internal data class LevelFilter(
  val level: LogLevel,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) = message.logcatMessage.header.logLevel >= level
}

internal data class AgeFilter(
  val age: Duration,
  private val clock: Clock,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper) =
    clock.millis() - message.logcatMessage.header.timestamp.toEpochMilli() <= age.toMillis()
}

/**
 * A special filter that matches the appName field in a [LogcatMessage] against a list of package names from the project.
 */
internal class ProjectAppFilter(
  private val packageNamesProvider: PackageNamesProvider,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  private var packageNames: Set<String> = emptySet()
  private var packageNamesRegex: Regex? = null

  override fun prepare() {
    packageNames = packageNamesProvider.getPackageNames()
    packageNamesRegex = if (packageNames.isNotEmpty()) packageNames.joinToString("|") { it.replace(".", "\\.") }.toRegex() else null
  }

  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logcatMessage.header
    return packageNames.contains(header.getAppName())
           || (header.logLevel >= ERROR && packageNamesRegex?.containsMatchIn(message.logcatMessage.message) == true)
  }

  override fun equals(other: Any?) = other is ProjectAppFilter && packageNamesProvider == other.packageNamesProvider

  override fun hashCode() = packageNamesProvider.hashCode()
}

/*
  A JVM crash looks like:

    2022-04-19 10:20:30.892 13253-13253/com.example.nativeapplication E/AndroidRuntime: FATAL EXCEPTION: main
      Process: com.example.nativeapplication, PID: 13253
      java.lang.RuntimeException: ...
        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3449)
        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3601)
    etc

  A native crash looks like:

  2022-04-19 10:24:34.051 13445-13445/com.example.nativeapplication A/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 13445 (tiveapplication), pid 13445 (tiveapplication)
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: Build fingerprint: 'google/sdk_gphone_x86_64/generic_x86_64_arm64:11/RSR1.201211.001/7027799:user/release-keys'
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: Revision: '0'
  2022-04-19 10:24:34.092 13474-13474/? A/DEBUG: ABI: 'x86_64'
  2022-04-19 10:24:34.095 13474-13474/? A/DEBUG: Timestamp: 2022-04-19 10:24:34-0700
  etc
*/
internal data class CrashFilter(override val textRange: TextRange) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper): Boolean {
    val header = message.logcatMessage.header
    val level = header.logLevel
    val tag = header.tag
    return (level == ERROR && tag == "AndroidRuntime" && message.logcatMessage.message.startsWith("FATAL EXCEPTION"))
           || (level == ASSERT && (tag == "DEBUG" || tag == "libc"))
  }
}

internal data class NameFilter(
  val name: String,
  override val textRange: TextRange,
) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper): Boolean = true

  override fun getFilterName(): String = name
}

private val EXCEPTION_LINE_PATTERN = Regex("\n\\s*at .+\\(.+\\)\n")

internal data class StackTraceFilter(override val textRange: TextRange) : LogcatFilter(textRange) {
  override fun matches(message: LogcatMessageWrapper): Boolean = EXCEPTION_LINE_PATTERN.find(message.logcatMessage.message) != null
}
