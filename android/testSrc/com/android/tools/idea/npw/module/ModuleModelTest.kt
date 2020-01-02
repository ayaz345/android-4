/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.npw.java.NewLibraryModuleModel
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.templates.Template
import com.android.tools.idea.templates.TemplateManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTestCase.invokeGradle
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import junit.framework.TestCase
import org.jetbrains.android.util.AndroidBundle

class ModuleModelTest : AndroidGradleTestCase() {
  private val projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()

  private val multiTemplateRenderer: MultiTemplateRenderer get() = MultiTemplateRenderer { renderer ->
    object : Task.Modal(project, AndroidBundle.message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        renderer(project)
      }
    }.queue()
    projectSyncInvoker.syncProject(project)
  }

  fun testInitFillsAllTheDataForLibraryModule() {
    loadSimpleApplication()

    val libraryModuleModel = NewLibraryModuleModel(project, projectSyncInvoker).apply {
      packageName.set("com.google.lib")
    }

    multiTemplateRenderer.requestRender(libraryModuleModel.renderer)

    val module = ModuleManager.getInstance(myFixture.project).findModuleByName("lib")
    val modulesToCompile = arrayOf(module)

    val invocationResult = invokeGradle(project) {
      it.compileJava(modulesToCompile, TestCompileType.UNIT_TESTS)
    }
    TestCase.assertTrue(invocationResult.isBuildSuccessful)
  }
}
