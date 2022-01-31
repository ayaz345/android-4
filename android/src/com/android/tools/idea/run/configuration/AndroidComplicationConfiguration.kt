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
package com.android.tools.idea.run.configuration

import com.android.SdkConstants
import com.android.tools.deployer.model.component.Complication
import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.configuration.editors.AndroidComplicationConfigurationEditor
import com.android.tools.idea.run.configuration.execution.AndroidComplicationConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorBase
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Transient
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class AndroidComplicationConfigurationType :
  ConfigurationTypeBase(
    ID,
    AndroidBundle.message("android.complication.configuration.type.name"),
    AndroidBundle.message("android.run.configuration.type.description"),
    StudioIcons.Shell.Filetree.ANDROID_PROJECT
  ) {
  companion object {
    const val ID = "AndroidComplicationConfigurationType"
  }

  init {
    addFactory(object : ConfigurationFactory(this) {
      override fun getId() = "AndroidComplicationConfigurationFactory"
      override fun createTemplateConfiguration(project: Project) = AndroidComplicationConfiguration(project, this)
    })
  }
}


class AndroidComplicationConfiguration(project: Project, factory: ConfigurationFactory) : AndroidWearConfiguration(project, factory) {
  data class ChosenSlot(var id: Int, var type: Complication.ComplicationType) {
    // We need parameterless constructor for correct work of XmlSerializer. See [AndroidWearConfiguration.readExternal]
    private constructor() : this(-1, Complication.ComplicationType.LONG_TEXT)
  }

  var chosenSlots: List<ChosenSlot> = listOf()

  @Transient
  @JvmField
  var watchFaceInfo: ComplicationWatchFaceInfo = DefaultComplicationWatchFaceInfo

  override val componentType = ComponentType.COMPLICATION
  override val userVisibleComponentTypeName: String = AndroidBundle.message("android.run.configuration.complication")

  @Transient
  override val componentBaseClassesFqNames = arrayOf(SdkConstants.CLASS_COMPLICATION_SERVICE_ANDROIDX,
                                                     SdkConstants.CLASS_COMPLICATION_SERVICE_WSL)

  override fun getConfigurationEditor() = AndroidComplicationConfigurationEditor(project, this)

  override fun getExecutor(environment: ExecutionEnvironment): AndroidConfigurationExecutorBase {
    return AndroidComplicationConfigurationExecutor(environment)
  }
}
