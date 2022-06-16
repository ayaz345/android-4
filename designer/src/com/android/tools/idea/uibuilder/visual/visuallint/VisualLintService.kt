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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderAsyncActionExecutor
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater.updateHierarchy
import com.android.tools.idea.uibuilder.visual.WindowSizeModelsProvider
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LocaleAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzer
import java.util.concurrent.CompletableFuture
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.lang.IllegalArgumentException

/**
 * Service that runs visual lints
 */
@Service
class VisualLintService(project: Project) {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintService {
      return project.getService(VisualLintService::class.java)
    }
  }

  /** Default issue provider for Visual Lint Service. */
  val issueProvider = VisualLintIssueProvider(project)

  private val basicAnalyzers = listOf(BoundsAnalyzer, BottomNavAnalyzer, BottomAppBarAnalyzer, TextFieldSizeAnalyzer,
                                      OverlapAnalyzer, LongTextAnalyzer, ButtonSizeAnalyzer)

  fun removeIssues(surface: DesignSurface<*>) {
    surface.issueModel.removeIssueProvider(issueProvider)
  }

  /**
   * Run visual lint analysis and return the list of issues.
   */
  fun runVisualLintAnalysis(models: List<NlModel>, issueModel: IssueModel) {
    issueModel.removeIssueProvider(issueProvider)
    issueProvider.clear()
    if (models.isEmpty()) {
      return
    }

    issueModel.addIssueProvider(issueProvider, false)
    val displayingModel = models[0]
    displayingModel.addListener(object: ModelListener {
      override fun modelChanged(model: NlModel) {
        RenderService.getRenderAsyncActionExecutor().cancelLowerPriorityActions(RenderAsyncActionExecutor.RenderingPriority.LOW)
      }
    })
    val modelsToAnalyze = WindowSizeModelsProvider.createNlModels(displayingModel, displayingModel.file, displayingModel.facet )

    for (model in modelsToAnalyze) {
      inflate(model).thenComposeAsync({ result ->
        if (result == null) {
          // already logged error above
          return@thenComposeAsync CompletableFuture.completedFuture(null)
        }

        updateHierarchy(result, model)
        analyzeAfterModelUpdate(result, model, VisualLintBaseConfigIssues(), VisualLintAnalyticsManager(null))

        return@thenComposeAsync CompletableFuture.completedFuture(null)
      }, AppExecutorUtil.getAppExecutorService()).thenAccept {
        // TODO: This might be triggered too frequently (4 times).
        issueModel.updateErrorsList()
      }
    }
  }

  /**
   * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
   */
  fun analyzeAfterModelUpdate(result: RenderResult,
                              model: NlModel,
                              baseConfigIssues: VisualLintBaseConfigIssues,
                              analyticsManager: VisualLintAnalyticsManager) {
    basicAnalyzers.forEach {
      val issues = it.analyze(result, model, analyticsManager)
      issueProvider.addAllIssues(it.type, issues)
    }
    LocaleAnalyzer(baseConfigIssues).let { issueProvider.addAllIssues(it.type, it.analyze(result, model, analyticsManager)) }
    if (StudioFlags.NELE_ATF_IN_VISUAL_LINT.get()) {
      AtfAnalyzer.analyze(result, model, issueProvider)
    }
  }
}

/**
 * Inflate a view, then return the completable future with render result.
 */
fun inflate(model: NlModel): CompletableFuture<RenderResult> {
  val renderService = RenderService.getInstance(model.project)
  val logger = renderService.createLogger(model.facet)

  return renderService.taskBuilder(model.facet, model.configuration)
    .withPsiFile(model.file)
    .withLayoutScanner(false)
    .withLogger(logger)
    .withPriority(RenderAsyncActionExecutor.RenderingPriority.LOW)
    .build().thenCompose { newTask ->
      if (newTask == null) {
        logger.error("INFLATE", "Error inflating view for visual lint on background. No RenderTask Created.",
        null, null, null)
        return@thenCompose CompletableFuture.failedFuture(IllegalArgumentException())
      }

      // TODO: Potentially save this task for future?
      return@thenCompose newTask.inflate().whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result.renderResult.exception
        if (exception != null || result == null) {
          logger.error("INFLATE", "Error inflating views for visual lint on background", exception, null, null)
        }
        newTask.dispose()
      }
    }
}
