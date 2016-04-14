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
package com.android.tools.idea.npw.template.components;

import com.android.tools.idea.ui.properties.ObservableProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * An interface for providing a Swing component and an {@link ObservableProperty} that controls it.
 *
 * To use this class, simply call {@link #createComponent()} and
 * {@link #createProperty(JComponent)} with the component it returns.
 */
public abstract class ComponentProvider<T extends JComponent> {
  @NotNull
  public abstract T createComponent();

  @Nullable
  public ObservableProperty<?> createProperty(@NotNull T component) {
    return null;
  }

  /**
   * Gives subclasses a chance to handle the user accepting the current value. Most components
   * won't do anything but some may save their values into a Recents database, for example.
   */
  public void accept(@NotNull T component) {
  }
}
