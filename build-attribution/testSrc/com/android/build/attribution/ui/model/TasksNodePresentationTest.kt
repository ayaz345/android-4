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
package com.android.build.attribution.ui.model

import com.android.build.attribution.ui.MockUiData
import com.android.build.attribution.ui.data.builder.TaskIssueUiDataContainer
import com.android.build.attribution.ui.mockTask
import com.android.build.attribution.ui.view.BuildAnalyzerTreeNodePresentation
import com.google.common.truth.Truth.assertThat
import org.junit.Test


class TasksNodePresentationTest {

  @Test
  fun testTaskWithoutWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200)
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.UNGROUPED)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      showWarnIcon = false,
      rightAlignedSuffix = "1.200 s",
      showChartKey = true
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskWithWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.UNGROUPED)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      showWarnIcon = true,
      rightAlignedSuffix = "1.200 s",
      showChartKey = true
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testPluginWithoutWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 800)
    val plugin = MockUiData().createPluginData("resources.plugin", listOf(task))

    val descriptor = PluginDetailsNodeDescriptor(plugin)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "resources.plugin",
      suffix = "",
      showWarnIcon = false,
      rightAlignedSuffix = "0.800 s",
      showChartKey = true
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testPluginWithWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 800)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val plugin = MockUiData().createPluginData("resources.plugin", listOf(task))

    val descriptor = PluginDetailsNodeDescriptor(plugin)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = "resources.plugin",
      suffix = "1 warning",
      showWarnIcon = false,
      rightAlignedSuffix = "0.800 s",
      showChartKey = true
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskUnderPluginWithoutWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200)
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.BY_PLUGIN)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      showWarnIcon = false,
      rightAlignedSuffix = "1.200 s",
      showChartKey = false
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }

  @Test
  fun testTaskUnderPluginWithWarningPresentation() {
    val task = mockTask(":app", "resources", "resources.plugin", 1200)
    task.issues = listOf(TaskIssueUiDataContainer.AlwaysRunNoOutputIssue(task))
    val descriptor = TaskDetailsNodeDescriptor(task, TasksDataPageModel.Grouping.BY_PLUGIN)

    val expectedPresentation = BuildAnalyzerTreeNodePresentation(
      mainText = ":app:resources",
      suffix = "",
      showWarnIcon = true,
      rightAlignedSuffix = "1.200 s",
      showChartKey = false
    )
    assertThat(descriptor.presentation).isEqualTo(expectedPresentation)
  }
}
