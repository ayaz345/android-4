/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.index

import com.android.tools.idea.flags.StudioFlags
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryIndexer
import com.intellij.util.io.DataExternalizer
import com.intellij.util.text.CharArrayUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataOutput
import java.io.StringReader
import javax.xml.bind.JAXBContext

/**
 * File based index for the parts of navigation xml files relevant to generating Safe Args classes.
 */
class NavXmlIndex : SingleEntryFileBasedIndexExtension<NavXmlData>() {
  companion object {
    private fun getLog() = Logger.getInstance(NavXmlIndex::class.java)

    @JvmField
    val NAME = ID.create<Int, NavXmlData>("NavXmlIndex")

    fun getDataForFile(project: Project, file: VirtualFile): NavXmlData? {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return FileBasedIndex.getInstance().getSingleEntryIndexData(NAME, file, project)
    }
  }

  private val jaxbContext = JAXBContext.newInstance(MutableNavNavigationData::class.java)
  // JAXB marshallers / unmarshallers are not thread-safe, so create a new one each time
  private val jaxbSerializer get() = jaxbContext.createMarshaller()
  private val jaxbDeserializer get() = jaxbContext.createUnmarshaller()

  override fun getVersion() = 10
  override fun dependsOnFileContent() = true
  override fun getName(): ID<Int, NavXmlData> = NAME

  override fun getInputFilter(): FileBasedIndex.InputFilter {
    return object : DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      override fun acceptInput(file: VirtualFile): Boolean {
        if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) {
          return false
        }
        return "xml" == file.extension
      }
    }
  }

  /**
   * Defines the data externalizer handling the serialization/de-serialization of indexed information.
   */
  override fun getValueExternalizer(): DataExternalizer<NavXmlData> {
    return object : DataExternalizer<NavXmlData> {
      override fun save(out: DataOutput, value: NavXmlData) {
        val outBytes = ByteArrayOutputStream().use { writer ->
          jaxbSerializer.marshal(value.root, writer)
          writer.toByteArray()
        }
        out.writeInt(outBytes.size)
        out.write(outBytes)
      }

      override fun read(`in`: DataInput): NavXmlData {
        val inBytes = ByteArray(`in`.readInt())
        `in`.readFully(inBytes)
        val rootNav = ByteArrayInputStream(inBytes).use { bytes -> jaxbDeserializer.unmarshal(bytes) as NavNavigationData }
        return NavXmlData(rootNav)
      }
    }
  }

  override fun getIndexer(): SingleEntryIndexer<NavXmlData> {
    return object : SingleEntryIndexer<NavXmlData>(false) {
      private fun FileContent.looksLikeNavigationXml(): Boolean {
        val text = contentAsText
        var idx = 0

        while (true) {
          // find open tag
          idx = CharArrayUtil.indexOf(text, "<", idx)
          if (idx == -1) return false

          // ignore processing & comment tags
          if (CharArrayUtil.regionMatches(text, idx, "<!--") ||
              CharArrayUtil.regionMatches(text, idx, "<?")) {
            idx++
            continue
          }
          return CharArrayUtil.regionMatches(text, idx, "<navigation")
        }
      }

      override fun computeValue(inputData: FileContent): NavXmlData? {
        if (!inputData.looksLikeNavigationXml()) return null
        return try {
          val rootNav = jaxbDeserializer.unmarshal(StringReader(inputData.contentAsText.toString())) as NavNavigationData
          NavXmlData(rootNav)
        }
        // Normally we'd just catch explicit exceptions, like UnmarshalException, but JAXB also
        // throws AssertionErrors, which isn't documented. Since we don't really care why the parse
        // failed, and we definitely don't want any exceptions to leak to our users here, to be safe
        // we just catch and log all possible problems.
        catch (e: Throwable) {
          getLog().info("${NavXmlIndex::class.java.simpleName} skipping over \"${inputData.file.path}\": ${e.message}")
          null
        }
      }
    }
  }
}
