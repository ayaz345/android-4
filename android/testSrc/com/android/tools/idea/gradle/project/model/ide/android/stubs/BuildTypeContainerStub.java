/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class BuildTypeContainerStub extends BaseStub implements BuildTypeContainer {
  @NotNull private final BuildType myBuildType;
  @NotNull private final SourceProvider mySourceProvider;
  @NotNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

  public BuildTypeContainerStub() {
    this(new BuildTypeStub(), new SourceProviderStub(), Lists.newArrayList(new SourceProviderContainerStub()));
  }

  public BuildTypeContainerStub(@NotNull BuildType type,
                                @NotNull SourceProvider sourceProvider,
                                @NotNull Collection<SourceProviderContainer> extraSourceProviders) {
    myBuildType = type;
    mySourceProvider = sourceProvider;
    myExtraSourceProviders = extraSourceProviders;
  }

  @Override
  @NotNull
  public BuildType getBuildType() {
    return myBuildType;
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Collection<SourceProviderContainer> getExtraSourceProviders() {
    return myExtraSourceProviders;
  }
}
