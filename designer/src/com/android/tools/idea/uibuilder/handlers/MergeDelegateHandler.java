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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class MergeDelegateHandler extends DelegatingViewGroupHandler {

  public MergeDelegateHandler(@NotNull ViewGroupHandler handler) {
    super(handler);
  }

  @NotNull
  @Override
  public String getTitle(@NotNull String tagName) {
    return "<merge>";
  }

  @NotNull
  @Override
  public String getTitle(@NotNull NlComponent component) {
    return "<merge>";
  }

  @NotNull
  @Override
  public List<String> getInspectorProperties() {
    return ImmutableList.<String>builder()
      .add(TOOLS_NS_NAME_PREFIX + ATTR_SHOW_IN)
      .add(TOOLS_NS_NAME_PREFIX + ATTR_PARENT_TAG)
      .addAll(getDelegateHandler().getInspectorProperties())
      .build();
  }
}
