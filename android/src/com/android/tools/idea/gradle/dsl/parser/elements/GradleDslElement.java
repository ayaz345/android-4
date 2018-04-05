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

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.DERIVED;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;

/**
 * Provide Gradle specific abstraction over a {@link PsiElement}.
 */
public abstract class GradleDslElement implements AnchorProvider {
  @NotNull protected GradleNameElement myName;

  @Nullable protected GradleDslElement myParent;

  @NotNull protected List<GradlePropertiesDslElement> myHolders = new ArrayList<>();

  @NotNull private final GradleDslFile myDslFile;

  @Nullable private PsiElement myPsiElement;

  @Nullable private GradleDslClosure myClosureElement;

  private volatile boolean myModified;

  // Whether or not that DslElement should be represented with the assignment syntax i.e "name = 'value'" or
  // the method call syntax i.e "name 'value'". This is needed since on some element types as we do not carry
  // the information to make this distinction. GradleDslElement will set this to a default of false.
  protected boolean myUseAssignment;

  @NotNull private PropertyType myElementType;

  @NotNull protected final List<GradleReferenceInjection> myDependencies = new ArrayList<>();
  @NotNull protected final List<GradleReferenceInjection> myDependents = new ArrayList<>();

  /**
   * Creates an in stance of a {@link GradleDslElement}
   *
   * @param parent     the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if
   *                   this element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link PsiElement} of this dsl element.
   * @param name       the name of this element.
   */
  protected GradleDslElement(@Nullable GradleDslElement parent, @Nullable PsiElement psiElement, @NotNull GradleNameElement name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;


    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.getDslFile();
    }

    myUseAssignment = false;
    // Default to DERIVED, this is overwritten in the parser if required for the given element type.
    myElementType = DERIVED;
  }

  public void setParsedClosureElement(@NotNull GradleDslClosure closureElement) {
    myClosureElement = closureElement;
  }

  @Nullable
  public GradleDslClosure getClosureElement() {
    return myClosureElement;
  }

  /**
   * Returns the name of this element at the lowest scope. I.e the text after the last dot ('.').
   */
  @NotNull
  public String getName() {
    return myName.name();
  }

  @NotNull
  public GradleNameElement getNameElement() {
    return myName;
  }

  public void rename(@NotNull String newName) {
    myName.rename(newName);
    setModified(true);

    // If we are a GradleDslSimpleExpression we need to ensure our dependencies are correct.
    if (!(this instanceof GradleDslSimpleExpression)) {
      return;
    }

    List<GradleReferenceInjection> dependents = getDependents();
    unregisterAllDependants();

    reorder();

    // The property we renamed could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // The new name could also create new dependencies, we need to make sure to resolve them.
    getDslFile().getContext().getDependencyManager().resolveWith(this);
  }

  /**
   * Returns the full name of the element. For elements where it makes sense, this will be the text of the
   * PsiElement in the build file.
   */
  @NotNull
  public String getFullName() {
    return myName.fullName();
  }

  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public void setPsiElement(@Nullable PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  public boolean shouldUseAssignment() {
    return myUseAssignment;
  }

  public void setUseAssignment(boolean useAssignment) {
    myUseAssignment = useAssignment;
  }

  @NotNull
  public PropertyType getElementType() {
    return myElementType;
  }

  public void setElementType(@NotNull PropertyType propertyType) {
    myElementType = propertyType;
  }

  @NotNull
  public String getQualifiedName() {
    // Don't include the name of the parent if this element is a direct child of the file.
    if (myParent == null || myParent instanceof GradleDslFile) {
      return getName();
    }

    String ourName = getName();
    return myParent.getQualifiedName() + (ourName.isEmpty() ? "" : "." + getName());
  }

  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    ImmutableList.Builder<GradleReferenceInjection> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
  }

  @Override
  @Nullable
  public GradleDslElement requestAnchor(@NotNull GradleDslElement element) {
    return null;
  }

  @Nullable
  public GradleDslElement getAnchor() {
    return myParent == null ? null : myParent.requestAnchor(this);
  }

  /**
   * Creates the {@link PsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link PsiElement} only when {@link #getPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link PsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link PsiElement}.
   */
  @Nullable
  public PsiElement create() {
    return myDslFile.getWriter().createDslElement(this);
  }

  @Nullable
  public PsiElement move() {
    return myDslFile.getWriter().moveDslElement(this);
  }

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  protected void delete() {
    for (GradleDslElement element : getChildren()) {
      element.delete();
    }

    this.getDslFile().getWriter().deleteDslElement(this);
  }

  public void setModified(boolean modified) {
    myModified = modified;
    if (myParent != null && modified) {
      myParent.setModified(true);
    }
  }

  public boolean isModified() {
    return myModified;
  }

  /**
   * Returns {@code true} if this element represents a Block element (Ex. android, productFlavors, dependencies etc.),
   * {@code false} otherwise.
   */
  public boolean isBlockElement() {
    return false;
  }

  /**
   * Returns {@code true} if this element represents an element which is insignificant if empty.
   */
  public boolean isInsignificantIfEmpty() {
    return true;
  }

  @NotNull
  public abstract Collection<GradleDslElement> getChildren();

  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    setModified(false);
  }

  protected abstract void apply();

  public final void resetState() {
    reset();
    setModified(false);
  }

  protected abstract void reset();

  /**
   * Computes a list of properties and variables that are declared or assigned to in this scope.
   * Override in subclasses to return meaningful values.
   */
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return Collections.emptyList();
  }

  /**
   * Computes a list of properties and variables that are visible from this GradleDslElement.
   */
  public Map<String, GradleDslElement> getInScopeElements() {
    Map<String, GradleDslElement> results = new LinkedHashMap<>();

    if (this instanceof GradlePropertiesDslElement) {
      GradlePropertiesDslElement thisElement = (GradlePropertiesDslElement)this;
      results.putAll(thisElement.getVariableElements());
    }

    // Trace parents finding any variable elements present.
    GradleDslElement currentElement = this;
    while (currentElement != null && currentElement.getParent() != null) {
      currentElement = currentElement.getParent();
      if (currentElement instanceof GradlePropertiesDslElement) {
        GradlePropertiesDslElement element = (GradlePropertiesDslElement)currentElement;
        results.putAll(element.getVariableElements());
      }
    }

    // Get Ext properties from the GradleDslFile.
    if (currentElement instanceof GradleDslFile) {
      GradleDslFile file = (GradleDslFile)currentElement;
      while (file != null) {
        ExtDslElement ext = file.getPropertyElement(EXT_BLOCK_NAME, ExtDslElement.class);
        if (ext != null) {
          results.putAll(ext.getPropertyElements());
        }
        // Add properties file properties if it exists.
        GradleDslFile propertiesFile = file.getSiblingDslFile();
        if (propertiesFile != null) {
          results.putAll(propertiesFile.getPropertyElements());
        }
        file = file.getParentModuleDslFile();
      }
    }

    return results;
  }

  /**
   * Helpers to quick obtain a notification instance for this elements build context.
   *
   * @param type type reference of the given notification, see {@link NotificationTypeReference} for possible values.
   * @param <T>  type of the notification
   * @return the instance of the notification in the build model.
   */
  @NotNull
  public <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type) {
    return getDslFile().getContext().getNotificationForType(type);
  }

  public void registerDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    myDependents.add(injection);
  }

  public void unregisterDependent(@NotNull GradleReferenceInjection injection) {
    assert injection.isResolved() && injection.getToBeInjected() == this;
    assert myDependents.contains(injection);
    myDependents.remove(injection);
  }

  protected void unregisterAllDependants() {
    // We need to create a new array to avoid concurrent modification exceptions.
    myDependents.forEach(e -> {
      // Break the dependency.
      e.resolveWith(null);
      // Register with DependencyManager
      getDslFile().getContext().getDependencyManager().registerUnresolvedReference(e);
    });
    myDependents.clear();
  }

  /**
   * @return all things that depend on this element.
   */
  @NotNull
  public List<GradleReferenceInjection> getDependents() {
    return new ArrayList<>(myDependents);
  }

  /**
   * @return all resolved and unresolved dependencies.
   */
  @NotNull
  public List<GradleReferenceInjection> getDependencies() {
    return new ArrayList<>(myDependencies);
  }

  public void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement) {
    newElement.resolve();
    newElement.getDslFile().getContext().getDependencyManager().resolveWith(newElement);
  }

  public void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    // Switch dependents to point to the new element.
    List<GradleReferenceInjection> injections = oldElement.getDependents();
    oldElement.unregisterAllDependants();
    injections.forEach(e -> e.resolveWith(newElement));
    // Register all the dependents with this new element.
    injections.forEach(newElement::registerDependent);

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  public void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement) {
    List<GradleReferenceInjection> dependents = oldElement.getDependents();
    oldElement.unregisterAllDependants();

    // The property we remove could have been shadowing another one. Attempt to re-resolve all dependents.
    dependents.forEach(e -> e.getOriginElement().resolve());

    // Go though our dependencies and unregister us as a dependent.
    oldElement.getResolvedVariables().forEach(e -> {
      GradleDslElement toBeInjected = e.getToBeInjected();
      if (toBeInjected != null) {
        toBeInjected.unregisterDependent(e);
      }
    });
  }

  protected void resolve() {
  }

  protected void reorder() {
    if (myParent instanceof ExtDslElement) {
      ((ExtDslElement)myParent).reorderAndMaybeGetNewIndex(this);
    }
  }
}
