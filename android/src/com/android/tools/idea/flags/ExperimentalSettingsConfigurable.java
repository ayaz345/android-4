/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.flags;

import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT;
import static com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.SPECIFIED_LOCATION;
import static com.android.tools.idea.layoutlib.LayoutLibrary.LAYOUTLIB_NATIVE_PLUGIN;
import static com.android.tools.idea.layoutlib.LayoutLibrary.LAYOUTLIB_STANDARD_PLUGIN;
import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.ui.LayoutInspectorSettingsKt;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TitledSeparator;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class ExperimentalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  @NotNull private final GradleExperimentalSettings mySettings;
  @NotNull private final RenderSettings myRenderSettings;

  private JPanel myPanel;
  private JCheckBox myUseL2DependenciesCheckBox;
  private JCheckBox myUseSingleVariantSyncCheckbox;
  private JSlider myLayoutEditorQualitySlider;
  private JCheckBox myLayoutInspectorCheckbox;
  private TitledSeparator myLayoutInspectorSeparator;
  private JCheckBox mySkipGradleTasksList;
  private JCheckBox myUseLayoutlibNative;
  private JCheckBox myTraceGradleSyncCheckBox;
  private JComboBox<TraceProfileItem> myTraceProfileComboBox;
  private TextFieldWithBrowseButton myTraceProfilePathField;

  private Runnable myRestartCallback;

  @SuppressWarnings("unused") // called by IDE
  public ExperimentalSettingsConfigurable(@NotNull Project project) {
    this(GradleExperimentalSettings.getInstance(), RenderSettings.getProjectSettings(project));
  }

  @VisibleForTesting
  ExperimentalSettingsConfigurable(@NotNull GradleExperimentalSettings settings,
                                   @NotNull RenderSettings renderSettings) {
    mySettings = settings;
    myRenderSettings = renderSettings;

    // TODO make visible once Gradle Sync switches to L2 dependencies
    myUseL2DependenciesCheckBox.setVisible(false);

    Hashtable qualityLabels = new Hashtable();
    qualityLabels.put(new Integer(0), new JLabel("Fastest"));
    qualityLabels.put(new Integer(100), new JLabel("Slowest"));
    myLayoutEditorQualitySlider.setLabelTable(qualityLabels);
    myLayoutEditorQualitySlider.setPaintLabels(true);
    myLayoutEditorQualitySlider.setPaintTicks(true);
    myLayoutEditorQualitySlider.setMajorTickSpacing(25);
    boolean showLayoutInspectorSettings = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLED.get();
    myLayoutInspectorSeparator.setVisible(showLayoutInspectorSettings);
    myLayoutInspectorCheckbox.setVisible(showLayoutInspectorSettings);
    initTraceComponents();
    reset();
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.experimental";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Experimental";
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Override
  @NotNull
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return mySettings.USE_L2_DEPENDENCIES_ON_SYNC != isUseL2DependenciesInSync() ||
           mySettings.USE_SINGLE_VARIANT_SYNC != isUseSingleVariantSync() ||
           mySettings.SKIP_GRADLE_TASKS_LIST != skipGradleTasksList() ||
           mySettings.TRACE_GRADLE_SYNC != traceGradleSync() ||
           mySettings.TRACE_PROFILE_SELECTION != getTraceProfileSelection() ||
           !mySettings.TRACE_PROFILE_LOCATION.equals(getTraceProfileLocation()) ||
           (int)(myRenderSettings.getQuality() * 100) != getQualitySetting() ||
           myLayoutInspectorCheckbox.isSelected() != LayoutInspectorSettingsKt.getEnableLiveLayoutInspector() ||
           (myUseLayoutlibNative.isSelected() == PluginManagerCore.isDisabled(LAYOUTLIB_NATIVE_PLUGIN));
  }

  private int getQualitySetting() {
    return myLayoutEditorQualitySlider.getValue();
  }

  @Override
  public void apply() throws ConfigurationException {
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = isUseL2DependenciesInSync();
    mySettings.USE_SINGLE_VARIANT_SYNC = isUseSingleVariantSync();
    mySettings.SKIP_GRADLE_TASKS_LIST = skipGradleTasksList();
    mySettings.TRACE_GRADLE_SYNC = traceGradleSync();
    mySettings.TRACE_PROFILE_SELECTION = getTraceProfileSelection();
    mySettings.TRACE_PROFILE_LOCATION = getTraceProfileLocation();

    myRenderSettings.setQuality(getQualitySetting() / 100f);

    LayoutInspectorSettingsKt.setEnableLiveLayoutInspector(myLayoutInspectorCheckbox.isSelected());
    if (myUseLayoutlibNative.isSelected() == PluginManagerCore.isDisabled(LAYOUTLIB_NATIVE_PLUGIN)) {
      myRestartCallback = () -> ApplicationManager.getApplication().invokeLater(() -> PluginManagerConfigurable.shutdownOrRestartApp());
      LayoutEditorEvent.Builder eventBuilder = LayoutEditorEvent.newBuilder();
      if (myUseLayoutlibNative.isSelected()) {
        eventBuilder.setType(LayoutEditorEvent.LayoutEditorEventType.ENABLE_LAYOUTLIB_NATIVE);
        PluginManagerCore.enablePlugin(LAYOUTLIB_NATIVE_PLUGIN);
      }
      else {
        eventBuilder.setType(LayoutEditorEvent.LayoutEditorEventType.DISABLE_LAYOUTLIB_NATIVE);
        PluginManagerCore.disablePlugin(LAYOUTLIB_NATIVE_PLUGIN);
        PluginManagerCore.enablePlugin(LAYOUTLIB_STANDARD_PLUGIN);
      }
      AndroidStudioEvent.Builder studioEvent = AndroidStudioEvent.newBuilder()
        .setCategory(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR)
        .setKind(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT)
        .setLayoutEditorEvent(eventBuilder.build());
      UsageTracker.log(studioEvent);
    }
  }

  @Override
  public void disposeUIResources() {
    if (myRestartCallback != null) {
      myRestartCallback.run();
      myRestartCallback = null;
    }
  }

  @VisibleForTesting
  boolean isUseL2DependenciesInSync() {
    return myUseL2DependenciesCheckBox.isSelected();
  }

  @TestOnly
  void setUseL2DependenciesInSync(boolean value) {
    myUseL2DependenciesCheckBox.setSelected(value);
  }

  boolean isUseSingleVariantSync() {
    return myUseSingleVariantSyncCheckbox.isSelected();
  }

  @TestOnly
  void setUseSingleVariantSync(boolean value) {
    myUseSingleVariantSyncCheckbox.setSelected(value);
  }

  boolean skipGradleTasksList() {
    return mySkipGradleTasksList.isSelected();
  }

  @TestOnly
  void setSkipGradleTasksList(boolean value) {
    mySkipGradleTasksList.setSelected(value);
  }

  boolean traceGradleSync() {
    return myTraceGradleSyncCheckBox.isSelected();
  }

  @TestOnly
  void setTraceGradleSync(boolean value) {
    myTraceGradleSyncCheckBox.setSelected(value);
  }

  @Nullable
  TraceProfileItem getTraceProfileSelection() {
    return (TraceProfileItem)myTraceProfileComboBox.getSelectedItem();
  }

  @TestOnly
  void setTraceProfileSelection(@NotNull TraceProfileItem value) {
    myTraceProfileComboBox.setSelectedItem(value);
  }

  @NotNull
  String getTraceProfileLocation() {
    return myTraceProfilePathField.getText();
  }

  @TestOnly
  void setTraceProfileLocation(@NotNull String value) {
    myTraceProfilePathField.setText(value);
  }

  private void initTraceComponents() {
    myTraceGradleSyncCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateTraceComponents();
      }
    });

    myTraceProfileComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myTraceProfilePathField.setEnabled(SPECIFIED_LOCATION.equals(myTraceProfileComboBox.getSelectedItem()));
      }
    });

    myTraceProfilePathField.addBrowseFolderListener("Trace Profile", "Please select trace profile",
                                                    null, createSingleFileDescriptor("profile"));
    myTraceProfileComboBox.addItem(DEFAULT);
    myTraceProfileComboBox.addItem(SPECIFIED_LOCATION);
  }


  private void updateTraceComponents() {
    myTraceProfileComboBox.setEnabled(myTraceGradleSyncCheckBox.isSelected());
    // Enable text field only if trace is enabled, and using profile from local disk.
    myTraceProfilePathField
      .setEnabled(myTraceGradleSyncCheckBox.isSelected() && SPECIFIED_LOCATION.equals(myTraceProfileComboBox.getSelectedItem()));
  }

  @Override
  public void reset() {
    myUseL2DependenciesCheckBox.setSelected(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
    myUseSingleVariantSyncCheckbox.setSelected(mySettings.USE_SINGLE_VARIANT_SYNC);
    mySkipGradleTasksList.setSelected(mySettings.SKIP_GRADLE_TASKS_LIST);
    myLayoutEditorQualitySlider.setValue((int)(myRenderSettings.getQuality() * 100));
    myLayoutInspectorCheckbox.setSelected(LayoutInspectorSettingsKt.getEnableLiveLayoutInspector());
    myUseLayoutlibNative.setSelected(!PluginManagerCore.isDisabled(LAYOUTLIB_NATIVE_PLUGIN));
    myTraceGradleSyncCheckBox.setSelected(mySettings.TRACE_GRADLE_SYNC);
    myTraceProfileComboBox.setSelectedItem(mySettings.TRACE_PROFILE_SELECTION);
    myTraceProfilePathField.setText(mySettings.TRACE_PROFILE_LOCATION);
    updateTraceComponents();
  }

  public enum TraceProfileItem {
    DEFAULT("Default profile"),
    SPECIFIED_LOCATION("Specified location");

    private String displayValue;

    TraceProfileItem(@NotNull String value) {
      displayValue = value;
    }

    @Override
    public String toString() {
      return displayValue;
    }
  }
}
