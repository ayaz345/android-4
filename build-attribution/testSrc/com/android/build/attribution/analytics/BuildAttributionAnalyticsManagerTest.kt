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
package com.android.build.attribution.analytics

import com.android.build.attribution.analyzers.BuildEventsAnalysisResult
import com.android.build.attribution.analyzers.createBinaryPluginIdentifierStub
import com.android.build.attribution.analyzers.createScriptPluginIdentifierStub
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginConfigurationData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.build.attribution.ui.data.builder.AbstractBuildAttributionReportBuilderTest
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AlwaysRunTasksAnalyzerData
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AnnotationProcessorsAnalyzerData
import com.google.wireless.android.sdk.stats.BuildAttribuitionTaskIdentifier
import com.google.wireless.android.sdk.stats.BuildAttributionPluginIdentifier
import com.google.wireless.android.sdk.stats.CriticalPathAnalyzerData
import com.google.wireless.android.sdk.stats.ProjectConfigurationAnalyzerData
import com.google.wireless.android.sdk.stats.TasksConfigurationIssuesAnalyzerData
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.time.Duration

class BuildAttributionAnalyticsManagerTest {
  @Mock
  private lateinit var project: Project

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private val applicationPlugin = PluginData(createBinaryPluginIdentifierStub("com.android.application"), ":app")
  private val pluginA = PluginData(createBinaryPluginIdentifierStub("pluginA"), ":app")
  private val buildScript = PluginData(createScriptPluginIdentifierStub("build.gradle"), ":app")

  val pluginATask = TaskData("dummyTask1", "", pluginA, 0, 100, TaskData.TaskExecutionMode.FULL, emptyList()).apply {
    setTaskType("com.example.test.DummyTask")
  }
  val buildScriptTask = TaskData("dummyTask2", "", buildScript, 0, 400, TaskData.TaskExecutionMode.FULL, emptyList()).apply {
    setTaskType("com.example.test.DummyTask")
  }

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    UsageTracker.setWriterForTest(tracker)

    `when`(project.basePath).thenReturn("test")
    val moduleManager = Mockito.mock(ModuleManager::class.java)
    `when`(project.getComponent(ModuleManager::class.java)).thenReturn(moduleManager)
    `when`(moduleManager.modules).thenReturn(emptyArray<Module>())
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  private fun getAnalyzersData(): BuildEventsAnalysisResult {
    return object : AbstractBuildAttributionReportBuilderTest.MockResultsProvider() {

      override fun getNonIncrementalAnnotationProcessorsData() = listOf(
        AnnotationProcessorData("com.example.processor", Duration.ofMillis(1234)))

      override fun getTotalBuildTimeMs() = 123456L

      override fun getCriticalPathTasks() = listOf(pluginATask, pluginATask)

      override fun getPluginsDeterminingBuildDuration() = listOf(PluginBuildData(applicationPlugin, 891), PluginBuildData(pluginA, 234))

      override fun getProjectsConfigurationData() = listOf(
        ProjectConfigurationData(":app", 891,
                                 listOf(PluginConfigurationData(applicationPlugin, 567),
                                        PluginConfigurationData(pluginA, 890)),
                                 listOf(ProjectConfigurationData.ConfigurationStep(
                                   ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS, 123),
                                        ProjectConfigurationData.ConfigurationStep(
                                          ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS, 456))))

      override fun getTotalConfigurationData() =
        ProjectConfigurationData("Total Configuration Data", 891,
                                 listOf(PluginConfigurationData(applicationPlugin, 567),
                                        PluginConfigurationData(pluginA, 890)),
                                 listOf(ProjectConfigurationData.ConfigurationStep(
                                   ProjectConfigurationData.ConfigurationStep.Type.NOTIFYING_BUILD_LISTENERS, 123),
                                        ProjectConfigurationData.ConfigurationStep(
                                          ProjectConfigurationData.ConfigurationStep.Type.EXECUTING_BUILD_SCRIPT_BLOCKS, 456)))

      override fun getTasksDeterminingBuildDuration(): List<TaskData> = listOf(pluginATask, buildScriptTask)

      override fun getAlwaysRunTasks() = listOf(AlwaysRunTaskData(pluginATask, AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE))

      override fun getTasksSharingOutput() = listOf(TasksSharingOutputData("test", listOf(pluginATask, buildScriptTask)))
    }
  }

  @Test
  fun testAnalyzersDataMetricsReporting() {
    BuildAttributionAnalyticsManager("46f89941-2cea-83d7-e613-0c5823be215a", project).use { analyticsManager ->
      analyticsManager.logAnalyzersData(getAnalyzersData())
    }

    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_STATS }
    assertThat(buildAttributionEvents).hasSize(1)

    val buildAttributionAnalyzersData = buildAttributionEvents.first().studioEvent.buildAttributionStats.buildAttributionAnalyzersData
    assertThat(buildAttributionAnalyzersData.totalBuildTimeMs).isEqualTo(123456)

    checkAlwaysRunTasksAnalyzerData(buildAttributionAnalyzersData.alwaysRunTasksAnalyzerData)
    checkAnnotationProcessorsAnalyzerData(buildAttributionAnalyzersData.annotationProcessorsAnalyzerData)
    checkCriticalPathAnalyzerData(buildAttributionAnalyzersData.criticalPathAnalyzerData)
    checkProjectConfigurationAnalyzerData(buildAttributionAnalyzersData.projectConfigurationAnalyzerData)
    checkConfigurationIssuesAnalyzerData(buildAttributionAnalyzersData.tasksConfigurationIssuesAnalyzerData)

    val buildAttributionReportSessionId = buildAttributionEvents.first().studioEvent.buildAttributionStats.buildAttributionReportSessionId
    assertThat(buildAttributionReportSessionId).isEqualTo("46f89941-2cea-83d7-e613-0c5823be215a")
  }

  private fun checkAlwaysRunTasksAnalyzerData(analyzerData: AlwaysRunTasksAnalyzerData) {
    assertThat(analyzerData.alwaysRunTasksList).hasSize(1)
    assertThat(isTheSameTask(analyzerData.alwaysRunTasksList.first().taskIdentifier, pluginATask)).isTrue()
  }

  private fun checkAnnotationProcessorsAnalyzerData(analyzerData: AnnotationProcessorsAnalyzerData) {
    assertThat(analyzerData.nonIncrementalAnnotationProcessorsList).hasSize(1)
    assertThat(analyzerData.nonIncrementalAnnotationProcessorsList.first().annotationProcessorClassName).isEqualTo("com.example.processor")
    assertThat(analyzerData.nonIncrementalAnnotationProcessorsList.first().compilationDurationMs).isEqualTo(1234)
  }

  private fun checkCriticalPathAnalyzerData(analyzerData: CriticalPathAnalyzerData) {
    assertThat(analyzerData.criticalPathDurationMs).isEqualTo(200)
    assertThat(analyzerData.tasksDeterminingBuildDurationMs).isEqualTo(500)
    assertThat(analyzerData.numberOfTasksOnCriticalPath).isEqualTo(2)
    assertThat(analyzerData.pluginsCriticalPathList).hasSize(2)
    assertThat(analyzerData.pluginsCriticalPathList[0].buildDurationMs).isEqualTo(891)
    assertThat(isTheSamePlugin(analyzerData.pluginsCriticalPathList[0].pluginIdentifier, applicationPlugin)).isTrue()
    assertThat(analyzerData.pluginsCriticalPathList[1].buildDurationMs).isEqualTo(234)
    assertThat(isTheSamePlugin(analyzerData.pluginsCriticalPathList[1].pluginIdentifier, pluginA)).isTrue()
  }

  private fun checkProjectConfigurationAnalyzerData(analyzerData: ProjectConfigurationAnalyzerData) {
    assertThat(analyzerData.projectConfigurationDataList).hasSize(1)

    assertThat(analyzerData.projectConfigurationDataList[0]).isEqualTo(analyzerData.overallConfigurationData)

    assertThat(analyzerData.projectConfigurationDataList[0].configurationTimeMs).isEqualTo(891)
    assertThat(analyzerData.projectConfigurationDataList[0].pluginsConfigurationDataList).hasSize(2)
    assertThat(analyzerData.projectConfigurationDataList[0].pluginsConfigurationDataList[0].pluginConfigurationTimeMs).isEqualTo(567)
    assertThat(isTheSamePlugin(analyzerData.projectConfigurationDataList[0].pluginsConfigurationDataList[0].pluginIdentifier,
                               applicationPlugin)).isTrue()
    assertThat(analyzerData.projectConfigurationDataList[0].pluginsConfigurationDataList[1].pluginConfigurationTimeMs).isEqualTo(890)
    assertThat(
      isTheSamePlugin(analyzerData.projectConfigurationDataList[0].pluginsConfigurationDataList[1].pluginIdentifier, pluginA)).isTrue()

    assertThat(analyzerData.projectConfigurationDataList[0].configurationStepsList).hasSize(2)
    assertThat(analyzerData.projectConfigurationDataList[0].configurationStepsList[0].configurationTimeMs).isEqualTo(123)
    assertThat(analyzerData.projectConfigurationDataList[0].configurationStepsList[1].configurationTimeMs).isEqualTo(456)
    assertThat(analyzerData.projectConfigurationDataList[0].configurationStepsList[0].type).isEqualTo(
      ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.NOTIFYING_BUILD_LISTENERS)
    assertThat(analyzerData.projectConfigurationDataList[0].configurationStepsList[1].type).isEqualTo(
      ProjectConfigurationAnalyzerData.ConfigurationStep.StepType.EXECUTING_BUILD_SCRIPT_BLOCKS)
  }

  private fun checkConfigurationIssuesAnalyzerData(analyzerData: TasksConfigurationIssuesAnalyzerData) {
    assertThat(analyzerData.tasksSharingOutputDataList).hasSize(1)
    assertThat(analyzerData.tasksSharingOutputDataList[0].tasksSharingOutputList).hasSize(2)
    assertThat(isTheSameTask(analyzerData.tasksSharingOutputDataList[0].tasksSharingOutputList[0], pluginATask)).isTrue()
    assertThat(isTheSameTask(analyzerData.tasksSharingOutputDataList[0].tasksSharingOutputList[1], buildScriptTask)).isTrue()
  }

  private fun isTheSamePlugin(pluginIdentifier: BuildAttributionPluginIdentifier, pluginData: PluginData): Boolean {
    return when (pluginData.pluginType) {
      PluginData.PluginType.UNKNOWN -> pluginIdentifier.type == BuildAttributionPluginIdentifier.PluginType.UNKNOWN_TYPE &&
                                       pluginIdentifier.pluginDisplayName == ""
      PluginData.PluginType.BINARY_PLUGIN, PluginData.PluginType.BUILDSRC_PLUGIN -> pluginIdentifier.type == BuildAttributionPluginIdentifier.PluginType.BINARY_PLUGIN &&
                                                                                    pluginIdentifier.pluginDisplayName == pluginData.displayName
      PluginData.PluginType.SCRIPT -> pluginIdentifier.type == BuildAttributionPluginIdentifier.PluginType.BUILD_SCRIPT &&
                                      pluginIdentifier.pluginDisplayName == ""
    }
  }

  private fun isTheSameTask(taskIdentifier: BuildAttribuitionTaskIdentifier, taskData: TaskData): Boolean {
    return isTheSamePlugin(taskIdentifier.originPlugin, taskData.originPlugin) && taskIdentifier.taskClassName == "DummyTask"
  }
}
