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
package com.android.tools.profilers;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_BOTTOM_BORDER;
import static com.android.tools.profilers.ProfilerFonts.H4_FONT;
import static com.android.tools.profilers.ProfilerLayout.TOOLBAR_HEIGHT;
import static com.android.tools.profilers.sessions.SessionsView.SESSION_EXPANDED_WIDTH;
import static com.android.tools.profilers.sessions.SessionsView.SESSION_IS_COLLAPSED;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.stdui.CommonToggleButton;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.cpu.CpuCaptureStage;
import com.android.tools.profilers.cpu.CpuCaptureStageView;
import com.android.tools.profilers.cpu.CpuProfilerStage;
import com.android.tools.profilers.cpu.CpuProfilerStageView;
import com.android.tools.profilers.customevent.CustomEventProfilerStage;
import com.android.tools.profilers.customevent.CustomEventProfilerStageView;
import com.android.tools.profilers.energy.EnergyProfilerStage;
import com.android.tools.profilers.energy.EnergyProfilerStageView;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.MemoryProfilerStageView;
import com.android.tools.profilers.network.NetworkProfilerStage;
import com.android.tools.profilers.network.NetworkProfilerStageView;
import com.android.tools.profilers.sessions.SessionAspect;
import com.android.tools.profilers.sessions.SessionsView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiFunction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

public class StudioProfilersView extends AspectObserver implements Disposable {
  private final static String LOADING_VIEW_CARD = "LoadingViewCard";
  private final static String STAGE_VIEW_CARD = "StageViewCard";
  private static final int SHORTCUT_MODIFIER_MASK_NUMBER = SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  @NotNull public static final String ATTACH_LIVE = "Attach to live";
  @NotNull public static final String DETACH_LIVE = "Detach live";
  @NotNull public static final String ZOOM_IN = "Zoom in";
  @NotNull public static final String ZOOM_OUT = "Zoom out";

  private final StudioProfilers myProfiler;
  private final ViewBinder<StudioProfilersView, Stage, StageView> myBinder;
  private StageView myStageView;

  @NotNull
  private final ProfilerLayeredPane myLayeredPane;
  /**
   * Splitter between the sessions and main profiler stage panel. We use IJ's {@link ThreeComponentsSplitter} as it supports zero-width
   * divider while still handling mouse resize properly.
   */
  @NotNull private final ThreeComponentsSplitter mySplitter;
  @NotNull private final LoadingPanel myStageLoadingPanel;
  private final JPanel myStageComponent;
  private final JPanel myStageCenterComponent;
  private final CardLayout myStageCenterCardLayout;
  private SessionsView mySessionsView;
  private JPanel myToolbar;
  private JPanel myStageToolbar;
  private JPanel myCommonToolbar;
  private JPanel myGoLiveToolbar;
  private JToggleButton myGoLive;
  private CommonButton myZoomOut;
  private CommonButton myZoomIn;
  private CommonButton myResetZoom;
  private CommonButton myFrameSelection;
  private ProfilerAction myFrameSelectionAction;

  @NotNull
  private final IdeProfilerComponents myIdeProfilerComponents;

  public StudioProfilersView(@NotNull StudioProfilers profiler, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myProfiler = profiler;
    myIdeProfilerComponents = ideProfilerComponents;
    myStageView = null;
    myStageComponent = new JPanel(new BorderLayout());
    myStageCenterCardLayout = new CardLayout();
    myStageCenterComponent = new JPanel(myStageCenterCardLayout);

    myStageLoadingPanel = myIdeProfilerComponents.createLoadingPanel(0);
    myStageLoadingPanel.setLoadingText("");
    myStageLoadingPanel.getComponent().setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    mySplitter = new ThreeComponentsSplitter();
    // Override the splitter's custom traversal policy back to the default, because the custom policy prevents the profilers from tabbing
    // across the components (e.g. sessions panel and the main stage UI).
    mySplitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    mySplitter.setDividerWidth(0);
    mySplitter.setDividerMouseZoneSize(-1);
    mySplitter.setHonorComponentsMinimumSize(true);
    mySplitter.setLastComponent(myStageComponent);
    Disposer.register(this, mySplitter);

    myLayeredPane = new ProfilerLayeredPane(mySplitter);
    initializeSessionUi();
    initializeStageUi();

    myBinder = new ViewBinder<>();
    myBinder.bind(StudioMonitorStage.class, StudioMonitorStageView::new);
    myBinder.bind(CpuProfilerStage.class, CpuProfilerStageView::new);
    myBinder.bind(CpuCaptureStage.class, CpuCaptureStageView::new);
    myBinder.bind(MemoryProfilerStage.class, MemoryProfilerStageView::new);
    myBinder.bind(NetworkProfilerStage.class, NetworkProfilerStageView::new);
    myBinder.bind(NullMonitorStage.class, NullMonitorStageView::new);
    myBinder.bind(EnergyProfilerStage.class, EnergyProfilerStageView::new);
    myBinder.bind(CustomEventProfilerStage.class, CustomEventProfilerStageView::new);

    myProfiler.addDependency(this)
      .onChange(ProfilerAspect.STAGE, this::updateStageView)
      .onChange(ProfilerAspect.AGENT, this::toggleStageLayout)
      .onChange(ProfilerAspect.PREFERRED_PROCESS, this::toggleStageLayout);
    updateStageView();
    toggleStageLayout();
  }

  @Override
  public void dispose() {
  }

  @VisibleForTesting
  public <S extends Stage, T extends StageView> void bind(@NotNull Class<S> clazz,
                                                          @NotNull BiFunction<StudioProfilersView, S, T> constructor) {
    myBinder.bind(clazz, constructor);
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomInButton() {
    return myZoomIn;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getZoomOutButton() {
    return myZoomOut;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getResetZoomButton() {
    return myResetZoom;
  }

  @VisibleForTesting
  @NotNull
  CommonButton getFrameSelectionButton() {
    return myFrameSelection;
  }

  @VisibleForTesting
  @NotNull
  JToggleButton getGoLiveButton() {
    return myGoLive;
  }

  @VisibleForTesting
  public StageView getStageView() {
    return myStageView;
  }

  @VisibleForTesting
  @NotNull
  SessionsView getSessionsView() {
    return mySessionsView;
  }

  @NotNull
  public StudioProfilers getStudioProfilers() {
    return myProfiler;
  }

  private void initializeSessionUi() {
    mySessionsView = new SessionsView(myProfiler, myIdeProfilerComponents);
    JComponent sessionsComponent = mySessionsView.getComponent();
    mySplitter.setFirstComponent(sessionsComponent);
    mySessionsView.addExpandListener(e -> {
      toggleSessionsPanel(false);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(true);
    });
    mySessionsView.addCollapseListener(e -> {
      toggleSessionsPanel(true);
      myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelStateChanged(false);
    });
    boolean initiallyCollapsed =
      myProfiler.getIdeServices().getPersistentProfilerPreferences().getBoolean(SESSION_IS_COLLAPSED, false);
    toggleSessionsPanel(initiallyCollapsed);

    // Track Sessions UI resize event.
    // The divider mechanism within ThreeComponentsSplitter consumes the mouse event so we cannot use regular mouse listeners on the
    // splitter itself. Instead, we mirror the logic that the divider uses to capture mouse event and check whether the width of the
    // sessions UI has changed between mouse press and release. Using Once here to mimic ThreeComponentsSplitter's implementation, as
    // we only need to add the MousePreprocessor to the glassPane once when the UI shows up.
    new UiNotifyConnector.Once(mySplitter, new Activatable.Adapter() {
      @Override
      public void showNotify() {
        IdeGlassPane glassPane = IdeGlassPaneUtil.find(mySplitter);
        glassPane.addMousePreprocessor(new MouseAdapter() {
          private int mySessionsUiWidth;

          @Override
          public void mousePressed(MouseEvent e) {
            mySessionsUiWidth = sessionsComponent.getWidth();
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            int width = sessionsComponent.getWidth();
            if (mySessionsUiWidth != width) {
              myProfiler.getIdeServices().getPersistentProfilerPreferences().setInt(SESSION_EXPANDED_WIDTH, width);
              myProfiler.getIdeServices().getFeatureTracker().trackSessionsPanelResized();
            }
          }
        }, mySplitter);
      }
    });
  }

  private void initializeStageUi() {
    myToolbar = new JPanel(new BorderLayout());
    JPanel leftToolbar = new JPanel(ProfilerLayout.createToolbarLayout());

    myToolbar.setBorder(DEFAULT_BOTTOM_BORDER);
    myToolbar.setPreferredSize(new Dimension(0, TOOLBAR_HEIGHT));

    myCommonToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    JButton button = new CommonButton(AllIcons.Actions.Back);
    button.addActionListener(action -> {
      myProfiler.setMonitoringStage();
      myProfiler.getIdeServices().getFeatureTracker().trackGoBack();
    });
    myCommonToolbar.add(button);
    myCommonToolbar.add(new FlatSeparator());

    JComboBox<Class<? extends Stage>> stageCombo = new FlatComboBox<>();
    JComboBoxView stages = new JComboBoxView<>(stageCombo, myProfiler, ProfilerAspect.STAGE,
                                               myProfiler::getDirectStages,
                                               myProfiler::getStageClass,
                                               stage -> {
                                                 // Track first, so current stage is sent with the event
                                                 myProfiler.getIdeServices().getFeatureTracker().trackSelectMonitor();
                                                 myProfiler.setNewStage(stage);
                                               });
    stageCombo.setRenderer(new StageComboBoxRenderer());
    stages.bind();
    myCommonToolbar.add(stageCombo);
    myCommonToolbar.add(new FlatSeparator());
    leftToolbar.add(myCommonToolbar);
    myToolbar.add(leftToolbar, BorderLayout.WEST);

    JPanel rightToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myToolbar.add(rightToolbar, BorderLayout.EAST);
    rightToolbar.setBorder(new JBEmptyBorder(0, 0, 0, 2));

    myZoomOut = new CommonButton(AllIcons.General.ZoomOut);
    myZoomOut.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomOut));
    myZoomOut.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomOut();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomOut();
    });
    ProfilerAction zoomOutAction =
      new ProfilerAction.Builder(ZOOM_OUT).setContainerComponent(mySplitter).setActionRunnable(() -> myZoomOut.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();

    myZoomOut.setToolTipText(zoomOutAction.getDefaultToolTipText());
    rightToolbar.add(myZoomOut);

    myZoomIn = new CommonButton(AllIcons.General.ZoomIn);
    myZoomIn.setDisabledIcon(IconLoader.getDisabledIcon(AllIcons.General.ZoomIn));
    myZoomIn.addActionListener(event -> {
      myStageView.getStage().getTimeline().zoomIn();
      myProfiler.getIdeServices().getFeatureTracker().trackZoomIn();
    });
    ProfilerAction zoomInAction =
      new ProfilerAction.Builder(ZOOM_IN).setContainerComponent(mySplitter)
        .setActionRunnable(() -> myZoomIn.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, SHORTCUT_MODIFIER_MASK_NUMBER),
                       KeyStroke.getKeyStroke(KeyEvent.VK_ADD, SHORTCUT_MODIFIER_MASK_NUMBER)).build();
    myZoomIn.setToolTipText(zoomInAction.getDefaultToolTipText());
    rightToolbar.add(myZoomIn);

    myResetZoom = new CommonButton(StudioIcons.Common.RESET_ZOOM);
    myResetZoom.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.RESET_ZOOM));
    myResetZoom.addActionListener(event -> {
      myStageView.getStage().getTimeline().resetZoom();
      myProfiler.getIdeServices().getFeatureTracker().trackResetZoom();
    });
    ProfilerAction resetZoomAction =
      new ProfilerAction.Builder("Reset zoom").setContainerComponent(mySplitter)
        .setActionRunnable(() -> myResetZoom.doClick(0))
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0),
                       KeyStroke.getKeyStroke(KeyEvent.VK_0, 0)).build();
    myResetZoom.setToolTipText(resetZoomAction.getDefaultToolTipText());
    rightToolbar.add(myResetZoom);

    myFrameSelection = new CommonButton(StudioIcons.Common.ZOOM_SELECT);
    myFrameSelection.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Common.ZOOM_SELECT));
    myFrameSelection.addActionListener(
      event -> myStageView.getStage().getTimeline().frameViewToRange(myStageView.getStage().getTimeline().getSelectionRange())
    );
    myFrameSelectionAction = new ProfilerAction.Builder("Zoom to Selection")
      .setContainerComponent(mySplitter)
      .setActionRunnable(() -> myFrameSelection.doClick(0))
      .setEnableBooleanSupplier(() -> myStageView != null && !myStageView.getStage().getTimeline().getSelectionRange().isEmpty())
      .build();
    myFrameSelection.setToolTipText(myFrameSelectionAction.getDefaultToolTipText());
    rightToolbar.add(myFrameSelection);

    myGoLiveToolbar = new JPanel(ProfilerLayout.createToolbarLayout());
    myGoLiveToolbar.add(new FlatSeparator());

    myGoLive = new CommonToggleButton("", StudioIcons.Profiler.Toolbar.GOTO_LIVE);
    myGoLive.setDisabledIcon(IconLoader.getDisabledIcon(StudioIcons.Profiler.Toolbar.GOTO_LIVE));
    myGoLive.setFont(H4_FONT);
    myGoLive.setHorizontalTextPosition(SwingConstants.LEFT);
    myGoLive.setHorizontalAlignment(SwingConstants.LEFT);
    myGoLive.setBorder(new JBEmptyBorder(3, 7, 3, 7));
    // Configure shortcuts for GoLive.
    ProfilerAction attachAction =
      new ProfilerAction.Builder(ATTACH_LIVE).setContainerComponent(mySplitter)
        .setActionRunnable(() -> myGoLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> myGoLive.isEnabled() &&
                !myGoLive.isSelected() &&
                myStageView.navigationControllersEnabled())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, SHORTCUT_MODIFIER_MASK_NUMBER))
        .build();
    ProfilerAction detachAction =
      new ProfilerAction.Builder(DETACH_LIVE).setContainerComponent(mySplitter)
        .setActionRunnable(() -> myGoLive.doClick(0))
        .setEnableBooleanSupplier(
          () -> myGoLive.isEnabled() &&
                myGoLive.isSelected() &&
                myStageView.navigationControllersEnabled())
        .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)).build();

    myGoLive.setToolTipText(detachAction.getDefaultToolTipText());
    myGoLive.addActionListener(event -> {
      Timeline currentStageTimeline = myStageView.getStage().getTimeline();
      assert currentStageTimeline instanceof StreamingTimeline;
      ((StreamingTimeline)currentStageTimeline).toggleStreaming();
      myProfiler.getIdeServices().getFeatureTracker().trackToggleStreaming();
    });
    myGoLive.addChangeListener(e -> {
      boolean isSelected = myGoLive.isSelected();
      myGoLive.setIcon(isSelected ? StudioIcons.Profiler.Toolbar.PAUSE_LIVE : StudioIcons.Profiler.Toolbar.GOTO_LIVE);
      myGoLive.setToolTipText(isSelected ? detachAction.getDefaultToolTipText() : attachAction.getDefaultToolTipText());
    });
    myProfiler.getTimeline().addDependency(this).onChange(StreamingTimeline.Aspect.STREAMING, this::updateStreaming);
    myGoLiveToolbar.add(myGoLive);
    rightToolbar.add(myGoLiveToolbar);

    ProfilerContextMenu.createIfAbsent(myStageComponent)
      .add(attachAction, detachAction, ContextMenuItem.SEPARATOR, zoomInAction, zoomOutAction);
    myProfiler.getSessionsManager().addDependency(this).onChange(SessionAspect.SELECTED_SESSION, this::toggleTimelineButtons);
    toggleTimelineButtons();

    myStageToolbar = new JPanel(new BorderLayout());
    myToolbar.add(myStageToolbar, BorderLayout.CENTER);

    myStageComponent.add(myToolbar, BorderLayout.NORTH);
    myStageComponent.add(myStageCenterComponent, BorderLayout.CENTER);

    updateStreaming();
  }

  private void toggleTimelineButtons() {
    boolean isAlive = myProfiler.getSessionsManager().isSessionAlive();
    if (isAlive) {
      Common.AgentData agentData = myProfiler.getAgentData();
      boolean waitForAgent = agentData.getStatus() == Common.AgentData.Status.UNSPECIFIED;
      if (waitForAgent) {
        // Disable all controls if the agent is still initialization/attaching.
        myZoomOut.setEnabled(false);
        myZoomIn.setEnabled(false);
        myResetZoom.setEnabled(false);
        myFrameSelection.setEnabled(false);
        myGoLive.setEnabled(false);
        myGoLive.setSelected(false);
      }
      else {
        myZoomOut.setEnabled(true);
        myZoomIn.setEnabled(true);
        myResetZoom.setEnabled(true);
        myFrameSelection.setEnabled(myFrameSelectionAction.isEnabled());
        myGoLive.setEnabled(true);
        myGoLive.setSelected(true);
      }
    }
    else {
      boolean isValidSession = !Common.Session.getDefaultInstance().equals(myProfiler.getSessionsManager().getSelectedSession());
      myZoomOut.setEnabled(isValidSession);
      myZoomIn.setEnabled(isValidSession);
      myResetZoom.setEnabled(isValidSession);
      myFrameSelection.setEnabled(isValidSession && myFrameSelectionAction.isEnabled());
      myGoLive.setEnabled(false);
      myGoLive.setSelected(false);
    }
  }

  private void toggleSessionsPanel(boolean isCollapsed) {
    if (isCollapsed) {
      mySplitter.setDividerMouseZoneSize(-1);
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(false));
      // Let the Sessions panel min size govern how much space to reserve on the left.
      mySplitter.setFirstSize(0);
    }
    else {
      mySplitter.setDividerMouseZoneSize(JBUI.scale(10));
      mySessionsView.getComponent().setMinimumSize(SessionsView.getComponentMinimizeSize(true));
      mySplitter
        .setFirstSize(myProfiler.getIdeServices().getPersistentProfilerPreferences().getInt(SESSION_EXPANDED_WIDTH, 0));
    }

    mySplitter.revalidate();
    mySplitter.repaint();
  }

  private void updateStreaming() {
    myGoLive.setSelected(myProfiler.getTimeline().isStreaming());
  }

  private void updateStageView() {
    Stage stage = myProfiler.getStage();
    if (myStageView != null && myStageView.getStage() == stage) {
      return;
    }

    if (myStageView != null) {
      myStageView.getStage().getTimeline().getSelectionRange().removeDependencies(this);
    }
    myStageView = myBinder.build(this, stage);
    myStageView.getStage().getTimeline().getSelectionRange().addDependency(this)
      .onChange(Range.Aspect.RANGE, () -> myFrameSelection.setEnabled(myFrameSelectionAction.isEnabled()));
    SwingUtilities.invokeLater(() -> {
      Component focussed = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (focussed == null || !SwingUtilities.isDescendingFrom(focussed, mySplitter)) {
        mySplitter.requestFocusInWindow();
      }
    });

    myStageCenterComponent.removeAll();
    myStageCenterComponent.add(myStageView.getComponent(), STAGE_VIEW_CARD);
    myStageCenterComponent.add(myStageLoadingPanel.getComponent(), LOADING_VIEW_CARD);
    myStageCenterComponent.revalidate();
    myStageToolbar.removeAll();
    myStageToolbar.add(myStageView.getToolbar(), BorderLayout.CENTER);
    myStageToolbar.revalidate();
    myToolbar.setVisible(myStageView.isToolbarVisible());
    myGoLiveToolbar.setVisible(myStageView.navigationControllersEnabled());

    boolean topLevel = myStageView == null || myStageView.needsProcessSelection();
    myCommonToolbar.setVisible(!topLevel && myStageView.navigationControllersEnabled());
  }

  private void toggleStageLayout() {
    // Show the loading screen if StudioProfilers is waiting for a process to profile or if it is waiting for an agent to attach.
    boolean loading = (myProfiler.getAutoProfilingEnabled() && myProfiler.getPreferredProcessName() != null) &&
                      !myProfiler.getSessionsManager().isSessionAlive();
    Common.AgentData agentData = myProfiler.getAgentData();
    loading |= (agentData.getStatus() == Common.AgentData.Status.UNSPECIFIED && myProfiler.getSessionsManager().isSessionAlive());

    // Show the loading screen only if the device is supported.
    loading &= (myProfiler.getDevice() != null && myProfiler.getDevice().getUnsupportedReason().isEmpty());

    if (loading) {
      myStageLoadingPanel.startLoading();
      myStageCenterCardLayout.show(myStageCenterComponent, LOADING_VIEW_CARD);
    }
    else {
      myStageLoadingPanel.stopLoading();
      myStageCenterCardLayout.show(myStageCenterComponent, STAGE_VIEW_CARD);
    }
    toggleTimelineButtons();
  }

  @NotNull
  public JLayeredPane getComponent() {
    return myLayeredPane;
  }

  /**
   * Installs the {@link ContextMenuItem} common to all profilers.
   *
   * @param component
   */
  public void installCommonMenuItems(@NotNull JComponent component) {
    ContextMenuInstaller contextMenuInstaller = getIdeProfilerComponents().createContextMenuInstaller();
    ProfilerContextMenu.createIfAbsent(myStageComponent).getContextMenuItems()
      .forEach(item -> contextMenuInstaller.installGenericContextMenu(component, item));
  }

  @VisibleForTesting
  final JPanel getStageComponent() {
    return myStageComponent;
  }

  @VisibleForTesting
  final JComponent getStageLoadingComponent() {
    return myStageLoadingPanel.getComponent();
  }

  @VisibleForTesting
  final JComponent getStageViewComponent() {
    return myStageView.getComponent();
  }

  @VisibleForTesting
  public static class StageComboBoxRenderer extends ColoredListCellRenderer<Class> {

    private static ImmutableMap<Class<? extends Stage>, String> CLASS_TO_NAME = ImmutableMap.of(
      CpuProfilerStage.class, "CPU",
      MemoryProfilerStage.class, "MEMORY",
      NetworkProfilerStage.class, "NETWORK",
      EnergyProfilerStage.class, "ENERGY",
      CustomEventProfilerStage.class, "CUSTOM EVENTS");

    @Override
    protected void customizeCellRenderer(@NotNull JList list, Class value, int index, boolean selected, boolean hasFocus) {
      String name = CLASS_TO_NAME.get(value);
      append(name == null ? "[UNKNOWN]" : name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }


  @NotNull
  public IdeProfilerComponents getIdeProfilerComponents() {
    return myIdeProfilerComponents;
  }
}
