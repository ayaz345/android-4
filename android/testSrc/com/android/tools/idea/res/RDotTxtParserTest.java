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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.resources.ResourceUrl;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RDotTxtParserTest {
  @Test
  public void testGetDeclareStyleableArray() throws IOException {
    String content = "int anim abc_fade_in 0x7f040000\n" +
                     "int anim abc_fade_out 0x7f040001\n" +
                     "\n" +
                     "int anim abc_grow_fade_in_from_bottom 0x7f040002\n" +
                     "int anim abc_popup_enter 0x7f040003\n" +
                     "int anim abc_popup_exit 0x7f040004\n" +
                     "int anim abc_shrink_fade_out_from_bottom 0x7f040005\n" +
                     "int[] styleable ActionBar { 0x7f010001, 0x7f010003 }\n" +
                     "int styleable ActionBar_title 0\n" +
                     "int styleable ActionBar_titleTextStyle 1\n" +
                     "int drawable abc_list_selector_holo_dark 0x7f020031\n" +
                     "int drawable abc_list_selector_holo_light 0x7f020032\n" +
                     "int drawable abc_menu_hardkey_panel_mtrl_mult 0x7f02003";

    File rFile = FileUtil.createTempFile("R", ".txt");
    FileUtil.writeToFile(rFile, content);

    List<AttrResourceValue> attributes = ImmutableList.of(
      new AttrResourceValue(ResourceUrl.parse("@styleable/title"), null),
      new AttrResourceValue(ResourceUrl.parse("@styleable/titleTextStyle"), null));
    Integer[] r = RDotTxtParser.getDeclareStyleableArray(rFile, attributes, "ActionBar");
    assertEquals(2130771969, r[0].intValue());
    assertEquals(2130771971, r[1].intValue());

    // Test that:
    //  - We can still parse the content correctly even having interleaved elements
    //  - The order of the attribute indexes does not break the parsing (having the pointer to the second
    //    element first)
    content = "int anim abc_popup_enter 0x7f040003\n" +
              "int[] styleable ActionBar { 0x7f010001, 0x7f010003 }\n" +
              "int anim abc_popup_exit 0x7f040004\n" +
              "int anim abc_shrink_fade_out_from_bottom 0x7f040005\n" +
              "int drawable abc_list_selector_holo_light 0x7f020032\n" +
              "int styleable ActionBar_titleTextStyle 1\n" +
              "int drawable abc_list_selector_holo_dark 0x7f020031\n" +
              "int styleable ActionBar_title 0\n" +
              "int drawable abc_menu_hardkey_panel_mtrl_mult 0x7f02003";
    rFile = FileUtil.createTempFile("R", ".txt");
    FileUtil.writeToFile(rFile, content);
    r = RDotTxtParser.getDeclareStyleableArray(rFile, attributes, "ActionBar");
    assertEquals(2130771969, r[0].intValue());
    assertEquals(2130771971, r[1].intValue());
  }
}