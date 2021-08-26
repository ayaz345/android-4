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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.ui.HideablePanel
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServicesAdapter
import com.android.tools.idea.appinspection.inspectors.backgroundtask.ide.IntellijUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTestUtils.sendWorkAddedEvent
import com.android.tools.idea.appinspection.inspectors.backgroundtask.view.BackgroundTaskInspectorTestUtils.sendWorkEvent
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class EntryDetailsViewTest {
  private class TestIdeServices : AppInspectionIdeServicesAdapter() {
    var lastVisitedCodeLocation: AppInspectionIdeServices.CodeLocation? = null

    override suspend fun navigateTo(codeLocation: AppInspectionIdeServices.CodeLocation) {
      lastVisitedCodeLocation = codeLocation
    }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope
  private lateinit var client: BackgroundTaskInspectorClient
  private lateinit var ideServices: TestIdeServices
  private lateinit var tab: BackgroundTaskInspectorTab
  private lateinit var uiDispatcher: ExecutorCoroutineDispatcher
  private lateinit var detailsView: EntryDetailsView
  private lateinit var selectionModel: EntrySelectionModel

  @Before
  fun setUp() = runBlocking {
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher() + SupervisorJob())
    uiDispatcher = EdtExecutorService.getInstance().asCoroutineDispatcher()
    withContext(uiDispatcher) {
      client = BackgroundTaskInspectorTestUtils.getFakeClient(scope)
      ideServices = TestIdeServices()
      tab = BackgroundTaskInspectorTab(client, ideServices, IntellijUiComponentsProvider(projectRule.project), scope, uiDispatcher)
      tab.isDetailsViewVisible = true
      detailsView = tab.component.secondComponent as EntryDetailsView
      tab.isDetailsViewVisible = false
      selectionModel = detailsView.selectionModel
    }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun workEntrySelected() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)

      val descriptionPanel = detailsView.getCategoryPanel("Description") as JPanel
      val classComponent = descriptionPanel.getValueComponent("Class") as HyperlinkLabel
      assertThat(classComponent.text).isEqualTo(workInfo.workerClassName)
      classComponent.doClick()
      assertThat(ideServices.lastVisitedCodeLocation!!.fqcn).isEqualTo(workInfo.workerClassName)
      val tagsComponent = descriptionPanel.getValueComponent("Tags") as JPanel
      assertThat(tagsComponent.componentCount).isEqualTo(2)
      assertThat((tagsComponent.getComponent(0) as JLabel).text).isEqualTo("\"tag1\"")
      assertThat((tagsComponent.getComponent(1) as JLabel).text).isEqualTo("\"tag2\"")
      val idComponent = descriptionPanel.getValueComponent("UUID") as JLabel
      assertThat(idComponent.text).isEqualTo(workInfo.id)

      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val enqueuedAtComponent = executionPanel.getValueComponent("Enqueued by") as HyperlinkLabel
      assertThat(enqueuedAtComponent.text).isEqualTo("File1 (12)")
      enqueuedAtComponent.doClick()
      assertThat(ideServices.lastVisitedCodeLocation!!.fileName).isEqualTo("File1")
      assertThat(ideServices.lastVisitedCodeLocation!!.lineNumber).isEqualTo(12)
      val constraintsAtComponent = executionPanel.getValueComponent("Constraints") as JPanel
      assertThat(constraintsAtComponent.componentCount).isEqualTo(1)
      assertThat((constraintsAtComponent.getComponent(0) as JLabel).text).isEqualTo("Network must be connected")
      val frequencyComponent = executionPanel.getValueComponent("Frequency") as JLabel
      assertThat(frequencyComponent.text).isEqualTo("One Time")
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")

      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val previousComponent = workContinuationPanel.getValueComponent("Previous") as JPanel
      assertThat(previousComponent.componentCount).isEqualTo(1)
      assertThat((previousComponent.getComponent(0) as JLabel).text).isEqualTo("prerequisiteId")
      val nextComponent = workContinuationPanel.getValueComponent("Next") as JPanel
      assertThat(nextComponent.componentCount).isEqualTo(1)
      assertThat((nextComponent.getComponent(0) as JLabel).text).isEqualTo("dependentsId")
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(1)
      assertThat((chainComponent.getComponent(0) as HyperlinkLabel).text).isEqualTo("ID1  (Current)")

      val resultsPanel = detailsView.getCategoryPanel("Results") as JPanel
      val timeStartedComponent = resultsPanel.getValueComponent("Time started") as JLabel
      assertThat(timeStartedComponent.text).isEqualTo(workInfo.scheduleRequestedAt.toFormattedTimeString())
      val retryCountComponent = resultsPanel.getValueComponent("Retries") as JLabel
      assertThat(retryCountComponent.text).isEqualTo("1")
      val dataComponent = resultsPanel.getValueComponent("Output data") as HideablePanel
      val keyLabel = TreeWalker(dataComponent).descendantStream().filter { (it as? JLabel)?.text == "k = " }.findFirst().get()
      val valueLabel = (keyLabel.parent as JPanel).getComponent(1) as JLabel
      assertThat(valueLabel.text).isEqualTo("\"v\"")
    }
  }

  @Test
  fun selectedWorkEntryUpdated() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)

      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Enqueued")
    }

    client.sendWorkEvent {
      workUpdatedBuilder.apply {
        id = workInfo.id
        state = WorkManagerInspectorProtocol.WorkInfo.State.FAILED
      }
    }
    withContext(uiDispatcher) {
      val executionPanel = detailsView.getCategoryPanel("Execution") as JPanel
      val stateComponent = executionPanel.getValueComponent("State") as JLabel
      assertThat(stateComponent.text).isEqualTo("Failed")
    }
  }

  @Test
  fun unselectedWorkEntryUpdated() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    val dependentWork = workInfo.toBuilder().setId(workInfo.getDependents(0)).build()
    client.sendWorkAddedEvent(dependentWork)
    lateinit var oldDependentWorkLabel: HyperlinkLabel
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      oldDependentWorkLabel = chainComponent.getComponent(1) as HyperlinkLabel
      assertThat(oldDependentWorkLabel.text).isEqualTo("dependentsId")
    }

    client.sendWorkEvent {
      workUpdatedBuilder.apply {
        id = dependentWork.id
        state = WorkManagerInspectorProtocol.WorkInfo.State.FAILED
      }
    }
    withContext(uiDispatcher) {
      val workContinuationPanel = detailsView.getCategoryPanel("WorkContinuation") as JPanel
      val chainComponent = workContinuationPanel.getValueComponent("Unique work chain") as JPanel
      assertThat(chainComponent.componentCount).isEqualTo(2)
      val newDependentWorkLabel = chainComponent.getComponent(1) as HyperlinkLabel
      assertThat(newDependentWorkLabel.text).isEqualTo("dependentsId")
      // Ideally, we want to check if the two labels are with different icons.
      // Unfortunately, [HyperlinkLabel] does not have icon access so we compare labels directly.
      assertThat(oldDependentWorkLabel).isNotEqualTo(newDependentWorkLabel)
    }
  }

  @Test
  fun closeDetailsView() = runBlocking {
    val workInfo = BackgroundTaskInspectorTestUtils.FAKE_WORK_INFO
    client.sendWorkAddedEvent(workInfo)
    withContext(uiDispatcher) {
      selectionModel.selectedEntry = client.getEntry(workInfo.id)
      assertThat(tab.isDetailsViewVisible).isTrue()
      val detailedPanelTitleLabel =
        TreeWalker(detailsView).descendantStream().filter { (it as? JLabel)?.text == "Task Details" }.findFirst().get()
      val titlePanel = detailedPanelTitleLabel.parent as JPanel
      val closeButton = titlePanel.getComponent(1) as InplaceButton
      assertThat(closeButton.toolTipText).isEqualTo("Close")
      closeButton.doClick()
      assertThat(tab.isDetailsViewVisible).isFalse()
    }
  }

  fun JComponent.getValueComponent(key: String) =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == key }.findFirst().get().parent.parent.getComponent(1)

  fun JComponent.getCategoryPanel(key: String) =
    TreeWalker(this).descendantStream().filter { (it as? JLabel)?.text == key }.findFirst().get().parent.parent
}
