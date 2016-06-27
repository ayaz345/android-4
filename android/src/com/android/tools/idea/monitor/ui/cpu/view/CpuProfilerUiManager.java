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
package com.android.tools.idea.monitor.ui.cpu.view;

import com.android.tools.adtui.AccordionLayout;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.cpu.model.CpuDataPoller;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class CpuProfilerUiManager extends BaseProfilerUiManager {

  private ThreadsSegment myThreadSegment;

  private CpuDataPoller myCpuDataPoller;

  public CpuProfilerUiManager(@NotNull Range xRange,
                              @NotNull Choreographer choreographer,
                              @NotNull SeriesDataStore dataStore,
                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, dataStore, eventDispatcher);
  }

  @NotNull
  @Override
  public Poller createPoller(int pid) {
    myCpuDataPoller = new CpuDataPoller(myDataStore, pid);
    return myCpuDataPoller;
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(overviewPanel);

    myThreadSegment = new ThreadsSegment(myXRange, myDataStore, myEventDispatcher, null);
    assert myCpuDataPoller != null;
    myCpuDataPoller.setThreadAddedNotifier(myThreadSegment.getThreadAddedNotifier());
    myChoreographer.register(myThreadSegment);
    setupAndRegisterSegment(myThreadSegment, DEFAULT_MONITOR_MIN_HEIGHT, DEFAULT_MONITOR_PREFERRED_HEIGHT, DEFAULT_MONITOR_MAX_HEIGHT);
    overviewPanel.add(myThreadSegment);
    setSegmentState(overviewPanel, myThreadSegment, AccordionLayout.AccordionState.MAXIMIZE);
  }

  @Override
  public void resetProfiler(@NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(overviewPanel, detailPanel);

    myChoreographer.unregister(myThreadSegment);
    overviewPanel.remove(myThreadSegment);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new CpuUsageSegment(xRange, dataStore, eventDispatcher);
  }
}
