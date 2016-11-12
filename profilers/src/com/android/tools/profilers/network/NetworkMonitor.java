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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NetworkMonitor extends ProfilerMonitor {

  private final int myProcessId;

  @NotNull
  private final StudioProfilers myProfilers;

  public NetworkMonitor(@NotNull StudioProfilers profilers, int pid) {
    myProcessId = pid;
    myProfilers = profilers;
  }

  @NotNull
  public RangedContinuousSeries getTrafficData() {
    NetworkServiceGrpc.NetworkServiceBlockingStub client = myProfilers.getClient().getNetworkClient();

    // TODO: Switch to the network dataseries, when bugs are sorted out:
    // NetworkTrafficDataSeries series = new NetworkTrafficDataSeries(myClient, pid, NetworkTrafficDataSeries.Type.BYTES_RECIEVED);
    DataSeries<Long> series = xRange -> {
      List<SeriesData<Long>> seriesData = new ArrayList<>();
      seriesData.add(new SeriesData<>((long)xRange.getMin(), 50L));
      seriesData.add(new SeriesData<>((long)xRange.getMax(), 50L));
      return ContainerUtil.immutableList(seriesData);
    };

    return new RangedContinuousSeries("Network", myProfilers.getViewRange(), new Range(0, 100), series);
  }

  public void expand() {
    myProfilers.setStage(new NeworkProfilerStage(myProfilers));
  }
}
