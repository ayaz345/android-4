/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.run;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;

public class LoggingReceiver extends AndroidOutputReceiver {
  private final Logger myLogger;

  public LoggingReceiver(Logger logger) {
    myLogger = logger;
  }

  @Override
  protected void processNewLines(@NotNull List<String> newLines) {
    for (String line : newLines) {
      myLogger.info(line);
    }
  }

  @Override
  public synchronized boolean isCancelled() {
    return false;
  }
}
