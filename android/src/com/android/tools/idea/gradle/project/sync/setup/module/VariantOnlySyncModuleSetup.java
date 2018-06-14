/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.android.CompilerOutputModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.ContentRootsModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesAndroidModuleSetupStep;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariantOnlySyncModuleSetup {
  @NotNull private final AndroidModuleSetupStep[] mySetupSteps;

  public VariantOnlySyncModuleSetup() {
    this(new ContentRootsModuleSetupStep(), new DependenciesAndroidModuleSetupStep(), new CompilerOutputModuleSetupStep());
  }

  @VisibleForTesting
  public VariantOnlySyncModuleSetup(@NotNull AndroidModuleSetupStep... setupSteps) {
    mySetupSteps = setupSteps;
  }

  public void setUpModule(@NotNull ModuleSetupContext context, @Nullable AndroidModuleModel androidModel) {
    for (AndroidModuleSetupStep step : mySetupSteps) {
      if (step.invokeOnBuildVariantChange()) {
        step.setUpModule(context, androidModel);
      }
    }
  }
}
