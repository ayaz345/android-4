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
package org.jetbrains.android.dom;

import com.intellij.util.xml.Required;

import java.lang.annotation.Annotation;

/**
 * Implementation of @Required annotation interface to be used for marking DOM attributes as required.
 */
@SuppressWarnings("BadAnnotationImplementation")
public class RequiredImpl implements Required {
  @Override
  public boolean value() {
    return true;
  }

  @Override
  public boolean nonEmpty() {
    return true;
  }

  @Override
  public boolean identifier() {
    return false;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return Required.class;
  }
}
