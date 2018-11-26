/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.rendering

import com.android.resources.ResourceType
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon

class StubAssetPreviewManager(
  private val iconProvider: AssetIconProvider = StubAssetIconProvider()
) : AssetPreviewManager {

  constructor(icon: Icon) : this(StubAssetIconProvider(icon))

  override fun getPreviewProvider(resourceType: ResourceType): AssetIconProvider = iconProvider
}

class StubAssetIconProvider(var icon: Icon = EmptyIcon.ICON_18) : AssetIconProvider {
  override val supportsTransparency = false

  override fun getIcon(assetToRender: DesignAsset,
                       width: Int,
                       height: Int,
                       refreshCallback: () -> Unit,
                       shouldBeRendered: () -> Boolean): Icon = icon
}