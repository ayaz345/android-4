/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.idea.localization.MessageBundleReference
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.AndroidAdbUiBundle"

/**
 * Message bundle for the Android ADB UI module.
 */
internal object AndroidAdbUiBundle {
  private val bundleRef = MessageBundleReference(BUNDLE_NAME)
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any) =
    bundleRef.message(key, *(params.map(Any::toString).toTypedArray()))
}
