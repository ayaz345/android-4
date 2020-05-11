/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY
import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.isUpdatablePluginVersion
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.FileUtils
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.YES
import java.io.File

class AgpUpgradeVersionRefactoringProcessor(
  val project: Project,
  val current: GradleVersion,
  val new: GradleVersion
) : BaseRefactoringProcessor(project) {
  lateinit var buildModel: ProjectBuildModel

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade AGP version from $current to $new"
    }
  }

  override fun findUsages(): Array<UsageInfo> {
    buildModel = ProjectBuildModel.get(project)
    val usages = ArrayList<UsageInfo>()

    // using the buildModel, look for classpath dependencies on AGP, and if we find one, record it as a usage, and additionally
    // check the buildscript/repositories block for a google() gaven entry, recording an additional usage if we don't find one
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CLASSPATH).forEach dep@{ dep ->
        when (val shouldUpdate = isUpdatablePluginVersion(new, dep)) {
          YES -> {
            val resultModel = dep.version().resultModel
            val psiElement = when (val element = resultModel.rawElement) {
              null -> return@dep
              // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
              is FakeArtifactElement -> element.realExpression.psiElement
              else -> element.psiElement
            }
            psiElement?.let {
              usages.add(AgpVersionUsageInfo(it, current, new, resultModel))
              val repositories = model.buildscript().repositories()
              if (!repositories.hasGoogleMavenRepository()) {
                // TODO(xof) if we don't have a psiElement, we should add a suitable parent (and explain what
                //  we're going to do in terms of that parent
                repositories.psiElement?.let { element -> usages.add(RepositoriesNoGMavenUsageInfo(element, current, new, repositories)) }
              }
            }
          }
          NO -> return@model
          else -> Unit
        }
      }
    }

    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.filterNotNull().forEach { ioRoot ->
        val ioFile = GradleWrapper.getDefaultPropertiesFilePath(ioRoot)
        val gradleWrapper = GradleWrapper.get(ioFile, project)
        val currentGradleVersion = gradleWrapper.gradleVersion ?: return@forEach
        val parsedCurrentGradleVersion = GradleVersion.tryParse(currentGradleVersion) ?: return@forEach
        if (!GradleUtil.isSupportedGradleVersion(parsedCurrentGradleVersion)) {
          val virtualFile = VfsUtil.findFileByIoFile(ioFile, true) ?: return@forEach
          val propertiesFile = PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile ?: return@forEach
          val property = propertiesFile.findPropertyByKey(GRADLE_DISTRIBUTION_URL_PROPERTY) ?: return@forEach
          usages.add(GradleVersionUsageInfo(property.psiElement, current, new))
        }
      }
    }

    return usages.toTypedArray()
  }

  override fun performRefactoring(usages: Array<out UsageInfo>?) {
    usages?.forEach {
      if (it is AgpUpgradeUsageInfo) {
        it.performAgpUpgrade()
      }
    }
    buildModel.applyChanges()
  }

  override fun getCommandName() = "Upgrade AGP version from ${current} to ${new}"
}

abstract class AgpUpgradeUsageInfo(element: PsiElement, val current: GradleVersion, val new: GradleVersion): UsageInfo(element) {
  abstract fun performAgpUpgrade()
}

class AgpVersionUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val resultModel: GradlePropertyModel
) : AgpUpgradeUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Upgrade AGP version from ${current} to ${new}"
  }

  override fun performAgpUpgrade() {
    resultModel.setValue(new.toString())
  }
}

class RepositoriesNoGMavenUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val repositoriesModel: RepositoriesModel
) : AgpUpgradeUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Add google() to repositories"
  }

  override fun performAgpUpgrade() {
    // FIXME(xof): this is wrong; the version in question is the version of Gradle, not the new version of AGP.
    //  This means this is intertwingled with the refactoring which upgrades Gradle version, though in practice
    //  it is unlikely to be a problem (the behaviour changed in Gradle 4.0.
    //  Further: we have the opportunity to make this correct if we can rely on the order of processing UsageInfos
    //  because if we assure ourselves that the Gradle upgrade happens before this one, we can (in principle)
    //  inspect the buildModel or the project to determine the appropriate version of Gradle.
    repositoriesModel.addGoogleMavenRepository(new)
  }
}

class GradleVersionUsageInfo(element: PsiElement, current: GradleVersion, new: GradleVersion): AgpUpgradeUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Upgrade Gradle version to $GRADLE_LATEST_VERSION"
  }

  override fun performAgpUpgrade() {
    val gradleWrapper = GradleWrapper.get(VfsUtil.virtualToIoFile(element!!.containingFile.virtualFile), element!!.project)
    gradleWrapper.updateDistributionUrl(GRADLE_LATEST_VERSION)
  }
}