/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidSdkManagerEnabled;

public class SdkUpdaterConfigurableProvider extends ConfigurableProvider {
  @Nullable
  @Override
  public Configurable createConfigurable() {
    if (Boolean.parseBoolean(System.getProperty("use.new.sdk", "false"))) {
      return new com.android.tools.idea.updater.configure.v2.SdkUpdaterConfigurable();
    }
    return new SdkUpdaterConfigurable();
  }

  @Override
  public boolean canCreateConfigurable() {
    return isAndroidSdkManagerEnabled();
  }
}
