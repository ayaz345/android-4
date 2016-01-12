/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service.atom;

import com.android.tools.idea.editors.gfxtrace.controllers.AtomController;
import com.android.tools.rpclib.binary.*;
import com.android.tools.rpclib.schema.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;

public final class AtomGroup implements BinaryObject {
  public boolean isValid() {
    return myRange.isValid();
  }

  public void addChildren(@NotNull DefaultMutableTreeNode parent, @NotNull AtomList atoms) {
    long next = myRange.getStart();
    for (AtomGroup subGroup : mySubGroups) {
      // add any atoms that come before the group
      atoms.addAtoms(parent, next, subGroup.getRange().getStart());
      // and add the group itself
      DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(
        new AtomController.Group(subGroup, atoms.get(subGroup.getRange().getLast()), subGroup.getRange().getLast()), true);
      subGroup.addChildren(subNode, atoms);
      parent.add(subNode);
      next = subGroup.getRange().getEnd();
    }
    // Add all the trailing atoms
    atoms.addAtoms(parent, next, myRange.getEnd());
  }

  //<<<Start:Java.ClassBody:1>>>
  private String myName;
  private Range myRange;
  private AtomGroup[] mySubGroups;

  // Constructs a default-initialized {@link AtomGroup}.
  public AtomGroup() {}


  public String getName() {
    return myName;
  }

  public AtomGroup setName(String v) {
    myName = v;
    return this;
  }

  public Range getRange() {
    return myRange;
  }

  public AtomGroup setRange(Range v) {
    myRange = v;
    return this;
  }

  public AtomGroup[] getSubGroups() {
    return mySubGroups;
  }

  public AtomGroup setSubGroups(AtomGroup[] v) {
    mySubGroups = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("atom", "Group", "", "");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Name", new Primitive("string", Method.String)),
      new Field("Range", new Struct(Range.Klass.INSTANCE.entity())),
      new Field("SubGroups", new Slice("GroupList", new Struct(AtomGroup.Klass.INSTANCE.entity()))),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new AtomGroup(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      AtomGroup o = (AtomGroup)obj;
      e.string(o.myName);
      e.value(o.myRange);
      e.uint32(o.mySubGroups.length);
      for (int i = 0; i < o.mySubGroups.length; i++) {
        e.value(o.mySubGroups[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      AtomGroup o = (AtomGroup)obj;
      o.myName = d.string();
      o.myRange = new Range();
      d.value(o.myRange);
      o.mySubGroups = new AtomGroup[d.uint32()];
      for (int i = 0; i <o.mySubGroups.length; i++) {
        o.mySubGroups[i] = new AtomGroup();
        d.value(o.mySubGroups[i]);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
