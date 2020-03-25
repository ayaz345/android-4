/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture


class OpenFileAtLocationQuickFix(val myFilePosition: FilePosition) : BuildIssueQuickFix {
  override val id = "OPEN_FILE"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val projectFile = project.projectFile ?: return CompletableFuture.completedFuture<Any>(null)
    invokeLater {
      val file = projectFile.parent.fileSystem.findFileByPath(myFilePosition.file.path)
      if (file != null) {
        val openFile = OpenFileDescriptor(project, file, myFilePosition.startLine, myFilePosition.startColumn, false)
        if (openFile.canNavigate()) {
          openFile.navigate(true)
        }
      }
    }
    return CompletableFuture.completedFuture<Any>(null)
  }
}