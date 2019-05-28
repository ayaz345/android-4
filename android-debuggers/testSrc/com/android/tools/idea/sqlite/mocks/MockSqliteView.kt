/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.SqliteService
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.ui.ResultSetView
import com.android.tools.idea.sqlite.ui.mainView.SqliteView
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewListener
import org.mockito.Mockito.mock
import java.util.ArrayList
import javax.swing.JComponent

open class MockSqliteView : SqliteView {
  val viewListeners = ArrayList<SqliteViewListener>()

  override fun addListener(listener: SqliteViewListener) {
    viewListeners.add(listener)
  }

  override fun removeListener(listener: SqliteViewListener) {
    viewListeners.remove(listener)
  }

  override val component: JComponent = mock(JComponent::class.java)

  override val tableView: ResultSetView = mock(ResultSetView::class.java)

  override fun setUp() { }

  override fun startLoading(text: String) { }

  override fun stopLoading() { }

  override fun displaySchema(schema: SqliteSchema) { }

  override fun reportErrorRelatedToService(service: SqliteService, message: String, t: Throwable) { }

  override fun resetView() { }
}