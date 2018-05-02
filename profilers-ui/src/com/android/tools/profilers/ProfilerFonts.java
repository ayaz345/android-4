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
package com.android.tools.profilers;

import com.android.tools.adtui.common.AdtUiUtils;

import java.awt.*;

/**
 * Common fonts shared across the profiler views.
 */
public class ProfilerFonts {
  public static final Font H1_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(11f); // 21 scaled
  public static final Font H2_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(5f);  // 15 scaled
  public static final Font H3_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(4f);  // 14 scaled
  public static final Font H4_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(3f);  // 13 scaled
  public static final Font STANDARD_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(2); // 12 scaled
  public static final Font TOOLTIP_FONT = AdtUiUtils.DEFAULT_FONT.biggerOn(1);  // 11 scaled
  public static final Font SMALL_FONT = AdtUiUtils.DEFAULT_FONT;  // 10 scaled
}
