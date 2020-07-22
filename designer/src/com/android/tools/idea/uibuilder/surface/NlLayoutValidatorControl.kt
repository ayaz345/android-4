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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.IssuePanel
import com.android.tools.idea.ui.alwaysEnableLayoutValidation
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult

/** Impl of [LayoutValidatorControl] configured with [LayoutValidatorAction] */
class NlLayoutValidatorControl(private val surface: NlDesignSurface): LayoutValidatorControl {

  override val validator = NlLayoutValidator(surface.issueModel, surface)

  private val metricTracker = NlLayoutValidatorMetricTracker()

  /** Listener for issue panel open/close */
  private val issuePanelListener = IssuePanel.MinimizeListener {
    val check = surface.sceneManager?.layoutValidationConfig ?: return@MinimizeListener

    if (it) {
      // Minimized
      if (!alwaysEnableLayoutValidation) {
        validator.disable()
        check.isLayoutValidationEnabled = false
      }
    }
    else if (!check.isLayoutValidationEnabled) {
      check.isLayoutValidationEnabled = true
      surface.forceUserRequestedRefresh()
    }
  }

  private val issueExpandListener = IssuePanel.ExpandListener {issue, expanded ->
    if (validator.issues.contains(issue)) {
      metricTracker.trackIssueExpanded(issue, expanded)
    }
  }

  private val validatorListener = object : NlLayoutValidator.Listener {
    override fun lintUpdated(result: ValidatorResult?) {
      if (result != null) {
        surface.analyticsManager.trackShowIssuePanel()
        surface.setShowIssuePanel(true)
        validator.removeListener(this)
      }
    }
  }

  init {
    surface.issuePanel.addMinimizeListener(issuePanelListener)
    surface.issuePanel.expandListener = issueExpandListener
  }

  override fun runLayoutValidation() {
    validator.addListener(validatorListener)
    val manager = surface.sceneManager ?: return
    manager.layoutValidationConfig?.isLayoutValidationEnabled = true
    manager.forceReinflate()
    surface.requestRender()
  }
}

// For debugging
fun ValidatorResult.toDetailedString(): String? {
  val builder: StringBuilder = StringBuilder().append("Result containing ").append(issues.size).append(
    " issues:\n")
  val var2: Iterator<*> = this.issues.iterator()
  while (var2.hasNext()) {
    val issue = var2.next() as ValidatorData.Issue
    if (issue.mLevel == ValidatorData.Level.ERROR) {
      builder.append(" - [E::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    } else {
      builder.append(" - [W::").append(issue.mLevel.name).append("] ").append(issue.mMsg).append("\n")
    }
  }
  return builder.toString()
}