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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

@UiThread
class AdbDevicePairingViewImpl(private val project: Project, override val model: AdbDevicePairingModel) : AdbDevicePairingView {
  private val dlg: AdbDevicePairingDialog

  init {
    // Note: No need to remove the listener, as the Model and View have the same lifetime
    model.addListener(ModelListener())
    dlg = AdbDevicePairingDialog(project, true, DialogWrapper.IdeModalityType.PROJECT)
  }

  override fun showDialog() {
    updateQrCodeImage(model.qrCodeImage)
    dlg.show()
  }

  private fun updateQrCodeImage(image: QrCodeImage?) {
    image?.let { dlg.setQrCodeImage(it) }
  }

  @UiThread
  private inner class ModelListener : AdbDevicePairingModelListener {
    override fun qrCodeGenerated(newImage: QrCodeImage) {
      updateQrCodeImage(newImage)
    }
  }
}
