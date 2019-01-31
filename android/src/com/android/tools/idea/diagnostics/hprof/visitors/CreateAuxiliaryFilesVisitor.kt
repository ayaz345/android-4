/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.hprof.visitors

import com.android.tools.idea.diagnostics.hprof.classstore.ClassDefinition
import com.android.tools.idea.diagnostics.hprof.classstore.ClassStore
import com.android.tools.idea.diagnostics.hprof.parser.ConstantPoolEntry
import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.android.tools.idea.diagnostics.hprof.parser.HProfVisitor
import com.android.tools.idea.diagnostics.hprof.parser.HeapDumpRecordType
import com.android.tools.idea.diagnostics.hprof.parser.InstanceFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.StaticFieldEntry
import com.android.tools.idea.diagnostics.hprof.parser.Type
import com.android.tools.idea.diagnostics.hprof.util.FileChannelBackedWriteBuffer
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class CreateAuxiliaryFilesVisitor(
  private val auxOffsetsChannel: FileChannel,
  private val auxChannel: FileChannel,
  private val classStore: ClassStore,
  private val parser: HProfEventBasedParser
) : HProfVisitor() {
  private lateinit var offsets: FileChannelBackedWriteBuffer
  private lateinit var aux: FileChannelBackedWriteBuffer

  override fun preVisit() {
    disableAll()
    enable(HeapDumpRecordType.ClassDump)
    enable(HeapDumpRecordType.InstanceDump)
    enable(HeapDumpRecordType.ObjectArrayDump)
    enable(HeapDumpRecordType.PrimitiveArrayDump)

    offsets = FileChannelBackedWriteBuffer(auxOffsetsChannel)
    aux = FileChannelBackedWriteBuffer(auxChannel)

    // Map id=0 to 0
    offsets.writeInt(0)
  }

  override fun postVisit() {
    aux.close()
    offsets.close()
  }

  override fun visitPrimitiveArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, numberOfElements: Long, elementType: Type) {
    assert(arrayObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == arrayObjectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(classStore.getClassForPrimitiveArray(elementType)!!.id.toInt())
  }

  override fun visitClassDump(classId: Long,
                              stackTraceSerialNumber: Long,
                              superClassId: Long,
                              classloaderClassId: Long,
                              instanceSize: Long,
                              constants: Array<ConstantPoolEntry>,
                              staticFields: Array<StaticFieldEntry>,
                              instanceFields: Array<InstanceFieldEntry>) {
    assert(classId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == classId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(0) // Special value for class definitions, to differentiate from regular java.lang.Class instances
  }

  override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
    assert(arrayObjectId <= Int.MAX_VALUE)
    assert(arrayClassObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == arrayObjectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(arrayClassObjectId.toInt())
    val nonNullElementsCount = objects.count { it != 0L }
    aux.writeId(nonNullElementsCount)
    objects.forEach {
      if (it == 0L) return@forEach
      assert(it <= Int.MAX_VALUE)
      aux.writeId(it.toInt())
    }
  }

  override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {
    assert(objectId <= Int.MAX_VALUE)
    assert(classObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == objectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(classObjectId.toInt())

    var classOffset = 0
    var classDef: ClassDefinition = classStore[classObjectId]
    do {
      classDef.refInstanceFields.forEach {
        val offset = classOffset + it.offset
        val value = bytes.getLong(offset)

        if (value == 0L) {
          aux.writeId(0)
        }
        else {
          // bytes are just raw data. IDs have to be mapped manually.
          val reference = parser.remap(value)
          assert(reference != 0L)
          aux.writeId(reference.toInt())
        }
      }
      classOffset += classDef.superClassOffset

      if (classDef.superClassId == 0L) {
        break
      }
      classDef = classStore[classDef.superClassId]
    }
    while (true)
  }

  private fun FileChannelBackedWriteBuffer.writeId(id: Int) {
    // Use variable-length Int for IDs to save space
    this.writeNonNegativeLEB128Int(id)
  }
}

