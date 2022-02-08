/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.ClientImpl
import com.android.testutils.MockitoKt
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.engine.DebugProcess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertTrue

class AndroidRemoteDebugProcessHandlerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  lateinit var debugProcess: DebugProcess
  lateinit var client: Client
  lateinit var device: IDevice

  @Before
  fun setUpDebugSession() {
    client = Mockito.mock(ClientImpl::class.java)
    device = Mockito.mock(IDevice::class.java)
    Mockito.`when`(client.device).thenReturn(device)
    val clientData = object : ClientData(client as ClientImpl, 111) {
      override fun getPackageName() = "MyApp"
      override fun getClientDescription() = "MyApp"
    }
    Mockito.`when`(client.clientData).thenReturn(clientData)

    debugProcess = Mockito.mock(DebugProcess::class.java)
    val debugManager = projectRule.mockProjectService(DebuggerManager::class.java)
    Mockito.`when`(debugManager.getDebugProcess(MockitoKt.any(AndroidRemoteDebugProcessHandler::class.java))).thenReturn(debugProcess)
  }

  @Test
  fun restoreConnectionOnDetach() {
    val client = Mockito.mock(Client::class.java)
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.detachProcess()
    Mockito.verify(client).notifyVmMirrorExited()
  }

  @Test
  fun `don'tRestoreConnectionOnDestroy`() {
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.destroyProcess()
    Mockito.verify(client, Mockito.never()).notifyVmMirrorExited()
  }

  @Test
  fun callFinishAndroidProcess() {
    var androidProcessFinished = false
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false) { androidProcessFinished = true }
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
    Thread.sleep(100)
    assertTrue(androidProcessFinished)
  }

  @Test
  fun `don'tTerminateTargetVMOnDestroy`() {
    val client = Mockito.mock(Client::class.java)
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
    Mockito.verify(debugProcess).stop(false)
  }

  @Test
  fun forceStopOnDestroy() {
    val processHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    processHandler.startNotify()
    processHandler.destroyProcess()
    processHandler.waitFor()
    Thread.sleep(100)
    Mockito.verify(device).forceStop("MyApp")
  }
}