/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("javadoc")
public class FmKeystoreSha1MethodTest extends TestCase {

  private void check(String certPath, String expected) throws TemplateModelException {
    FmKeystoreSha1Method method = new FmKeystoreSha1Method();
    List list = Collections.singletonList(new SimpleScalar(certPath));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  public void testSha1() throws Exception {
    String certPath = AndroidTestCase.getTestDataPath() + File.separator + "signingKey" + File.separator + "debug.keystore";
    check(certPath, "6B:D1:08:20:E4:95:86:82:19:3C:36:D8:C2:C9:52:CB:A8:19:1A:54");
  }
}