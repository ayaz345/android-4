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
package com.android.tools.idea.uibuilder.palette2;

import com.android.tools.idea.common.model.NlLayoutType;
import org.jetbrains.android.AndroidTestCase;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class DataModelTest extends AndroidTestCase {
  private DataModel myDataModel;
  private CategoryListModel myCategoryListModel;
  private ItemListModel myItemListModel;
  private DependencyManager myDependencyManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
    myDataModel = new DataModel(myDependencyManager);
    myCategoryListModel = myDataModel.getCategoryListModel();
    myItemListModel = myDataModel.getItemListModel();
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myDataModel = null;
    myCategoryListModel = null;
    myItemListModel = null;
    myDependencyManager = null;
  }

  public void testEmptyModelHoldsUsableListModels() {
    assertThat(myCategoryListModel.getSize()).isEqualTo(0);
    assertThat(myItemListModel.getSize()).isEqualTo(0);
  }

  public void testCommonLayoutGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.COMMON);
    assertThat(myItemListModel.getSize()).isEqualTo(0);

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(myItemListModel.getSize()).isEqualTo(30);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("TextView");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("Plain Text");
    assertThat(myItemListModel.getElementAt(2).getTitle()).isEqualTo("TextInputLayout");
  }

  public void testTextLayoutGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(myItemListModel.getSize()).isEqualTo(8);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("Button");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("ImageButton");
    assertThat(myItemListModel.getElementAt(2).getTitle()).isEqualTo("CheckBox");
  }

  public void testSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("ima");

    assertThat(myCategoryListModel.getSize()).isEqualTo(4);
    assertThat(myCategoryListModel.hasMatchCounts()).isTrue();
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.RESULTS);
    assertThat(myCategoryListModel.getMatchCountAt(0)).isEqualTo(3);
    assertThat(myCategoryListModel.getElementAt(1).getName()).isEqualTo("Text");
    assertThat(myCategoryListModel.getMatchCountAt(1)).isEqualTo(1);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.getMatchCountAt(2)).isEqualTo(1);
    assertThat(myCategoryListModel.getElementAt(3).getName()).isEqualTo("Widgets");
    assertThat(myCategoryListModel.getMatchCountAt(3)).isEqualTo(1);

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(myItemListModel.getSize()).isEqualTo(3);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("Number (Decimal)");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("ImageButton");
    assertThat(myItemListModel.getElementAt(2).getTitle()).isEqualTo("ImageView");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("Number (Decimal)");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(myItemListModel.getSize()).isEqualTo(1);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("ImageButton");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(myItemListModel.getSize()).isEqualTo(1);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("ImageView");
  }

  public void testMetaSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("material");

    assertThat(myCategoryListModel.getSize()).isEqualTo(4);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.RESULTS);
    assertThat(myCategoryListModel.getMatchCountAt(0)).isEqualTo(4);
    assertThat(myCategoryListModel.getElementAt(1).getName()).isEqualTo("Text");
    assertThat(myCategoryListModel.getMatchCountAt(1)).isEqualTo(1);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.getMatchCountAt(2)).isEqualTo(1);
    assertThat(myCategoryListModel.getElementAt(3).getName()).isEqualTo("Containers");
    assertThat(myCategoryListModel.getMatchCountAt(3)).isEqualTo(2);

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(myItemListModel.getSize()).isEqualTo(4);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("TextInputLayout");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("FloatingActionButton");
    assertThat(myItemListModel.getElementAt(2).getTitle()).isEqualTo("TabLayout");
    assertThat(myItemListModel.getElementAt(3).getTitle()).isEqualTo("TabItem");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getSize()).isEqualTo(1);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("TextInputLayout");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(myItemListModel.getSize()).isEqualTo(1);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("FloatingActionButton");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(myItemListModel.getSize()).isEqualTo(2);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("TabLayout");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("TabItem");
  }

  public void testMenuType() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.MENU);

    assertThat(myCategoryListModel.getSize()).isEqualTo(1);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(myItemListModel.getSize()).isEqualTo(6);
    assertThat(myItemListModel.getElementAt(0).getTitle()).isEqualTo("Cast Button");
    assertThat(myItemListModel.getElementAt(1).getTitle()).isEqualTo("Menu Item");
    assertThat(myItemListModel.getElementAt(2).getTitle()).isEqualTo("Search Item");
    assertThat(myItemListModel.getElementAt(3).getTitle()).isEqualTo("Switch Item");
    assertThat(myItemListModel.getElementAt(4).getTitle()).isEqualTo("Menu");
    assertThat(myItemListModel.getElementAt(5).getTitle()).isEqualTo("Group");
  }
}
