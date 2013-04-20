/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.android;

import com.android.build.gradle.model.AndroidProject;
import com.android.build.gradle.model.BuildTypeContainer;
import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.build.gradle.model.Variant;
import com.android.tools.idea.gradle.model.FileStructure;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

public class AndroidProjectStub implements AndroidProject {
  @NotNull private final Map<String, BuildTypeContainer> myBuildTypes = Maps.newHashMap();
  @NotNull private final Map<String, ProductFlavorContainer> myProductFlavors = Maps.newHashMap();
  @NotNull private final Map<String, Variant> myVariants = Maps.newHashMap();

  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final ProductFlavorContainerStub myDefaultConfig;
  @NotNull private final File myBuildFile;

  @Nullable private VariantStub myFirstVariant;

  public AndroidProjectStub(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public AndroidProjectStub(@NotNull File parentDir, @NotNull String name) {
    this(name, new FileStructure(parentDir, name));
  }

  private AndroidProjectStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    this.myName = name;
    myFileStructure = fileStructure;
    myDefaultConfig = new ProductFlavorContainerStub("main", myFileStructure);
    myBuildFile = myFileStructure.createProjectFile("build.gradle");
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isLibrary() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public ProductFlavorContainerStub getDefaultConfig() {
    return myDefaultConfig;
  }

  @NotNull
  @Override
  public Map<String, BuildTypeContainer> getBuildTypes() {
    return myBuildTypes;
  }

  @NotNull
  public ProductFlavorContainerStub addProductFlavor(@NotNull String flavorName) {
    ProductFlavorContainerStub flavor = new ProductFlavorContainerStub(flavorName, myFileStructure);
    myProductFlavors.put(flavorName, flavor);
    return flavor;
  }

  @NotNull
  @Override
  public Map<String, ProductFlavorContainer> getProductFlavors() {
    return myProductFlavors;
  }

  @NotNull
  public VariantStub addNewVariant(String variantName) {
    VariantStub variant = new VariantStub(variantName, myFileStructure);
    addVariant(variant);
    return variant;
  }

  private void addVariant(@NotNull VariantStub variant) {
    if (myFirstVariant == null) {
      myFirstVariant = variant;
    }
    myVariants.put(variant.getName(), variant);
  }

  @NotNull
  @Override
  public Map<String, Variant> getVariants() {
    return myVariants;
  }

  @Nullable
  public VariantStub getFirstVariant() {
    return myFirstVariant;
  }

  /**
   * Deletes this project's directory structure.
   */
  public void dispose() {
    myFileStructure.dispose();
  }

  /**
   * @return this project's root directory.
   */
  @NotNull
  public File getRootDir() {
    return myFileStructure.getRootDir();
  }

  /**
   * @return this project's build.gradle file.
   */
  @NotNull
  public File getBuildFile() {
    return myBuildFile;
  }
}
