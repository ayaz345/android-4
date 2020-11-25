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
package com.android.tools.idea.updater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.repository.Revision;
import com.android.repository.api.Channel;
import com.android.repository.api.Downloader;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
import com.android.repository.api.SimpleRepositorySource;
import com.android.repository.impl.manager.RepoManagerImpl;
import com.android.repository.testframework.FakeDownloader;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepositorySourceProvider;
import com.android.repository.testframework.FakeSettingsController;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioProgressIndicatorAdapter;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.externalComponents.ExternalComponentManager;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.mock.MockApplication;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.ExternalUpdate;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.ExtensionTestUtil;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for {@link SdkComponentSource}
 */
public class SdkComponentSourceTest {

  private static final Comparator<? super UpdatableExternalComponent> COMPONENT_COMPARATOR = Comparator.comparing(o -> o.getName());
  private SdkComponentSource myTestComponentSource;
  private int myChannelId;
  private MockFileOp myFileOp;

  @Rule
  public DisposableRule myDisposableRule = new DisposableRule();

  @Before
  public void setUp() throws Exception {
    MockApplication instance = MockApplication.setUp(myDisposableRule.getDisposable());
    instance.registerService(ExternalComponentManager.class);
    instance.registerService(UpdateSettings.class, UpdateSettings.class);
    ApplicationInfoEx appInfo = mock(ApplicationInfoEx.class);
    when(appInfo.getBuild()).thenReturn(new BuildNumber("a", 1));
    instance.registerService(ApplicationInfo.class, appInfo);
    instance.getExtensionArea().registerExtensionPoint(ExternalComponentSource.EP_NAME, ExternalComponentSource.class.getName(),
                                                       ExtensionPoint.Kind.INTERFACE, myDisposableRule.getDisposable());

    ApplicationManager.setApplication(instance, myDisposableRule.getDisposable());

    try (MockedStatic<PermanentInstallationID> theMock = Mockito.mockStatic(PermanentInstallationID.class)) {
      theMock.when(PermanentInstallationID::get).thenReturn("foo");
      //noinspection ResultOfMethodCallIgnored
      UpdateChecker.INSTANCE.toString();
    }

    myFileOp = new MockFileOp();
    myFileOp.recordExistingFile("/sdk/noRemote/package.xml", getLocalRepoXml("noRemote", new Revision(1)));
    myFileOp.recordExistingFile("/sdk/newerRemote/package.xml", getLocalRepoXml("newerRemote", new Revision(1)));
    myFileOp.recordExistingFile("/sdk/sameRemote/package.xml", getLocalRepoXml("sameRemote", new Revision(1)));
    myFileOp.recordExistingFile("/sdk/olderRemote/package.xml", getLocalRepoXml("olderRemote", new Revision(1, 2)));
    myFileOp.recordExistingFile("/sdk/hasPreview/package.xml", getLocalRepoXml("hasPreview", new Revision(1)));
    myFileOp.recordExistingFile("/sdk/newerPreview/package.xml", getLocalRepoXml("newerPreview", new Revision(1, 0, 0, 1)));
    myFileOp.recordExistingFile("/sdk/samePreview/package.xml", getLocalRepoXml("samePreview", new Revision(1, 0, 0, 1)));
    myFileOp.recordExistingFile("/sdk/olderPreview/package.xml", getLocalRepoXml("olderPreview", new Revision(2)));
    myFileOp.recordExistingFile("/sdk/zNewerInBeta/package.xml", getLocalRepoXml("zNewerInBeta", new Revision(1)));

    final FakeDownloader downloader = new FakeDownloader(myFileOp);

    List<String> remotePaths = Lists.newArrayList();
    List<Revision> remoteRevisions = Lists.newArrayList();
    List<Integer> remoteChannels = Lists.newArrayList();

    remotePaths.add("newerRemote");
    remoteRevisions.add(new Revision(1, 1));
    remoteChannels.add(0);

    remotePaths.add("sameRemote");
    remoteRevisions.add(new Revision(1));
    remoteChannels.add(0);

    remotePaths.add("olderRemote");
    remoteRevisions.add(new Revision(1, 1));
    remoteChannels.add(0);

    remotePaths.add("hasPreview");
    remoteRevisions.add(new Revision(1, 0, 0, 1));
    remoteChannels.add(0);

    remotePaths.add("newerPreview");
    remoteRevisions.add(new Revision(1, 0, 0, 2));
    remoteChannels.add(0);

    remotePaths.add("samePreview");
    remoteRevisions.add(new Revision(1, 0, 0, 1));
    remoteChannels.add(0);

    remotePaths.add("olderPreview");
    remoteRevisions.add(new Revision(1, 0, 0, 1));
    remoteChannels.add(0);

    remotePaths.add("onlyRemote");
    remoteRevisions.add(new Revision(1));
    remoteChannels.add(0);

    remotePaths.add("zNewerInBeta");
    remoteRevisions.add(new Revision(2));
    remoteChannels.add(1);

    String url = "http://example.com/repo";
    downloader.registerUrl(new URL(url), getRepoXml(remotePaths, remoteRevisions, remoteChannels, true).getBytes(Charsets.UTF_8));

    final RepoManager mgr = new RepoManagerImpl();
    mgr.setLocalPath(myFileOp.toPath("/sdk"));
    mgr.registerSchemaModule(AndroidSdkHandler.getRepositoryModule());
    mgr.registerSchemaModule(AndroidSdkHandler.getCommonModule());
    mgr.registerSourceProvider(new FakeRepositorySourceProvider(
      ImmutableList.of(new SimpleRepositorySource(url, "dummy", true, mgr.getSchemaModules(), null))));

    myChannelId = 0;

    myTestComponentSource = new SdkComponentSource() {
      @Nullable
      @Override
      public List<String> getAllChannels() {
        return null;
      }

      @Override
      @NotNull
      RepoManager getRepoManager() {
        return mgr;
      }

      @NotNull
      @Override
      SettingsController getSettingsController() {
        return new FakeSettingsController(false) {
          @Override
          public Channel getChannel() {
            return Channel.create(myChannelId);
          }
        };
      }

      @NotNull
      @Override
      Downloader getDownloader() {
        return downloader;
      }
    };
  }

  @Test
  public void testAvailableStableVersions() {
    ProgressIndicator progress = new StudioProgressIndicatorAdapter(new FakeProgressIndicator(), null);
    Set<UpdatableExternalComponent> components = Sets.newTreeSet(COMPONENT_COMPARATOR);
    components.addAll(myTestComponentSource.getAvailableVersions(progress, null));
    Iterator<UpdatableExternalComponent> componentIter = components.iterator();

    validateStablePackages(componentIter);

    assertFalse(componentIter.hasNext());
  }

  @Test
  public void testAvailableBetaVersions() {
    myChannelId = 1;
    ProgressIndicator progress = new StudioProgressIndicatorAdapter(new FakeProgressIndicator(true), null);
    Set<UpdatableExternalComponent> components = Sets.newTreeSet(COMPONENT_COMPARATOR);
    components.addAll(myTestComponentSource.getAvailableVersions(progress, null));
    Iterator<UpdatableExternalComponent> componentIter = components.iterator();

    validateStablePackages(componentIter);

    UpdatableExternalComponent c = componentIter.next();
    assertEquals("package zNewerInBeta", c.getName());
    assertEquals(new Revision(2, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    assertFalse(componentIter.hasNext());
  }

  private static void validateStablePackages(Iterator<UpdatableExternalComponent> componentIter) {
    UpdatableExternalComponent c = componentIter.next();
    assertEquals("package hasPreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 1), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package newerPreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 2), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package newerRemote", c.getName());
    assertEquals(new Revision(1, 1, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package olderPreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 1), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package olderRemote", c.getName());
    assertEquals(new Revision(1, 1, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package onlyRemote", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package samePreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 1), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package sameRemote", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());
  }

  @Test
  public void testCurrentVersions() {
    Set<UpdatableExternalComponent> components = Sets.newTreeSet(COMPONENT_COMPARATOR);
    components.addAll(myTestComponentSource.getCurrentVersions());
    Iterator<UpdatableExternalComponent> componentIter = components.iterator();

    UpdatableExternalComponent c = componentIter.next();
    assertEquals("package hasPreview", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package newerPreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 1), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package newerRemote", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package noRemote", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package olderPreview", c.getName());
    assertEquals(new Revision(2, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package olderRemote", c.getName());
    assertEquals(new Revision(1, 2, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package samePreview", c.getName());
    assertEquals(new Revision(1, 0, 0, 1), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package sameRemote", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    c = componentIter.next();
    assertEquals("package zNewerInBeta", c.getName());
    assertEquals(new Revision(1, 0, 0), ((RepoPackage)c.getKey()).getVersion());

    assertFalse(componentIter.hasNext());
  }

  @Test
  public void testStatuses() {
    myFileOp.recordExistingFile("/sdk/platforms/android-23/package.xml",
                                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                                "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\" " +
                                "                xmlns:ns5=\"http://schemas.android.com/repository/android/generic/01\" " +
                                "                xmlns:ns6=\"http://schemas.android.com/sdk/android/repo/repository2/01\"" +
                                "                xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                                "    <localPackage path=\"platforms;android-23\">" +
                                "        <type-details xsi:type=\"ns6:platformDetailsType\">" +
                                "            <api-level>23</api-level>" +
                                "            <layoutlib xsi:type=\"ns6:layoutlibType\" api=\"15\"/>" +
                                "        </type-details>" +
                                "        <revision><major>2</major></revision>" +
                                "        <display-name>Android SDK Platform 23, rev 2</display-name>" +
                                "    </localPackage>" +
                                "</ns2:repository>");
    myFileOp.recordExistingFile("/sdk/platforms/android-20/package.xml",
                                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                                "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\" " +
                                "                xmlns:ns5=\"http://schemas.android.com/repository/android/generic/01\" " +
                                "                xmlns:ns6=\"http://schemas.android.com/sdk/android/repo/repository2/01\"" +
                                "                xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                                "    <localPackage path=\"platforms;android-20\">" +
                                "        <type-details xsi:type=\"ns6:platformDetailsType\">" +
                                "            <api-level>20</api-level>" +
                                "            <layoutlib xsi:type=\"ns6:layoutlibType\" api=\"15\"/>" +
                                "        </type-details>" +
                                "        <revision><major>2</major></revision>" +
                                "        <display-name>Android SDK Platform 20, rev 2</display-name>" +
                                "    </localPackage>" +
                                "</ns2:repository>");
    myTestComponentSource.getRepoManager().loadSynchronously(-1, new FakeProgressIndicator(), null, null);
    Collection<? extends Pair<String, String>> statuses = myTestComponentSource.getStatuses();
    assertTrue(statuses.contains(Pair.create("Android Platform Version:", "API 23: Android 6.0 (Marshmallow) revision 2")));
  }

  @Test
  public void testIgnored() {
    final AtomicReference<String> id = new AtomicReference<>();
    ProgressIndicator progress = new StudioProgressIndicatorAdapter(new FakeProgressIndicator(), null);
    for (UpdatableExternalComponent c : myTestComponentSource.getAvailableVersions(progress, null)) {
      if ("package newerRemote".equals(c.getName())) {
        id.set(SdkComponentSource.getPackageRevisionId((RepoPackage)c.getKey()));
      }
    }
    assertNotNull(id.get());

    ExtensionTestUtil
      .maskExtensions(ExternalComponentSource.EP_NAME, Collections.singletonList(myTestComponentSource), myDisposableRule.getDisposable());
    UpdateSettings settings = new UpdateSettings() {
      @Override
      @NotNull
      public List<String> getIgnoredBuildNumbers() {
        return ImmutableList.of(id.get());
      }
    };

    Collection<ExternalUpdate> updates = UpdateChecker.INSTANCE.checkExternalUpdates(true, settings, progress);
    assertEquals(1, updates.size());
    ExternalUpdate update = updates.iterator().next();
    Iterator<UpdatableExternalComponent> iter = update.getComponents().iterator();
    UpdatableExternalComponent component = iter.next();
    assertEquals("package newerPreview", component.getName());
    assertEquals(new Revision(1, 0, 0, 2), ((RepoPackage)component.getKey()).getVersion());

    assertFalse(iter.hasNext());
  }

  @Test
  public void testUpdates() {
    ExtensionTestUtil
      .maskExtensions(ExternalComponentSource.EP_NAME, Collections.singletonList(myTestComponentSource), myDisposableRule.getDisposable());

    ProgressIndicator progress = new StudioProgressIndicatorAdapter(new FakeProgressIndicator(), null);
    UpdateSettings settings = new UpdateSettings();

    Collection<ExternalUpdate> updates = UpdateChecker.INSTANCE.checkExternalUpdates(true, settings, progress);
    assertEquals(1, updates.size());
    ExternalUpdate update = updates.iterator().next();
    Iterator<UpdatableExternalComponent> iter = update.getComponents().iterator();
    UpdatableExternalComponent component = iter.next();
    assertEquals("package newerPreview", component.getName());
    assertEquals(new Revision(1, 0, 0, 2), ((RepoPackage)component.getKey()).getVersion());

    component = iter.next();
    assertEquals("package newerRemote", component.getName());
    assertEquals(new Revision(1, 1, 0), ((RepoPackage)component.getKey()).getVersion());

    assertFalse(iter.hasNext());
  }

  @Test
  public void testBetaUpdates() {
    myChannelId = 1;
    ExtensionTestUtil
      .maskExtensions(ExternalComponentSource.EP_NAME, Collections.singletonList(myTestComponentSource), myDisposableRule.getDisposable());

    ProgressIndicator progress = new StudioProgressIndicatorAdapter(new FakeProgressIndicator(), null);
    UpdateSettings settings = new UpdateSettings();

    Collection<ExternalUpdate> updates = UpdateChecker.INSTANCE.checkExternalUpdates(true, settings, progress);
    assertEquals(1, updates.size());
    ExternalUpdate update = updates.iterator().next();
    Iterator<UpdatableExternalComponent> iter = update.getComponents().iterator();
    UpdatableExternalComponent component = iter.next();
    assertEquals("package newerPreview", component.getName());
    assertEquals(new Revision(1, 0, 0, 2), ((RepoPackage)component.getKey()).getVersion());

    component = iter.next();
    assertEquals("package newerRemote", component.getName());
    assertEquals(new Revision(1, 1, 0), ((RepoPackage)component.getKey()).getVersion());

    component = iter.next();
    assertEquals("package zNewerInBeta", component.getName());
    assertEquals(new Revision(2, 0, 0), ((RepoPackage)component.getKey()).getVersion());

    assertFalse(iter.hasNext());
  }

  private static String getLocalRepoXml(String path, Revision revision) {
    return getRepoXml(ImmutableList.of(path), ImmutableList.of(revision), ImmutableList.of(), false);
  }

  private static String getRepoXml(List<String> paths, List<Revision> revisions, List<Integer> channels, boolean remote) {
    StringBuilder result = new StringBuilder(
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
    "<ns4:repository xmlns:ns4=\"http://schemas.android.com/repository/android/generic/01\"" +
    "                xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
    if (remote) {
      result.append("<channel id=\"channel-0\">stable</channel>");
      result.append("<channel id=\"channel-1\">beta</channel>");
    }
    Iterator<Revision> revisionIter = revisions.iterator();
    Iterator<Integer> channelIter = channels.iterator();
    for (String path : paths) {
      result.append(getPackageXml(path, revisionIter.next(), remote, remote ? channelIter.next() : 0));
    }
    result.append("</ns4:repository>");
    return result.toString();
  }

  private static String getPackageXml(String path, Revision revision, boolean remote, int channel) {
    return String.format(
      "    <%1$sPackage path=\"%2$s\">" +
      "        <type-details xsi:type=\"ns4:genericDetailsType\"/>" +
      "        <revision>" +
      "            <major>%3$d</major>" +
      "            <minor>%4$d</minor>" +
      "            <micro>%5$d</micro>" +
      (revision.isPreview() ? "            <preview>%6$d</preview>" : "") +
      "        </revision>" +
      "        <display-name>package %2$s</display-name>" +
      (remote ? "<channelRef ref=\"channel-%7$d\"/>" +
                "<archives><archive><complete><size>1234</size>" +
                "<checksum>3e60f223fec640cd47de34da018f41566ab5d6cb</checksum>" +
                "<url>foo</url></complete></archive></archives>" : "") +
      "    </%1$sPackage>",
      remote ? "remote" : "local", path, revision.getMajor(), revision.getMinor(), revision.getMicro(), revision.getPreview(), channel);
  }
}
