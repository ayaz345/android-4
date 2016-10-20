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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.android.util.AndroidUtils.isAndroidStudio;

/**
 * Base class for actions that perform Gradle-specific tasks in Android Studio.
 */
public abstract class AndroidStudioGradleAction extends AnAction {
  protected final boolean myAndroidStudio;

  protected AndroidStudioGradleAction(@Nullable String text) {
    this(text, isAndroidStudio());
  }

  @VisibleForTesting
  AndroidStudioGradleAction(@Nullable String text, boolean androidStudio) {
    this(text, null, null, androidStudio);
  }

  protected AndroidStudioGradleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    this(text, description, icon, isAndroidStudio());
  }

  private AndroidStudioGradleAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, boolean androidStudio) {
    super(text, description, icon);
    myAndroidStudio = androidStudio;
  }

  @Override
  public final void update(AnActionEvent e) {
    if (!isGradleProjectInAndroidStudio(e)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    // Make it visible and enabled for Gradle projects, and let subclasses decide whether the action should be enabled or not.
    e.getPresentation().setEnabledAndVisible(true);

    Project project = e.getProject();
    assert project != null;
    doUpdate(e, project);
  }

  protected abstract void doUpdate(@NotNull AnActionEvent e, @NotNull Project project);

  @Override
  public final void actionPerformed(AnActionEvent e) {
    if (!isGradleProjectInAndroidStudio(e)) {
      return;
    }
    Project project = e.getProject();
    assert project != null;
    doPerform(e, project);
  }

  protected abstract void doPerform(@NotNull AnActionEvent e, @NotNull Project project);

  private boolean isGradleProjectInAndroidStudio(@NotNull AnActionEvent e) {
    if (myAndroidStudio) {
      Project project = e.getProject();
      return project != null && GradleProjectInfo.getInstance(project).isBuildWithGradle();
    }
    return false;
  }
}
