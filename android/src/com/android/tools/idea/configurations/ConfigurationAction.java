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
package com.android.tools.idea.configurations;

import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.res.AppResourceRepository;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

import static com.android.SdkConstants.FD_RES_LAYOUT;

abstract class ConfigurationAction extends AnAction implements ConfigurationListener {
  private static final String FILE_ARROW = " \u2192 ";
  protected final ConfigurationHolder myRenderContext;
  private int myFlags;

  public ConfigurationAction(@NotNull ConfigurationHolder renderContext, @NotNull String title) {
    this(renderContext, title, null);
  }

  public ConfigurationAction(@NotNull ConfigurationHolder renderContext, @NotNull String title, @Nullable Icon icon) {
    super(title, null, icon);
    myRenderContext = renderContext;
  }

  protected void updatePresentation() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionManagerEx manager = ActionManagerEx.getInstanceEx();
    final DataContext dataContext = e.getDataContext();
    // Regular actions invoke this method before performing the action. We do so as well since the analytics subsystem hooks into
    // this event to monitor invoked actions.
    manager.fireBeforeActionPerformed(this, dataContext, e);

    tryUpdateConfiguration();
    updatePresentation();
  }

  /**
   * Performs a try update of the configuration.
   * If the update needs a change of layout file, this method makes it happen.
   * Otherwise, it simply updates the configuration
   */
  protected void tryUpdateConfiguration() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      // See if after switching we need to switch files
      Configuration clone = configuration.clone();
      myFlags = 0;
      clone.addListener(this);
      updateConfiguration(clone, false /*commit*/);
      clone.removeListener(this);

      boolean affectsFileSelection = (myFlags & MASK_FILE_ATTRS) != 0;
      // get the resources of the file's project.
      if (affectsFileSelection) {
        Module module = myRenderContext.getConfiguration().getModule();
        if (module != null) {
          VirtualFile file = myRenderContext.getConfiguration().getFile();
          if (file != null) {
            ConfigurationMatcher matcher = new ConfigurationMatcher(clone, AppResourceRepository.getOrCreateInstance(module), file);
            List<VirtualFile> matchingFiles = matcher.getBestFileMatches();
            if (!matchingFiles.isEmpty() && !matchingFiles.contains(file)) {
              // Switch files, and leave this configuration alone
              pickedBetterMatch(matchingFiles.get(0), file);
              AndroidFacet facet = AndroidFacet.getInstance(module);
              assert facet != null;
              ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(module);
              updateConfiguration(configurationManager.getConfiguration(matchingFiles.get(0)), true /*commit*/);
              return;
            }
          }
        }
      }

      updateConfiguration(configuration, true /*commit*/);
    }
  }

  protected void pickedBetterMatch(@NotNull VirtualFile file, @NotNull VirtualFile old) {
    // Switch files, and leave this configuration alone
    Module module = myRenderContext.getConfiguration().getModule();
    assert module != null;
    Project project = module.getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, -1);
    FileEditorManager manager = FileEditorManager.getInstance(project);
    FileEditor selectedEditor = manager.getSelectedEditor(old);
    List<FileEditor> editors = manager.openEditor(descriptor, true);

    if (selectedEditor != null) {
      FileEditorProvider provider = EditorHistoryManager.getInstance(project).getSelectedProvider(old);
      if (provider != null) {
        manager.setSelectedEditor(file, provider.getEditorTypeId());
      }
      // Proactively switch to the new editor right away in the layout XML preview, if applicable
      if (!editors.isEmpty()) {
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor && editor.getComponent().isShowing()) {
            if (RenderService.NELE_ENABLED) {
              // TODO
              //AndroidLayoutPreviewToolWindowManager previewManager = AndroidLayoutPreviewToolWindowManager.getInstance(project);
              //previewManager.notifyFileShown((TextEditor)editor, true);
              // Notify nele preview manager instead
            }
            break;
          }
        }
      }
    }
  }

  @Override
  public boolean changed(int flags) {
    myFlags |= flags;
    return true;
  }

  protected abstract void updateConfiguration(@NotNull Configuration configuration, boolean commit);

  public static boolean isBetterMatchLabel(@NotNull String label) {
    return label.contains(FILE_ARROW);
  }

  public static String getBetterMatchLabel(@NotNull String prefix, @NotNull VirtualFile better, @Nullable VirtualFile file) {
    StringBuilder sb = new StringBuilder();
    sb.append(prefix);
    sb.append(FILE_ARROW);
    String folderName = better.getParent().getName();
    if (folderName.equals(FD_RES_LAYOUT)) {
      if (file != null && !Comparing.equal(file.getParent(), better.getParent())) {
        sb.append(FD_RES_LAYOUT);
        sb.append(File.separatorChar);
      }
    } else {
      if (folderName.startsWith(FD_RES_LAYOUT)) {
        folderName = folderName.substring(FD_RES_LAYOUT.length() + 1);
      }
      sb.append(folderName);
      sb.append(File.separatorChar);
    }
    sb.append(better.getName());
    return sb.toString();
  }

  public static Icon getBetterMatchIcon() {
    return AndroidIcons.NotMatch;
  }
}
