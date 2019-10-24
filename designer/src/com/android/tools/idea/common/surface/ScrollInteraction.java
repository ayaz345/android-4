/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.surface;

import static java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL;

import com.android.annotations.NonNull;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.uibuilder.api.ScrollHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.util.EventObject;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link Interaction} that provides support for scrollable components like ScrollView
 */
public class ScrollInteraction extends Interaction {
  // This handles the max scroll speed
  private static final int MAX_SCROLL_MULTIPLIER = 5;

  private final ScrollHandler myHandler;
  private int myScrolledAmount;
  private short myLastScrollSign;
  /**
   * The scroll multiplier will increment in every scroll call. This allows that, when bundling multiple scroll events, the scroll
   * accelerates until it reaches {@link #MAX_SCROLL_MULTIPLIER}
   */
  private int myScrollMultiplier = 1;
  private SceneView mySceneView;

  public ScrollInteraction(@NonNull SceneView sceneView, @NonNull ScrollHandler scrollHandler) {
    mySceneView = sceneView;
    myHandler = scrollHandler;
  }

  /**
   * Creates a new {@link ScrollInteraction} if any of the components in the component hierarchy can handle scrolling.
   * @return the {@link ScrollInteraction} or null if none of the components handle the scrolling
   */
  @Nullable
  public static ScrollInteraction createScrollInteraction(@NonNull SceneView sceneView, @NonNull NlComponent component) {
    NlComponent currentComponent = component;
    ScrollHandler scrollHandler = null;
    ViewEditor editor = new ViewEditorImpl(sceneView);

    // Find the component that is the lowest in the hierarchy and can take the scrolling events
    while (currentComponent != null) {
      ViewHandler viewHandler = NlComponentHelperKt.getViewHandler(currentComponent);
      if (viewHandler instanceof ViewGroupHandler) {
        ViewGroupHandler viewGroupHandler = (ViewGroupHandler)viewHandler;
        scrollHandler = viewGroupHandler.createScrollHandler(editor, currentComponent);

        if (scrollHandler != null) {
          break;
        }
      }

      currentComponent = currentComponent.getParent();
    }

    if (scrollHandler == null) {
      return null;
    }
    return new ScrollInteraction(sceneView, scrollHandler);
  }

  @Override
  public void begin(@NotNull EventObject event, @NotNull InteractionInformation interactionInformation) {
    assert event instanceof MouseWheelEvent;
    MouseWheelEvent mouseEvent = (MouseWheelEvent) event;
    begin(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
  }

  @Override
  public void update(@NotNull EventObject event, @NotNull InteractionInformation interactionInformation) {
    if (event instanceof MouseWheelEvent) {
      MouseWheelEvent mouseWheelEvent = (MouseWheelEvent)event;
      int x = mouseWheelEvent.getX();
      int y = mouseWheelEvent.getY();
      int scrollAmount = mouseWheelEvent.getScrollType() == WHEEL_UNIT_SCROLL ? mouseWheelEvent.getUnitsToScroll()
                                                                              : (mouseWheelEvent.getWheelRotation() < 0 ? -1 : 1);
      DesignSurface surface = mySceneView.getSurface();

      SceneView sceneView = surface.getSceneView(x, y);
      if (sceneView == null) {
        mouseWheelEvent.getComponent().getParent().dispatchEvent(mouseWheelEvent);
        return;
      }

      final NlComponent component = Coordinates.findComponent(sceneView, x, y);
      if (component == null) {
        // There is no component consuming the scroll
        mouseWheelEvent.getComponent().getParent().dispatchEvent(mouseWheelEvent);
        return;
      }

      if (!canScroll(scrollAmount)) {
        JScrollPane scrollPane = surface.getScrollPane();
        JViewport viewport = scrollPane.getViewport();
        Dimension extentSize = viewport.getExtentSize();
        Dimension viewSize = viewport.getViewSize();
        if (viewSize.width > extentSize.width || viewSize.height > extentSize.height) {
          mouseWheelEvent.getComponent().getParent().dispatchEvent(mouseWheelEvent);
          return;
        }
      }
      scroll(x, y, scrollAmount);
    }
  }

  @Override
  public void scroll(@SwingCoordinate int x, @SwingCoordinate int y, int scrollAmount) {
    short currentScrollSign = (short)(scrollAmount < 0 ? -1 : 0);

    if (myLastScrollSign != currentScrollSign) {
      // The scroll has changed direction so reset the fast scrolling
      myScrollMultiplier = 1;
      myLastScrollSign = currentScrollSign;
    }
    else if (myScrollMultiplier < MAX_SCROLL_MULTIPLIER) {
      myScrollMultiplier += 1;
    }

    int newScrolledAmount = myScrolledAmount + scrollAmount * myScrollMultiplier;
    int scrolled = myHandler.update(newScrolledAmount);

    if (scrolled != 0) {
      myScrolledAmount += scrollAmount;
      mySceneView.getSceneManager().requestLayoutAndRender(false);
    }
  }

  @Override
  public void commit(@Nullable EventObject event, @NotNull InteractionInformation interactionInformation) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    end(interactionInformation.getX(), interactionInformation.getY(), interactionInformation.getModifiersEx());
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    // Reset scroll multiplier back to 1
    myScrollMultiplier = 1;
    myHandler.commit(myScrolledAmount);
    myScrolledAmount = 0;
  }

  @Override
  public void cancel(@Nullable EventObject event, @NotNull InteractionInformation interactionInformation) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    cancel(interactionInformation.getX(), interactionInformation.getY(), interactionInformation.getModifiersEx());
  }

  @Override
  public void cancel(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    // Make sure we reset the scroll to where it was
    myHandler.update(0);
    mySceneView.getSceneManager().requestLayoutAndRender(false);
  }

  public boolean canScroll(int scrollAmount) {
    return myHandler.canScroll(scrollAmount);
  }
}
