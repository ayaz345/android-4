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
package com.android.tools.idea.emulator

import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_CONTROLLER_KEY
import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_TOOLBAR_ID
import com.android.tools.idea.emulator.EmulatorConstants.EMULATOR_VIEW_KEY
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Represents contents of the Emulator tool window for a single Emulator instance.
 */
internal class EmulatorToolWindowPanel(private val emulator: EmulatorController) : BorderLayoutPanel(), DataProvider {
  private val toolbarActionGroup = DefaultActionGroup(createToolbarActions())
  private val toolbar = ActionManager.getInstance().createActionToolbar(EMULATOR_TOOLBAR_ID, toolbarActionGroup, isToolbarHorizontal)
  private val centerPanel: JPanel = JPanel(BorderLayout())
  var emulatorView: EmulatorView? = null
    private set

  val id
    get() = emulator.emulatorId

  val title
    get() = emulator.emulatorId.avdName

  val icon
    get() = ICON

  val component: JComponent
    get() = this

  init {
    addToCenter(centerPanel)
    addToolbar()
  }

  private fun addToolbar() {
    if (isToolbarHorizontal) {
      toolbar.setOrientation(SwingConstants.HORIZONTAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToTop(toolbar.component)
    }
    else {
      toolbar.setOrientation(SwingConstants.VERTICAL)
      centerPanel.border = IdeBorderFactory.createBorder(JBColor.border(), SideBorder.TOP)
      addToLeft(toolbar.component)
    }
  }

  fun createContent(cropSkin: Boolean) {
    try {
      emulatorView = EmulatorView(emulator, cropSkin)
      centerPanel.add(emulatorView)
      toolbar.setTargetComponent(emulatorView)
      centerPanel.repaint()
    }
    catch (e: Exception) {
      val label = "Unable to load emulator view: $e"
      add(JLabel(label), BorderLayout.CENTER)
    }
  }

  fun destroyContent() {
    emulatorView = null
    toolbar.setTargetComponent(null)
    centerPanel.layout = BorderLayout()
    centerPanel.removeAll()
  }

  override fun getData(dataId: String): Any? {
    return when (dataId) {
      EMULATOR_CONTROLLER_KEY.name -> emulator
      EMULATOR_VIEW_KEY.name -> emulatorView
      else -> null
    }
  }

  private fun createToolbarActions() =
      listOf(CustomActionsSchema.getInstance().getCorrectedAction(EMULATOR_TOOLBAR_ID)!!)

  companion object {
    @JvmStatic
    private val ICON = ExecutionUtil.getLiveIndicator(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE)
    private const val isToolbarHorizontal = true
  }
}
