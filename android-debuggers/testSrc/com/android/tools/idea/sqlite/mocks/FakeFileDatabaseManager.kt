/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.sqlite.FileDatabaseManager
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.intellij.mock.MockVirtualFile

class FakeFileDatabaseManager : FileDatabaseManager {
  val databaseFileData = DatabaseFileData(MockVirtualFile("mock virtual file"))

  val cleanedUpFiles = mutableListOf<DatabaseFileData>()

  override suspend fun loadDatabaseFileData(
    packageName: String,
    processDescriptor: ProcessDescriptor,
    databaseToDownload: SqliteDatabaseId.LiveSqliteDatabaseId): DatabaseFileData {
    return databaseFileData
  }

  override suspend fun cleanUp(databaseFileData: DatabaseFileData) {
    cleanedUpFiles.add(databaseFileData)
  }
}