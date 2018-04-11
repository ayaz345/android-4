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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a new expression.
 */
public final class GradleDslNewExpression extends GradleDslSimpleExpression {
  private final @NotNull List<GradleDslExpression> myArguments = Lists.newArrayList();
  private final @NotNull GradleNameElement myInvokedConstructor;

  public GradleDslNewExpression(@NotNull GradleDslElement parent,
                                @NotNull PsiElement newExpression,
                                @NotNull GradleNameElement name,
                                @NotNull GradleNameElement invokedConstructor) {
    super(parent, newExpression, name, newExpression);
    myInvokedConstructor = invokedConstructor;
  }

  private GradleDslNewExpression(@NotNull GradleDslElement parent,
                                 @NotNull GradleNameElement name,
                                 @NotNull GradleNameElement invokedConstructor) {
    super(parent, null, name, null);
    myInvokedConstructor = invokedConstructor;
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    expression.setParent(this);
    myArguments.add(expression);
  }

  @NotNull
  public List<GradleDslExpression> getArguments() {
    List<GradleDslExpression> result = Lists.newArrayList();

    for (GradleDslExpression argument : myArguments) {
      if (argument instanceof GradleDslReference) {
        // See if the reference is pointing to a list.
        GradleDslExpressionList listValue = ((GradleDslReference)argument).getValue(GradleDslExpressionList.class);
        if (listValue != null) {
          result.addAll(listValue.getExpressions());
          continue;
        }
      }
      result.add(argument);
    }

    return result;
  }

  @NotNull
  public List<GradleDslSimpleExpression> getSimpleArguments() {
    return getArguments().stream().filter(e -> e instanceof GradleDslSimpleExpression).map(e -> (GradleDslSimpleExpression)e).collect(
      Collectors.toList());
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Override
  @Nullable
  public Object getValue() {
    PsiElement psiElement = getPsiElement();
    return psiElement != null ? getPsiText(psiElement) : null;
  }

  @Override
  @Nullable
  public Object getUnresolvedValue() {
    return getValue();
  }

  @Override
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    if (clazz.isAssignableFrom(File.class)) {
      return clazz.cast(getFileValue());
    }
    Object value = getValue();
    if (clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  @Override
  @Nullable
  public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
    return getValue(clazz);
  }

  @Nullable
  private File getFileValue() {
    if (!myInvokedConstructor.name().equals("File")) {
      return null;
    }

    List<GradleDslSimpleExpression> arguments = getSimpleArguments();
    if (arguments.isEmpty()) {
      return null;
    }

    String firstArgumentValue = arguments.get(0).getValue(String.class);
    if (firstArgumentValue == null) {
      return null;
    }

    File result = new File(firstArgumentValue);
    for (int i = 1; i < arguments.size(); i++) {
      String value = arguments.get(i).getValue(String.class);
      if (value == null) {
        return null;
      }
      result = new File(result, value);
    }
    return result;
  }

  @Override
  public void setValue(@NotNull Object value) {
    // TODO: Add support to set the full expression definition as a String.
  }

  @Nullable
  @Override
  public Object getRawValue() {
    // TODO: Add support to get the raw value when there is a use case for it.
    throw new UnsupportedOperationException("Raw values of new expressions not currently supported");
  }

  @Override
  @NotNull
  public GradleDslNewExpression copy() {
    assert myParent != null;
    return new GradleDslNewExpression(myParent, GradleNameElement.copy(myName), GradleNameElement.copy(myInvokedConstructor));
  }

  @Override
  protected void apply() {
    // TODO: Add support to apply changes when there is a use case for it.
  }

  @Override
  protected void reset() {
    // TODO: Add support to reset changes when there is a use case for it.
  }

  @Override
  @Nullable
  public PsiElement create() {
    // TODO: Add support to create new element when there is a use case for it.
    return getPsiElement();
  }
}
