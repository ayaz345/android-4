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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.instantapp.InstantAppUrlFinder;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.intellij.openapi.module.Module;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Test that newly created Instant App projects do not have errors in them
 */
@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRemoteRunner.class)
public class NewInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void before() {
    SdkReplacer.replaceSdkLocationAndActivate("TestValue", true);
  }

  @After
  public void after() {
    SdkReplacer.putBack();
  }

  //Not putting this in before() as I plan to add some tests that work on non-default projects.
  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String featureModuleName,
                                              @Nullable String activityName, boolean includeUrl) {
    //TODO: There is some commonality between this code, the code in NewProjectTest and further tests I am planning, but there are also
    //      differences. Once AIA tests are completed this should be factored out into the NewProjectWizardFixture
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard
      .getConfigureAndroidProjectStep()
      .enterCompanyDomain("test.android.com")
      .enterApplicationName(projectName)
      .wizard()
      .clickNext() // Complete project configuration
      .getConfigureFormFactorStep()
      .selectMinimumSdkApi(MOBILE, "23")
      .selectInstantAppSupport(MOBILE)
      .wizard()
      .clickNext(); // Complete form factor configuration

    if (featureModuleName != null) {
      newProjectWizard
        .getConfigureInstantModuleStep()
        .enterFeatureModuleName(featureModuleName);
    }

    newProjectWizard
      .clickNext() // Complete configuration of Instant App Module
      .chooseActivity(activityName == null ? "Empty Activity" : activityName)
      .clickNext() // Complete "Add Activity" step
      .getConfigureActivityStep()
      .selectIncludeUrl(includeUrl)
      .wizard()
      .clickFinish();

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .findRunApplicationButton().waitUntilEnabledAndShowing(); // Wait for the toolbar to be ready

    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(featureModuleName == null ? "feature" : featureModuleName);
  }

  private void createAndOpenDefaultAIAProject(@NotNull String projectName, @Nullable String featureModuleName,
                                              @Nullable String activityName) {
    createAndOpenDefaultAIAProject(projectName, featureModuleName, activityName, false);
  }

  @RunIn(TestGroup.UNRELIABLE)  // b/66249968 or similar issue, apparently
  @Test
  public void testNoWarningsInDefaultNewInstantAppProjects() throws IOException {
    String projectName = "Warning";
    createAndOpenDefaultAIAProject(projectName, null, null);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    verifyOnlyExpectedWarnings(inspectionResults,
                               "    Android\n" +
                               "        Lint\n" +
                               "            Correctness\n" +
                               "                Obsolete Gradle Dependency\n" +
                               "                    build.gradle\n" +
                               "                        A newer version of com.android.support.test.espresso:espresso-core than 3.0.1 is available: 3.0.2\n" +
                               "                        A newer version of com.android.support.test:runner than 1.0.1 is available: 1.0.2\n" +
                               "                        A newer version of com.android.support.constraint:constraint-layout than 1.0.2 is available: 1.1.0\n" +
                               "                        A newer version of com.android.support:appcompat-v7 than 27.0.2 is available: 27.1.1\n" +
                               "            Security\n" +
                               "                AllowBackup/FullBackupContent Problems\n" +
                               "                    AndroidManifest.xml\n" +
                               "                        On SDK version 23 and up, your app data will be automatically backed up and restored on app install. Consider adding the attribute 'android:fullBackupContent' to specify an '@xml' resource which configures which files to backup. More info: <a href=\"https://developer.android.com/training/backup/autosyncapi.html\">https://developer.android.com/training/backup/autosyncapi.html</a>\n" +
                               "            Usability\n" +
                               "                Missing support for Firebase App Indexing\n" +
                               "                    AndroidManifest.xml\n" +
                               "                        App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW intent filter. See issue explanation for more details.\n" +
                               "    Java\n" +
                               "        Declaration redundancy\n" +
                               "            Redundant throws clause\n" +
                               "                ExampleInstrumentedTest\n" +
                               "                ExampleUnitTest\n" +
                               "                    The declared exception 'Exception' is never thrown\n" +
                               "            Unnecessary module dependency\n" +
                               "                app\n" +
                               "                    Module 'app' sources do not depend on module 'base' sources\n" +
                               "                    Module 'app' sources do not depend on module 'feature' sources\n" +
                               "                feature\n" +
                               "                    Module 'feature' sources do not depend on module 'base' sources\n" +
                               "    XML\n" +
                               "        Unused XML schema declaration\n" +
                               "            AndroidManifest.xml\n" +
                               "                Namespace declaration is never used\n" +
                               "        XML tag empty body\n" +
                               "            strings.xml\n" +
                               "                XML tag has empty body\n"
    );
  }

  @Test
  public void testCanBuildDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("BuildApp", null, null);

    guiTest.ideFrame().getEditor()
      .open("base/build.gradle") // Check "base" dependencies
      .moveBetween("application project(':app')", "")
      .moveBetween("feature project(':feature')", "")
      .open("feature/build.gradle") // Check "feature" dependencies
      .moveBetween("implementation project(':base')", "")
      .open("app/build.gradle") // Check "app" dependencies
      .moveBetween("implementation project(':feature')", "")
      .moveBetween("implementation project(':base')", "")
      .open("base/src/main/AndroidManifest.xml")
      .moveBetween("android:name=\"aia-compat-api-min-version\"", "")
      .moveBetween("android:value=\"1\"", "");

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithEmptyActivityWithoutUrls() throws IOException {
    createAndOpenDefaultAIAProject("BuildApp", null, null, false);
    String manifestContent = guiTest.ideFrame().getEditor()
      .open("feature/src/main/res/layout/activity_main.xml")
      .open("feature/src/main/AndroidManifest.xml")
      .getCurrentFileContents();

    assertThat(manifestContent).contains("android.intent.action.MAIN");
    assertThat(manifestContent).contains("android.intent.category.LAUNCHER");
    assertThat(manifestContent).doesNotContain("android:host=");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildNewInstantAppProjectsWithLoginActivity() throws IOException {
    createAndOpenDefaultAIAProject("BuildApp", null, "Login Activity", true);
    guiTest.ideFrame().getEditor()
      .open("feature/src/main/res/layout/activity_login.xml")
      .open("feature/src/main/AndroidManifest.xml")
      .moveBetween("android:order=", "")
      .moveBetween("android:host=", "")
      .moveBetween("android:pathPattern=", "")
      .moveBetween("android:scheme=\"https", "")
      .moveBetween("android.intent.action.", "MAIN")
      .moveBetween("android.intent.category.", "LAUNCHER");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void newInstantAppProjectWithFullScreenActivity() throws Exception {
    createAndOpenDefaultAIAProject("BuildApp", null, "Fullscreen Activity");
    guiTest.ideFrame().getEditor()
      .open("feature/src/main/res/layout/activity_fullscreen.xml")
      .open("base/src/main/res/values/attrs.xml") // Make sure "Full Screen" themes, colors and styles are on the base module
      .moveBetween("ButtonBarContainerTheme", "")
      .open("base/src/main/res/values/colors.xml")
      .moveBetween("black_overlay", "")
      .open("base/src/main/res/values/styles.xml")
      .moveBetween("FullscreenTheme", "")
      .moveBetween("FullscreenActionBarStyle", "");
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test // b/68122671
  public void addMapActivityToExistingIappModule() throws Exception {
    createAndOpenDefaultAIAProject("BuildApp", "feature", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Google", "Google Maps Activity")
      .clickFinish();

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish();

    assertAbout(file()).that(guiTest.getProjectPath("base/src/debug/res/values/google_maps_api.xml")).isFile();
    assertAbout(file()).that(guiTest.getProjectPath("base/src/release/res/values/google_maps_api.xml")).isFile();
  }

  @Test // b/68478730
  public void addMasterDetailActivityToExistingIappModule() throws Exception {
    createAndOpenDefaultAIAProject("BuildApp", "feature", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Master/Detail Flow")
      .clickFinish();

    String baseStrings = guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("base/src/main/res/values/strings.xml")
      .getCurrentFileContents();

    assertThat(baseStrings).contains("title_item_detail");
    assertThat(baseStrings).contains("title_item_list");
  }

  @Test // b/68684401
  public void addFullscreenActivityToExistingIappModule() throws Exception {
    createAndOpenDefaultAIAProject("BuildApp", "feature", null);
    guiTest.ideFrame()
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Fullscreen Activity")
      .clickFinish();

    String baseStrings = guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("base/src/main/res/values/strings.xml")
      .getCurrentFileContents();

    assertThat(baseStrings).contains("title_activity_fullscreen");
  }

  @Test
  public void testValidPathInDefaultNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("RouteApp", "routefeature", null, true);

    Module module = guiTest.ideFrame().getModule("routefeature");
    assertThat(new InstantAppUrlFinder(module).getAllUrls()).isNotEmpty();
  }

  @Test
  public void testCanCustomizeFeatureModuleInNewInstantAppProjects() throws IOException {
    createAndOpenDefaultAIAProject("SetFeatureNameApp", "testfeaturename", null);

    guiTest.ideFrame().getModule("testfeaturename");
  }

  // With warnings coming from multiple projects the order of warnings is not deterministic, also there are some warnings that show up only
  // on local machines. This method allows us to check that the warnings in the actual result are a sub-set of the expected warnings.
  // This is not a perfect solution, but this state where we have multiple warnings on a new project should only be temporary
  private static void verifyOnlyExpectedWarnings(@NotNull String actualResults, @NotNull String acceptedWarnings) {
    ArrayList<String> lines = new ArrayList<>(Arrays.asList(actualResults.split("\n")));

    // Ignore the first line of the error report
    for (String line : lines.subList(1, lines.size())) {
      assertThat(acceptedWarnings.split("\n")).asList().contains(line);
    }
  }
}
