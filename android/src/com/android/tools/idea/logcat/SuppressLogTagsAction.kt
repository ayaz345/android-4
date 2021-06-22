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
package com.android.tools.idea.logcat

import com.android.ddmlib.IDevice
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.actions.AbstractDeviceAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle

internal class SuppressLogTagsAction(val project: Project, context: DeviceContext, val logConsole: AndroidLogConsole) :
  AbstractDeviceAction(
    context,
    AndroidBundle.message("android.configure.logcat.suppress.tags.text"),
    AndroidBundle.message("android.configure.logcat.suppress.tags.description"),
    AllIcons.RunConfigurations.ShowIgnored) {

  override fun performAction(device: IDevice) {
    val preferences = AndroidLogcatGlobalPreferences.getInstance()
    val dialog = SuppressLogTagsActionDialog(project, logConsole, preferences.suppressedLogTags)
    dialog.setSize(preferences.suppressedLogTagsDialogDimension.width, preferences.suppressedLogTagsDialogDimension.height)
    if (dialog.showAndGet()) {
      preferences.suppressedLogTags.clear()
      preferences.suppressedLogTags.addAll(dialog.getSelectedTags())
      logConsole.refresh()
    }
    preferences.suppressedLogTagsDialogDimension = dialog.size
  }
}