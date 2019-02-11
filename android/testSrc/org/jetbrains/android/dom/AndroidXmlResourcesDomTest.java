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

package org.jetbrains.android.dom;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.refactoring.MigrateToAndroidxUtil;

/**
 * Tests for code editor features when working with resources under res/xml.
 */
public class AndroidXmlResourcesDomTest extends AndroidDomTestCase {
  public AndroidXmlResourcesDomTest() {
    super("dom/xml");
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);

    // Simulate enough of the AndroidX library to write the tests, until we fix our fixtures to work against real AARs.
    runWriteCommandAction(getProject(), () -> MigrateToAndroidxUtil.setAndroidxProperties(myFixture.getProject(), "true"));
    myFixture.addClass("package androidx.preference; public class Preference {}");
    myFixture.addClass("package androidx.preference; public abstract class PreferenceGroup extends Preference {}");
    myFixture.addClass("package androidx.preference; public class PreferenceScreen extends PreferenceGroup {}");

    myFixture.addFileToProject(
      "res/values/styleables.xml",
      // language=XML
      "<resources>" +
      "<declare-styleable name='Preference'>" +
      "  <attr name='onlyAndroidx' format='string' />\n" +
      "  <attr name='key' format='string' />\n" +
      "  <attr name='android:key' />" +
      "</declare-styleable>" +
      "</resources>"
    );
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/xml/" + testFileName;
  }

  public void testPreferenceRootCompletion() throws Throwable {
    toTestCompletion("pref1.xml", "pref1_after.xml");
  }

  public void testPreferenceGroupChildrenCompletion() throws Throwable {
    toTestCompletion("pref2.xml", "pref2_after.xml");
  }

  public void testPreferenceGroupChildrenCompletion_androidx() throws Throwable {
    VirtualFile file = copyFileToProject("pref2_androidx.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertThat(lookupElementStrings).contains("androidx.preference.Preference");
    assertThat(lookupElementStrings).contains("androidx.preference.PreferenceScreen");
    assertThat(lookupElementStrings).doesNotContain("androidx.preference.PreferenceGroup");
  }

  public void testPreferenceChildrenCompletion() throws Throwable {
    doTestCompletionVariants("pref10.xml", ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void testPreferenceAttributeNamesCompletion1() throws Throwable {
    doTestCompletionVariants("pref3.xml", "summary", "summaryOn", "summaryOff");
  }

  public void testPreferenceAttributeNamesCompletion2() throws Throwable {
    toTestCompletion("pref4.xml", "pref4_after.xml");
  }

  public void testPreferenceAttributeNamesCompletion_androidX() throws Throwable {
    VirtualFile file = copyFileToProject("pref3_androidx.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertThat(lookupElementStrings).contains("app:key");
    assertThat(lookupElementStrings).contains("android:key");
    assertThat(lookupElementStrings).contains("app:onlyAndroidx");
  }

  public void testPreferenceAttributeValueCompletion() throws Throwable {
    doTestCompletionVariants("pref5.xml", "@string/welcome", "@string/welcome1");
  }

  public void testPreferenceCompletion6() throws Throwable {
    VirtualFile file = copyFileToProject("pref6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertTrue(lookupElementStrings.contains("PreferenceScreen"));
    assertFalse(lookupElementStrings.contains("android.preference.PreferenceScreen"));

    assertTrue(lookupElementStrings.contains("androidx.preference.PreferenceScreen"));
  }

  public void testPreferenceCompletion7() throws Throwable {
    toTestCompletion("pref7.xml", "pref7_after.xml");
  }

  public void testPreferenceCompletion8() throws Throwable {
    VirtualFile file = copyFileToProject("pref8.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertTrue(lookupElementStrings.contains("CheckBoxPreference"));
    assertFalse(lookupElementStrings.contains("android.preference.CheckBoxPreference"));
  }

  public void testPreferenceCompletion9() throws Throwable {
    VirtualFile file = copyFileToProject("pref9.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    assertNotNull(lookupElementStrings);
    assertTrue(lookupElementStrings.contains("preference.CheckBoxPreference"));
  }

  public void testSearchableRoot() throws Throwable {
    toTestCompletion("searchable_r.xml", "searchable_r_after.xml");
  }

  public void testSearchableAttributeName() throws Throwable {
    toTestCompletion("searchable_an.xml", "searchable_an_after.xml");
  }

  public void testSearchableAttributeValue() throws Throwable {
    doTestCompletionVariants("searchable_av.xml", "@string/welcome", "@string/welcome1");
  }

  public void testSearchableTagNameCompletion() throws Throwable {
    toTestCompletion("searchable_tn.xml", "searchable_tn_after.xml");
  }

  public void testPreferenceIntent() throws Throwable {
    doTestHighlighting("pref_intent.xml");
  }

  public void testPreferenceIntent1() throws Throwable {
    toTestFirstCompletion("pref_intent1.xml", "pref_intent1_after.xml");
  }

  public void testPreferenceIntentDoc() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("pref_intent_doc.xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("VIE");
    doTestExternalDoc("the \"standard\" action that is");
  }

  public void testPreferenceIntentDoc1() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("pref_intent_doc1.xml"));
    doTestExternalDoc("the \"standard\" action that is");
  }

  public void testPreferenceWidget() throws Throwable {
    toTestCompletion("pref_widget.xml", "pref_widget_after.xml");
  }

  public void testKeyboard() throws Throwable {
    doTestHighlighting("keyboard.xml");
  }

  public void testKeyboard1() throws Throwable {
    toTestCompletion("keyboard1.xml", "keyboard1_after.xml");
  }

  public void testDeviceAdmin() throws Throwable {
    doTestHighlighting("deviceAdmin.xml");
  }

  public void testDeviceAdmin1() throws Throwable {
    toTestCompletion("deviceAdmin1.xml", "deviceAdmin1_after.xml");
  }

  public void testDeviceAdmin2() throws Throwable {
    toTestCompletion("deviceAdmin2.xml", "deviceAdmin2_after.xml");
  }

  public void testDeviceAdmin3() throws Throwable {
    toTestCompletion("deviceAdmin3.xml", "deviceAdmin3_after.xml");
  }

  public void testPoliciesCompletion() throws Throwable {
    doTestCompletionVariantsContains("deviceAdmin4.xml", "limit-password", "watch-login", "reset-password", "force-lock", "wipe-data",
                                     "set-global-proxy", "expire-password", "encrypted-storage", "disable-camera",
                                     "disable-keyguard-features");
  }

  public void testAccountAuthenticator() throws Throwable {
    toTestCompletion("accountAuthenticator.xml", "accountAuthenticator_after.xml");
  }

  public void testAccountAuthenticator1() throws Throwable {
    toTestCompletion("accountAuthenticator1.xml", "accountAuthenticator1_after.xml");
  }

  public void testAppwidgetProviderConfigure() throws Throwable {
    copyFileToProject("MyWidgetConfigurable.java", "src/p1/p2/MyWidgetConfigurable.java");
    doTestCompletion();
  }

  public void testPreferenceHeaders() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    doTestHighlighting();
  }

  public void testCustomPreference1() throws Throwable {
    copyFileToProject("MyPreference.java", "src/p1/p2/MyPreference.java");
    toTestCompletion("customPref1.xml", "customPref1_after.xml");
  }

  public void testCustomPreference2() throws Throwable {
    copyFileToProject("MyPreference.java", "src/p1/p2/MyPreference.java");
    toTestCompletion("customPref2.xml", "customPref2_after.xml");
  }

  public void testPreferenceHeaders1() throws Throwable {
    doTestCompletion();
  }

  public void testAndroidPrefixCompletion() throws Throwable {
    doTestAndroidPrefixCompletion("android:");
  }

  public void testHtmlAsXmlResource() throws Throwable {
    doTestHighlighting();
  }

  public void testCustomXmlFileHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testContentUrlHighlighting() throws Throwable {
    // Regression test for https://code.google.com/p/android/issues/detail?id=230194
    doTestHighlighting();
  }

  public void testCustomXmlFileCompletion2() throws Throwable {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    final LookupElement[] lookupElements = myFixture.getLookupElements();
    assertNotNull(lookupElements);

    for (LookupElement element : lookupElements) {
      if ("http://www.w3.org/1999/xhtml".equals(element.getLookupString())) {
        return;
      }
    }
    fail(Arrays.asList(lookupElements).toString());
  }

  public void testJavaCompletion1() throws Throwable {
    copyFileToProject("javaCompletion.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testPathsRootCompletion() throws Throwable {
    toTestCompletion("paths1.xml", "paths1_after.xml");
  }

  public void testPathsChildrenCompletion() throws Throwable {
    toTestCompletion("paths2.xml", "paths2_after.xml");
  }
}
