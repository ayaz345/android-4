/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription;

import java.io.File;

/**
 * Tests for {@link NavigationDomFileDescription}
 */
public class NavigationDomFileDescriptionTest extends NavigationTestCase {
  private static final String resourceFolder = "/app/src/main/res/";

  public void testDescription() throws Exception {
    assertFalse(isNavFile("layout/activity_main.xml"));
    assertTrue(isNavFile("navigation/navigation.xml"));
  }

  private boolean isNavFile(String fileName) {
    VirtualFile baseDir = getProject().getBaseDir();
    String baseDirPath = baseDir.getCanonicalPath();
    File file = new File(baseDirPath + resourceFolder, fileName);
    assertNotNull(file);

    VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
    assertNotNull(virtualFile);

    PsiManager manager = PsiManager.getInstance(getProject());
    PsiFile psiFile = manager.findFile(virtualFile);
    assertNotNull(psiFile);

    XmlFile xmlFile = (XmlFile)psiFile;
    assertNotNull(xmlFile);

    return NavigationDomFileDescription.isNavFile(xmlFile);
  }
}
