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
package com.android.tools.profilers.energy

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent
import com.android.tools.profiler.proto.Profiler
import com.android.tools.profiler.protobuf3jarjar.ByteString
import com.android.tools.profilers.*
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.JTextPane

class EnergyDetailsViewTest {
  private val wakeLockAcquired = EnergyProfiler.WakeLockAcquired.newBuilder()
    .setTag("wakeLockTag")
    .setLevel(EnergyProfiler.WakeLockAcquired.Level.SCREEN_DIM_WAKE_LOCK)
    .addFlags(EnergyProfiler.WakeLockAcquired.CreationFlag.ACQUIRE_CAUSES_WAKEUP)
    .build()
  private val wakeLockDuration = EventDuration(Arrays.asList(
    EnergyEvent.newBuilder().setTimestamp(1000L).setWakeLockAcquired(wakeLockAcquired).setTraceId("traceId").build()))
  private val callstackText = "android.os.PowerManager\$WakeLock.acquire(PowerManager.java:32)\n"

  private val profilerService = FakeProfilerService(true)
  private val energyService = FakeEnergyService()
  @get:Rule
  var grpcChannel = FakeGrpcChannel(EnergyDetailsViewTest::class.java.simpleName, profilerService, energyService)

  private lateinit var view: EnergyDetailsView

  @Before
  fun before() {
    val timer = FakeTimer()
    val services = FakeIdeProfilerServices().apply { enableEnergyProfiler(true) }
    val profilers = StudioProfilers(grpcChannel.client, services, timer)
    profilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED)
    timer.tick(TimeUnit.SECONDS.toNanos(1))

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    profilers.stage = EnergyProfilerStage(profilers)
    // Initialize the view after the stage, otherwise it will create the views for the monitoring stage.
    val studioProfilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    view = EnergyDetailsView(studioProfilersView.stageView as EnergyProfilerStageView)
  }

  @Test
  fun viewIsVisibleWhenDataIsNotNull() {
    view.isVisible = false
    view.setDuration(wakeLockDuration)
    assertThat(view.isVisible).isTrue()
  }

  @Test
  fun viewIsNotVisibleWhenDataIsNull() {
    view.isVisible = true
    view.setDuration(null)
    assertThat(view.isVisible).isFalse()
  }

  @Test
  fun wakeLockIsProperlyRendered() {
    view.setDuration(wakeLockDuration)
    val wakeLockTextPane = TreeWalker(view).descendants().filterIsInstance<JTextPane>().first()
    with(wakeLockTextPane.text) {
      assertUiContainsLabelAndValue(this, "Name", "wakeLockTag")
      assertUiContainsLabelAndValue(this, "Level", "SCREEN_DIM_WAKE_LOCK")
      assertUiContainsLabelAndValue(this, "Flags", "ACQUIRE_CAUSES_WAKEUP")
    }
    // TODO: Test time data
  }

  @Test
  fun callstackIsProperlyRendered() {
    profilerService.addFile("traceId", ByteString.copyFromUtf8(callstackText))
    view.setDuration(wakeLockDuration)
    val nonEmptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(nonEmptyView.components).isNotEmpty()
    view.setDuration(null)
    val emptyView = TreeWalker(view).descendants().filterIsInstance<EnergyCallstackView>().first()
    assertThat(emptyView.components).isEmpty()
  }

  private fun assertUiContainsLabelAndValue(uiText: String, label: String, value: String) {
    assertThat(uiText).containsMatch("\\b$label\\b.+\\b$value\\b")
  }
}
