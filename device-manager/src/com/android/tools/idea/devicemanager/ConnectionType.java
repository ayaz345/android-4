/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

public enum ConnectionType {
  UNKNOWN,
  USB,
  WI_FI;

  public static final @NotNull Object UNKNOWN_SET = Sets.immutableEnumSet(UNKNOWN);
  public static final @NotNull Object USB_SET = Sets.immutableEnumSet(USB);
  public static final @NotNull Object WI_FI_SET = Sets.immutableEnumSet(WI_FI);
  public static final @NotNull Object USB_AND_WI_FI_SET = Sets.immutableEnumSet(USB, WI_FI);
}
