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
package com.android.tools.compose

import com.android.testutils.MockitoKt.mock
import com.android.tools.compose.debug.ComposePositionManager
import com.android.tools.compose.debug.ComposePositionManagerFactory
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.debugger.SourcePosition
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test

class ComposePositionManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val project: Project
    get() = projectRule.project

  @Test
  fun testComposeSingletonClasses() {
    val source = """
      package a;
      import androidx.compose.runtime.Composable

      class A {
        fun f() {
          g {
           f()
          }
        }

        fun g(@Composable () -> Unit) {}
      }
    """.trimIndent()
    val file = projectRule.fixture.addFileToProject("src/a/test.kt", source)

    val debugProcess = mockDebugProcess(project) {
      type("a.A") {
        method("f", 4, 5, 6, 7, 8)
        method("g", 10)
      }

      type("a.ComposableSingletons\$TestKt")

      type("a.ComposableSingletons\$TestKt\$lambda-1") {
        method("invoke", 5, 6, 7)
      }
    }
    val composePositionManager =
      ComposePositionManagerFactory().createPositionManager(debugProcess) as ComposePositionManager

    val sourcePosition = SourcePosition.createFromLine(file, 5)

    composePositionManager.createPrepareRequests(mock(), sourcePosition)
    assert(debugProcess.prepareRequestPatterns.any { it == "a.ComposableSingletons\$TestKt\$*" })
    val referenceTypes = composePositionManager.getAllClasses(sourcePosition).map { it.name() }
    assert("a.ComposableSingletons\$TestKt\$lambda-1" in referenceTypes)
    assert("a.ComposableSingletons\$TestKt" !in referenceTypes)
    assert("a.A" in referenceTypes)
  }

  @Test
  fun testComposeSingletonClassesJvmName() {
    val source = """
      @file:JvmName("FileClass")
      package a;
      import androidx.compose.runtime.Composable

      fun f() {
        g {
          f()
        }
      }

      fun g(@Composable () -> Unit) {}
    """.trimIndent()
    val file = projectRule.fixture.addFileToProject("src/a/test2.kt", source)

    val debugProcess = mockDebugProcess(project) {
      type("a.FileClass") {
        method("f", 4, 5, 6, 7, 8)
        method("g", 10)
      }

      type("a.ComposableSingletons\$Test2Kt")

      type("a.ComposableSingletons\$Test2Kt\$lambda-1") {
        method("invoke", 5, 6, 7)
      }
    }
    val composePositionManager =
      ComposePositionManagerFactory().createPositionManager(debugProcess) as ComposePositionManager

    val sourcePosition = SourcePosition.createFromLine(file, 5)
    composePositionManager.createPrepareRequests(mock(), sourcePosition)
    assert(debugProcess.prepareRequestPatterns.any { it == "a.ComposableSingletons\$Test2Kt\$*" })
    val referenceTypes = composePositionManager.getAllClasses(sourcePosition).map { it.name() }
    assert("a.ComposableSingletons\$Test2Kt\$lambda-1" in referenceTypes)
    assert("a.ComposableSingletons\$Test2Kt" !in referenceTypes)
    assert("a.FileClass" in referenceTypes)
  }
}
