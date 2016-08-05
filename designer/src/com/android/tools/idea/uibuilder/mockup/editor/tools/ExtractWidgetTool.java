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
package com.android.tools.idea.uibuilder.mockup.editor.tools;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.MockupViewPanel;
import com.android.tools.idea.uibuilder.mockup.editor.WidgetCreator;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.JBColor;
import icons.AndroidIcons;

import javax.swing.*;
import java.awt.*;

/**
 * Tool Allowing the extraction of widget or layout from the current selection
 */
public class ExtractWidgetTool extends JPanel implements MockupEditor.Tool {

  private final MockupViewPanel myMockupViewPanel;
  private final WidgetCreator myWidgetCreator;
  private Rectangle mySelection;
  private MySelectionListener mySelectionListener;
  private float myAlpha = 0;
  private final MockupEditor.MockupEditorListener myMockupEditorListener;

  /**
   * @param mockup          Mockup used to extract new widget
   * @param screenView      Current screenView where the mockup is displayed
   * @param mockupViewPanel {@link MockupViewPanel} associated with this tool
   * @param mockupEditor
   */
  public ExtractWidgetTool(Mockup mockup, ScreenView screenView, MockupViewPanel mockupViewPanel, MockupEditor mockupEditor) {
    super();
    myMockupViewPanel = mockupViewPanel;
    mySelectionListener = new MySelectionListener();
    myWidgetCreator = new WidgetCreator(mockup, screenView);
    myMockupEditorListener = m -> hideTooltipActions();
    mockupEditor.addListener(myMockupEditorListener);
    setBorder(BorderFactory.createLineBorder(JBColor.background(),1, true));
    add(createActionButtons());
  }

  // TODO make it look nice
  @Override
  public void paint(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    final Composite composite = g2d.getComposite();
    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    super.paint(g2d);
    g2d.setComposite(composite);
  }

  private void updateMockup(Mockup mockup) {
    myWidgetCreator.setMockup(mockup);
  }

  /**
   * Display the buttons of this tool inside the {@link MockupViewPanel} next to selection
   *
   * @param selection the current selection in {@link MockupViewPanel}
   */
  private void displayTooltipActions(Rectangle selection) {
    mySelection = selection;
    myMockupViewPanel.removeAll();
    if (!selection.isEmpty()) {
      Timer timer = new Timer(20, e -> {
        float alpha = myAlpha;
        alpha += 0.1;
        if (alpha > 1) {
          alpha = 1;
          ((Timer)e.getSource()).setRepeats(false);
        }
        myAlpha = alpha;
        repaint();
      });
      timer.setRepeats(true);
      timer.setCoalesce(true);
      timer.start();
      myMockupViewPanel.add(this);
      myMockupViewPanel.doLayout();
    }
  }

  /**
   * hide the buttons of this tool inside the {@link MockupViewPanel} next to selection
   */
  private void hideTooltipActions() {
    myMockupViewPanel.remove(this);
    myAlpha = 0;
  }

  private JComponent createActionButtons() {
    final DefaultActionGroup group = new DefaultActionGroup(new NewWidgetAction(), new NewLayoutAction());
    final ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.UNKNOWN, group, false);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    actionToolbar.setTargetComponent(this);
    return actionToolbar.getComponent();
  }

  @Override
  public void enable(MockupViewPanel mockupViewPanel) {
    mockupViewPanel.addSelectionListener(mySelectionListener);
    mockupViewPanel.resetState();
  }

  @Override
  public void disable(MockupViewPanel mockupViewPanel) {
    mockupViewPanel.removeSelectionListener(mySelectionListener);
    hideTooltipActions();
  }

  /**
   * Action to create the new widget
   */
  private class NewWidgetAction extends AnAction {

    public static final String TITLE = "Create new widget from selection";

    public NewWidgetAction() {
      super(TITLE, TITLE, AndroidIcons.Mockup.CreateWidget);
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
      myWidgetCreator.createWidget(mySelection, SdkConstants.VIEW);
    }
  }

  /**
   * Action to create the new layout
   */
  private class NewLayoutAction extends AnAction {

    public static final String TITLE = "Create new layout from selection";

    public NewLayoutAction() {
      super(TITLE, TITLE, AndroidIcons.Mockup.CreateLayout);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myWidgetCreator.createNewIncludedLayout(mySelection);
    }
  }

  private class MySelectionListener implements MockupViewPanel.SelectionListener {
    @Override
    public void selectionStarted(MockupViewPanel mockupViewPanel, int x, int y) {
      hideTooltipActions();
    }

    @Override
    public void selectionEnded(MockupViewPanel mockupViewPanel, Rectangle selection) {
      displayTooltipActions(selection);
    }
  }
}
