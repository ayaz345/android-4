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
package com.android.tools.idea.gradle.structure.configurables.model;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.google.common.collect.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.gradle.structure.configurables.model.Coordinates.areEqual;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

/**
 * Represents a merged view of the project model obtained from Gradle and the model obtained by parsing a module's build.gradle file.
 */
public class ModuleMergedModel {
  @NotNull private static final Map<String, String> ARTIFACT_TO_SCOPE_MAP = Maps.newHashMap();
  static {
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_MAIN, "compile");
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_ANDROID_TEST, "androidTest");
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_UNIT_TEST, "testCompile");
  }

  @NotNull final GradleBuildModel buildModel;
  @NotNull final AndroidProject androidProject;
  @NotNull final Module module;

  @NotNull private final List<DependencyMergedModel> myDependencyModels = Lists.newArrayList();

  @Nullable
  public static ModuleMergedModel get(@NotNull Module module) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        return new ModuleMergedModel(buildModel, androidModel.getAndroidProject(), module);
      }
    }
    return null;
  }

  private ModuleMergedModel(@NotNull GradleBuildModel buildModel, @NotNull AndroidProject androidProject, @NotNull Module module) {
    this.buildModel = buildModel;
    this.androidProject = androidProject;
    this.module = module;

    populate();
  }

  private void populate() {
    GradleArtifactDependencies artifactDependencies = new GradleArtifactDependencies();
    for (Variant variant : androidProject.getVariants()) {
      BaseArtifact mainArtifact = variant.getMainArtifact();
      collectArtifactDependencies(variant, mainArtifact, artifactDependencies);

      for (BaseArtifact artifact : variant.getExtraAndroidArtifacts()) {
        collectArtifactDependencies(variant, artifact, artifactDependencies);
      }

      for (BaseArtifact artifact : variant.getExtraJavaArtifacts()) {
        collectArtifactDependencies(variant, artifact, artifactDependencies);
      }
    }

    DependenciesModel dependenciesModel = buildModel.dependencies();
    if (dependenciesModel != null) {
      for (DependencyModel dependencyModel : dependenciesModel.all()) {
        if (dependencyModel instanceof ArtifactDependencyModel) {
          addDependency((ArtifactDependencyModel)dependencyModel, artifactDependencies);
        }
      }
    }

    Set<GradleArtifactDependency> notFoundInBuildFile = artifactDependencies.getNotFoundInBuildFile();
    if (!notFoundInBuildFile.isEmpty()) {
      for (GradleArtifactDependency dependency : notFoundInBuildFile) {
        ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, dependency);
        myDependencyModels.add(model);
      }
    }
  }

  private static void collectArtifactDependencies(@NotNull Variant variant,
                                                  @NotNull BaseArtifact artifact,
                                                  @NotNull GradleArtifactDependencies artifactDependencies) {
    String scopeName = getScopeName(artifact);
    if (isEmpty(scopeName)) {
      return;
    }
    Dependencies dependencies = artifact.getDependencies();
    for (AndroidLibrary library : dependencies.getLibraries()) {
      GradleArtifactDependency dependency = GradleArtifactDependency.create(library);
      artifactDependencies.add(variant, scopeName, dependency);
    }
    for (JavaLibrary library : dependencies.getJavaLibraries()) {
      GradleArtifactDependency dependency = GradleArtifactDependency.create(library);
      artifactDependencies.add(variant, scopeName, dependency);
    }
  }

  @Nullable
  private static String getScopeName(@NotNull BaseArtifact artifact) {
    return ARTIFACT_TO_SCOPE_MAP.get(artifact.getName());
  }

  private void addDependency(@NotNull ArtifactDependencyModel parsedDependency, @NotNull GradleArtifactDependencies artifactDependencies) {
    GradleCoordinate parsedCoordinate = parseCoordinateString(parsedDependency.getSpec().compactNotation());
    if (parsedCoordinate != null) {
      String configurationName = parsedDependency.configurationName();
      Collection<GradleArtifactDependency> dependenciesInConfiguration = artifactDependencies.getByConfigurationName(configurationName);
      if (!dependenciesInConfiguration.isEmpty()) {
        List<GradleArtifactDependency> fromGradleModel = Lists.newArrayList();
        for (GradleArtifactDependency dependency : dependenciesInConfiguration) {
          GradleCoordinate logicalCoordinate = dependency.coordinate;
          if (areEqual(logicalCoordinate, parsedCoordinate)) {
            fromGradleModel.add(dependency);
            artifactDependencies.markAsFoundInBuildFile(dependency);
          }
        }
        if (!fromGradleModel.isEmpty()) {
          ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, fromGradleModel, parsedDependency);
          if (model != null) {
            myDependencyModels.add(model);
          }
          return;
        }
      }
    }
    ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, parsedDependency);
    if (model != null) {
      myDependencyModels.add(model);
    }
  }

  @NotNull
  public String getModuleName() {
    return module.getName();
  }

  @NotNull
  public Icon getIcon() {
    if (!module.isDisposed()) {
      return getModuleIcon(module);
    }
    return Module;
  }

  @NotNull
  public AndroidProject getAndroidProject() {
    return androidProject;
  }

  @NotNull
  public List<DependencyMergedModel> getDependencies() {
    return myDependencyModels;
  }

  @NotNull
  public List<DependencyMergedModel> getEditableDependencies() {
    List<DependencyMergedModel> dependencies = Lists.newArrayList();
    for (DependencyMergedModel model : myDependencyModels) {
      if (model.isEditable()) {
        dependencies.add(model);
      }
    }
    return dependencies;
  }

  /**
   * Collection of artifact dependencies obtained from the Gradle model.
   */
  private static class GradleArtifactDependencies {
    @NotNull private final Multimap<String, GradleArtifactDependency> myDependenciesByScope = LinkedHashMultimap.create();
    @NotNull private final Map<String, GradleArtifactDependency> myDependenciesByCoordinate = Maps.newHashMap();
    @NotNull private final Set<GradleArtifactDependency> myDependencies = Sets.newHashSet(myDependenciesByCoordinate.values());

    void add(@NotNull Variant variant, @NotNull String scopeName, @Nullable GradleArtifactDependency dependency) {
      if (dependency != null) {
        GradleArtifactDependency existing = myDependenciesByCoordinate.get(dependency.toString());
        if (existing != null) {
          dependency = existing;
        }
        else {
          myDependenciesByCoordinate.put(dependency.toString(), dependency);
        }
        dependency.addContainer(variant);
        myDependenciesByScope.put(scopeName, dependency);
        scopeName = capitalize(scopeName);
        for (String flavor : variant.getProductFlavors()) {
          String flavorScope = flavor + scopeName;
          myDependenciesByScope.put(flavorScope, dependency);
        }
        myDependenciesByScope.put(variant.getBuildType() + scopeName, dependency);
        myDependencies.add(dependency);
      }
    }

    @NotNull
    Collection<GradleArtifactDependency> getByConfigurationName(String configurationName) {
      return myDependenciesByScope.get(configurationName);
    }

    /**
     * Indicates that the given dependency (obtained from the Gradle model) has been found in the build.gradle file.
     */
    void markAsFoundInBuildFile(GradleArtifactDependency dependency) {
      myDependencies.remove(dependency);
    }

    @NotNull
    Set<GradleArtifactDependency> getNotFoundInBuildFile() {
      return myDependencies;
    }
  }

}
