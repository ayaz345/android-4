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
package com.android.tools.idea

import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.JavaProjectTestCase

/**
 * The actions which register the shortcuts.
 */
private val DESIGNER_SHORTCUT_ACTIONS = arrayListOf(
  DesignerActions.ACTION_FORCE_REFRESH_PREVIEW,
  DesignerActions.ACTION_TOGGLE_ISSUE_PANEL,
  DesignerActions.ACTION_SWITCH_DESIGN_MODE,
  DesignerActions.ACTION_TOGGLE_DEVICE_ORIENTATION
)

class DesignerKeymapExtensionTest : JavaProjectTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.NELE_CUSTOM_SHORTCUT_KEYMAP.override(true)
  }

  override fun tearDown() {
    StudioFlags.NELE_CUSTOM_SHORTCUT_KEYMAP.clearOverride()
    super.tearDown()
  }

  fun testKeySectionAdded() {
    val keymap = KeymapManager.getInstance().getKeymap(KeymapManager.DEFAULT_IDEA_KEYMAP)!!
    assertTrue(keymap.actionIdList.containsAll(DESIGNER_SHORTCUT_ACTIONS))
  }
}
