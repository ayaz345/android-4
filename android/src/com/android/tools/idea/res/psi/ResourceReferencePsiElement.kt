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
package com.android.tools.idea.res.psi

import com.android.SdkConstants.ATTR_NAME
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getResourceName
import com.android.tools.idea.res.isValueBased
import com.android.tools.idea.res.resolve
import com.android.tools.idea.res.resourceNamespace
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper
import org.jetbrains.android.util.AndroidResourceUtil

/**
 * A fake PsiElement that wraps a [ResourceReference].
 */
data class ResourceReferencePsiElement(val resourceReference: ResourceReference,
                                       val psiManager: PsiManager) : FakePsiElement() {

  companion object {

    @JvmStatic
    fun create(element: PsiElement): ResourceReferencePsiElement? {
      return when (element) {
        is ResourceReferencePsiElement -> element
        is AndroidLightField -> convertAndroidLightField(element)
        is XmlAttributeValue -> convertXmlAttributeValue(element)
        is PsiFile -> convertPsiFile(element)
        is LazyValueResourceElementWrapper -> ResourceReferencePsiElement(element.resourceInfo.resource.referenceToSelf, element.manager)
        else -> null
      }
    }

    private fun convertPsiFile(element: PsiFile) : ResourceReferencePsiElement? {
      if (!AndroidResourceUtil.isInResourceSubdirectory(element, null)) {
        return null
      }
      val resourceFolderType = getFolderType(element) ?: return null
      val resourceType = FolderTypeRelationship.getNonIdRelatedResourceType(resourceFolderType)
      val resourceNamespace = element.resourceNamespace ?: return null
      val resourceName = getResourceName(element)
      return ResourceReferencePsiElement(ResourceReference(resourceNamespace, resourceType, resourceName), element.manager)
    }

    private fun convertAndroidLightField(element: AndroidLightField) : ResourceReferencePsiElement? {
      if (element.containingClass?.containingClass !is AndroidRClassBase) {
        return null
      }
      val resourceClassName = AndroidResourceUtil.getResourceClassName(element) ?: return null
      val resourceType = ResourceType.fromClassName(resourceClassName) ?: return null
      return ResourceReferencePsiElement(ResourceReference(ResourceNamespace.TODO(), resourceType, element.name), element.manager)
    }

    /**
     * Attempts to convert an XmlAttributeValue into ResourceReferencePsiElement, if the attribute references or defines a resources.
     *
     * These are cases where the [com.intellij.util.xml.ResolvingConverter] does not provide a PsiElement and so the dom provides the
     * invocation element.
     * TODO: Implement resolve in all ResolvingConverters so that we never get the underlying element.
     */
    private fun convertXmlAttributeValue(element: XmlAttributeValue) : ResourceReferencePsiElement? {
      val resUrl = ResourceUrl.parse(element.value)
      if (resUrl != null) {
        val resourceReference = resUrl.resolve(element) ?: return null
        return ResourceReferencePsiElement(resourceReference, element.manager)
      }
      else {
        // Instances of value resources
        val tag = element.parentOfType<XmlTag>() ?: return null
        if ((element.parent as XmlAttribute).name != ATTR_NAME) return null
        val type = AndroidResourceUtil.getResourceTypeForResourceTag(tag) ?: return null
        if (!type.isValueBased()) return null
        val name = element.value
        val resourceReference = ResourceReference(ResourceNamespace.TODO(), type, name)
        return ResourceReferencePsiElement(resourceReference, element.manager)
      }
    }
  }

  override fun getManager(): PsiManager = psiManager

  override fun getProject() = psiManager.project

  override fun getParent(): PsiElement? = null

  override fun isValid() = true

  override fun isWritable() = false

  override fun getName() = null

  override fun isEquivalentTo(element: PsiElement) = create(element) == this
}