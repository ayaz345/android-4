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
package com.android.tools.idea.gradle.structure.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PsdChildModel implements PsdModel {
  @NotNull private final PsdModel myParent;

  private boolean myModified;

  protected PsdChildModel(@NotNull PsdModel parent) {
    myParent = parent;
  }

  @Override
  @NotNull
  public PsdModel getParent() {
    return myParent;
  }

  @Override
  public boolean isModified() {
    return myModified;
  }

  @Override
  public void setModified(boolean value) {
    myModified = value;
    if (myModified) {
      myParent.setModified(true);
    }
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Override
  @Nullable
  public PsdProblem getProblem() {
    return null;
  }
}
