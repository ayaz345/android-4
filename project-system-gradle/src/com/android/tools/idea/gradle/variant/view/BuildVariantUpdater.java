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
package com.android.tools.idea.gradle.variant.view;

import static com.android.tools.idea.gradle.project.sync.SelectedVariantCollectorKt.getModuleIdForSyncRequest;
import static com.android.tools.idea.gradle.project.sync.idea.GradleSyncExecutor.ALWAYS_SKIP_SYNC;
import static com.android.tools.idea.gradle.project.sync.idea.KotlinPropertiesKt.restoreKotlinUserDataFromDataNodes;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.util.ThreeState.YES;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder;
import com.android.tools.idea.gradle.project.sync.SwitchVariantRequest;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver;
import com.android.tools.idea.gradle.project.sync.idea.VariantSwitcher;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler;
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Updates the contents/settings of a module when a build variant changes.
 */
public class BuildVariantUpdater {
  @NotNull private final List<BuildVariantView.BuildVariantSelectionChangeListener> mySelectionChangeListeners =
    ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public static BuildVariantUpdater getInstance(@NotNull Project project) {
    return project.getService(BuildVariantUpdater.class);
  }

  // called by IDEA.
  @SuppressWarnings("unused")
  BuildVariantUpdater() { }

  /**
   * Add an {@link BuildVariantView.BuildVariantSelectionChangeListener} to the updater. Listeners are
   * invoked when the project's selected build variant changes.
   */
  void addSelectionChangeListener(@NotNull BuildVariantView.BuildVariantSelectionChangeListener listener) {
    mySelectionChangeListeners.add(listener);
  }

  /**
   * Updates a module's structure when the user selects a build variant from the tool window.
   *
   * @param project              the module's project.
   * @param moduleName           the module's name.
   * @param selectedBuildVariant the name of the selected build variant (without ABI).
   * @return true if there are affected facets.
   */
  public boolean updateSelectedBuildVariant(@NotNull Project project,
                                            @NotNull String moduleName,
                                            @NotNull String selectedBuildVariant) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName, new SwitchVariantRequest(getModuleIdForSyncRequest(moduleToUpdate), selectedBuildVariant, null));
  }

  /**
   * Updates a module's structure when the user selects an ABI from the tool window.
   *
   * @param project         the module's project.
   * @param moduleName      the module's name.
   * @param selectedAbiName the name of the selected ABI.
   * @return true if there are affected facets.
   */
  public boolean updateSelectedAbi(@NotNull Project project,
                                   @NotNull String moduleName,
                                   @NotNull String selectedAbiName) {
    Module moduleToUpdate = findModule(project, moduleName);
    if (moduleToUpdate == null) {
      logAndShowAbiNameFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    return updateSelectedVariant(project, moduleName,
                                 new SwitchVariantRequest(getModuleIdForSyncRequest(moduleToUpdate),
                                                          null,
                                                          selectedAbiName));
  }

  /**
   * Updates a module's structure when the user selects a build variant or ABI.
   *
   * @param project       the module's project.
   * @param moduleName    the module's name.
   * @param variantAndAbi the name of the selected build variant (without abi for non-native modules, with ABI for native modules).
   * @return true if there are affected facets.
   */
  private boolean updateSelectedVariant(@NotNull Project project,
                                        @NotNull String moduleName,
                                        @NotNull SwitchVariantRequest variantAndAbi) {
    Module module = findModule(project, moduleName);
    if (module == null) {
      logAndShowBuildVariantFailure(String.format("Cannot find module '%1$s'.", moduleName));
      return false;
    }

    @Nullable ExternalProjectInfo data =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.getBasePath());

    DataNode<ProjectData> variantProjectDataNode =
      StudioFlags.GRADLE_SYNC_ENABLE_CACHED_VARIANTS.get() &&data != null
      ? VariantSwitcher.findVariantProjectData(module, variantAndAbi, data)
      : null;

    Runnable invokeVariantSelectionChangeListeners = () -> {
      for (BuildVariantView.BuildVariantSelectionChangeListener listener : mySelectionChangeListeners) {
        listener.selectionChanged();
      }
    };

    // There are three different cases,
    // 1. Build files have been changed, request a full Gradle Sync - let Gradle Sync infrastructure handle single variant or not.
    // 2. Build files were not changed, variant to select doesn't exist, which can only happen with single-variant sync, request Variant-only Sync.
    // 3. Build files were not changed, variant to select exists, do module setup for affected modules.
    if (GradleSyncState.getInstance(project).isSyncNeeded().equals(YES)) {
      requestGradleSync(project, variantAndAbi, invokeVariantSelectionChangeListeners);
      return true;
    }

    if (data != null && variantProjectDataNode != null) {
      VariantSwitcher.findAndSetupSelectedCachedVariantData(data, variantProjectDataNode);
      disableKotlinCompilerPluginImportHandlers(project); // TODO(b/215522894)
      restoreKotlinUserDataFromDataNodes(variantProjectDataNode);
      setupCachedVariant(project, variantProjectDataNode, invokeVariantSelectionChangeListeners);
      return true;
    }

    // Build file is not changed, the cached variants should be cached and reused.
    AndroidGradleProjectResolver.saveCurrentlySyncedVariantsForReuse(project);
    requestGradleSync(project, variantAndAbi, invokeVariantSelectionChangeListeners);

    return true;
  }

  // TODO(b/215522894): Unfortunately, some Kotlin resolvers stash non-persisted data into the user data of data notes.
  //  The non-persisted data disappears when switching cached build variants, leading to NPEs in the corresponding data importers.
  //  This currently only affects certain Kotlin compiler plugins, e.g. the all-open plugin. For now we disable them.
  private static void disableKotlinCompilerPluginImportHandlers(Project project) {
    ExtensionPoint<GradleProjectImportHandler> importHandlerEP =
      project.getExtensionArea().getExtensionPoint(GradleProjectImportHandler.Companion.getExtensionPointName());
    for (GradleProjectImportHandler importHandler : importHandlerEP.getExtensionList()) {
      if (importHandler instanceof AbstractAnnotationBasedCompilerPluginGradleImportHandler) {
        importHandlerEP.unregisterExtension(importHandler.getClass());
      }
    }
  }

  /**
   * If the user modified the ABI of a module, then any dependent native module should use that same ABI (if possible).
   * If the user did not modify any ABIs (e.g., they changed a non-native module from "debug" to "release"), then any dependent native
   * module should preserve its ABI (if possible).
   * In either case, if the target ABI (either selected by the user, or preserved automatically) does not exist (e.g., "debug-x86" exists,
   * but "release-x86" does not), then the dependent modules should use any available ABI for the target variant.
   *
   * @param ndkModel        The NDK model of the current module, which contains the old variant with ABI. E.g., "debug-x86".
   * @param newVariant      The name of the selected variant (without ABI). E.g., "release".
   * @param userSelectedAbi The name of the selected ABI. E.g., "x86".
   * @return The variant that to be used for the current module, including the ABI. E.g., "release-x86".
   */
  @Nullable
  private static VariantAbi resolveNewVariantAbi(
    @NotNull NdkFacet ndkFacet,
    @NotNull NdkModuleModel ndkModel,
    @NotNull String newVariant,
    @Nullable String userSelectedAbi) {
    if (userSelectedAbi != null) {
      VariantAbi newVariantAbi = new VariantAbi(newVariant, userSelectedAbi);
      if (ndkModel.getAllVariantAbis().contains(newVariantAbi)) {
        return newVariantAbi;
      }
    }
    VariantAbi selectedVariantAbi = ndkFacet.getSelectedVariantAbi();
    if (selectedVariantAbi == null) return null;
    String existingAbi = selectedVariantAbi.getAbi();
    return new VariantAbi(newVariant, existingAbi);  // e.g., debug-x86
  }


  @NotNull
  private static GradleSyncListener getSyncListener(@NotNull Runnable variantSelectionChangeListeners) {
    return new GradleSyncListener() {
      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        if (project.getUserData(ALWAYS_SKIP_SYNC) == null) {
          AndroidNotification.getInstance(project)
            .showProgressBalloon("Cannot change the current build variant at this moment", MessageType.ERROR);
        }
        AndroidGradleProjectResolver.clearVariantsSavedForReuse(project);
        variantSelectionChangeListeners.run();
      }
    };
  }

  private static void requestGradleSync(@NotNull Project project,
                                        @NotNull SwitchVariantRequest requestedVariantChange,
                                        @NotNull Runnable variantSelectionChangeListeners) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(
      TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER,
      requestedVariantChange);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, getSyncListener(variantSelectionChangeListeners));
  }


  private static void setupCachedVariant(@NotNull Project project,
                                         @NotNull DataNode<ProjectData> variantData,
                                         @NotNull Runnable variantSelectionChangeListeners) {
    Application application = ApplicationManager.getApplication();

    Task.Backgroundable task = new Task.Backgroundable(project, "Setting up Project", false/* cannot be canceled*/) {

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        getLog().info("Starting setup of cached variant");

        // While we work to move the rest of the setup we need to perform two commits, once using IDEAs data import and the other
        // using the remainder of out setup steps.
        VariantSwitcher.switchVariant(project, variantData);

        GradleSyncStateHolder.getInstance(project).syncSkipped(null);

        // Commit changes and dispose models providers
        if (application.isUnitTestMode()) {
          variantSelectionChangeListeners.run();
        }
        else {
          application.invokeLater(variantSelectionChangeListeners);
        }

        getLog().info("Finished setup of cached variant");
      }
    };

    if (application.isUnitTestMode()) {
      task.run(new EmptyProgressIndicator());
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
    }
  }

  @Nullable
  private static Module findModule(@NotNull Project project, @NotNull String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    return moduleManager.findModuleByName(moduleName);
  }

  @Nullable
  private static NdkModuleModel getNdkModelIfItHasNativeVariantAbis(@NotNull NdkFacet facet) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(facet);
    if (ndkModuleModel == null) {
      logAndShowBuildVariantFailure(
        String.format("Cannot find NativeAndroidProject for module '%1$s'.", facet.getModule().getName()));
      return null;
    }
    if (ndkModuleModel.getAllVariantAbis().isEmpty()) {
      // Native module that does not have any real ABIs. Proceed with the build variant without ABI.
      return null;
    }
    return ndkModuleModel;
  }

  @Nullable
  private static NdkModuleModel getNdkModelIfItHasNativeVariantAbis(@NotNull Module module) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    if (ndkModuleModel == null) {
      return null;
    }
    if (ndkModuleModel.getAllVariantAbis().isEmpty()) {
      // Native module that does not have any real ABIs. Proceed with the build variant without ABI.
      return null;
    }
    return ndkModuleModel;
  }


  private static void logAndShowBuildVariantFailure(@NotNull String reason) {
    String prefix = "Unable to select build variant:\n";
    String msg = prefix + reason;
    getLog().error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }

  private static void logAndShowAbiNameFailure(@NotNull String reason) {
    String prefix = "Unable to select ABI:\n";
    String msg = prefix + reason;
    getLog().error(msg);
    msg += ".\n\nConsult IDE log for more details (Help | Show Log)";
    Messages.showErrorDialog(msg, "Error");
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(BuildVariantUpdater.class);
  }
}
