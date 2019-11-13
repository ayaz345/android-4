/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.builder.model.AndroidLibrary
import com.android.resources.ResourceFolderType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.MergedManifestContributors
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.util.AndroidUtils
import java.io.File

fun AndroidFacet.getMergedManifestContributors(): MergedManifestContributors {
  val dependencies = AndroidUtils.getAndroidResourceDependencies(module)
  return MergedManifestContributors(
    primaryManifest = AndroidRootUtil.getPrimaryManifestFile(this),
    flavorAndBuildTypeManifests = getFlavorAndBuildTypeManifests(),
    libraryManifests = if (configuration.isAppOrFeature) getLibraryManifests(dependencies) else emptyList(),
    navigationFiles = getNavigationFiles(),
    flavorAndBuildTypeManifestsOfLibs = getFlavorAndBuildTypeManifestsOfLibs(dependencies)
  )
}

private fun AndroidFacet.getFlavorAndBuildTypeManifests(): List<VirtualFile> {
  // get all other manifests for this module, (NOT including the default one)
  val sourceProviderManager = SourceProviderManager.getInstance(this)
  val defaultSourceProvider = sourceProviderManager.mainIdeaSourceProvider
  return sourceProviderManager.currentSourceProviders
    .filter { it != defaultSourceProvider }
    .mapNotNull(IdeaSourceProvider::manifestFile)
}

private fun AndroidFacet.getFlavorAndBuildTypeManifestsOfLibs(dependencies: List<AndroidFacet>): List<VirtualFile> {
  return dependencies.flatMap(AndroidFacet::getFlavorAndBuildTypeManifests)
}

private fun AndroidFacet.getLibraryManifests(dependencies: List<AndroidFacet>): List<VirtualFile> {
  if (isDisposed) return emptyList()
  val localLibManifests = dependencies.mapNotNull { SourceProviderManager.getInstance(it).mainManifestFile }

  val aarManifests = hashSetOf<File>()
  AndroidModuleModel.get(this)
    ?.selectedMainCompileDependencies
    ?.libraries
    ?.forEach { addAarManifests(it, aarManifests, dependencies) }

  // Local library manifests come first because they have higher priority.
  return localLibManifests +
         // If any of these are null, then the file is specified in the model,
         // but not actually available yet, such as exploded AAR manifests.
         aarManifests.mapNotNull { VfsUtil.findFileByIoFile(it, false) }
}

private fun addAarManifests(lib: AndroidLibrary, result: MutableSet<File>, moduleDeps: List<AndroidFacet>) {
  lib.project?.let { projectName ->
    // The model ends up with AndroidLibrary references both to normal, source modules,
    // as well as AAR dependency wrappers. We don't want to add an AAR reference for
    // normal libraries (so we find these and just return below), but we *do* want to
    // include AAR wrappers.
    // TODO(b/128928135): Make this build system-independent.
    if (moduleDeps.any { projectName == GradleUtil.getGradlePath(it.module) }) {
      return
    }
  }
  if (lib.manifest !in result) {
    result.add(lib.manifest)
    lib.libraryDependencies.forEach {
      addAarManifests(it, result, moduleDeps)
    }
  }
}

/**
 * Returns all navigation files for the facet's module, ordered from higher precedence to lower precedence.
 * TODO(b/70815924): Change implementation to use resource repository API
 */
private fun AndroidFacet.getNavigationFiles(): List<VirtualFile> {
  return SourceProviderManager.getInstance(this).currentSourceProviders
    .asReversed() // iterate over providers in reverse order so higher precedence navigation files are first
    .asSequence()
    .flatMapWithoutNulls { provider -> provider.resDirectories.asSequence() }
    .flatMapWithoutNulls { resDir -> resDir.children?.asSequence() }
    .filter { resDirFolder -> ResourceFolderType.getFolderType(resDirFolder.name) == ResourceFolderType.NAVIGATION }
    .flatMapWithoutNulls { navDir -> navDir.children?.asSequence() }
    .filter { potentialNavFile -> !potentialNavFile.isDirectory }
    .toList()
}

private fun <T, R : Any> Sequence<T>.flatMapWithoutNulls(transform: (T) -> Sequence<R?>?): Sequence<R> {
  return flatMap { transform(it) ?: emptySequence() }.filterNotNull()
}
