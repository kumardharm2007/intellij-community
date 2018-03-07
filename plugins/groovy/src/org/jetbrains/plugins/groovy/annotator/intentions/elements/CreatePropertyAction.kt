// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.JvmPsiConversionHelper
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.presentation.java.ClassPresentationUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getPropertyNameByAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getAccessorName
import org.jetbrains.plugins.groovy.lang.psi.util.getNameAndKind

internal class CreatePropertyAction(
  target: GrTypeDefinition,
  override val request: CreateMethodRequest,
  private val readOnly: Boolean
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  private val project = target.project
  private val propertyInfo get() = requireNotNull(getNameAndKind(request.methodName))

  override fun getFamilyName(): String = message("create.property.from.usage.family")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (!super.isAvailable(project, editor, file)) return false
    val (propertyKind, propertyName) = getNameAndKind(request.methodName) ?: return false

    if (propertyKind == SETTER && readOnly) return false

    when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> if (request.parameters.isNotEmpty()) return false
      SETTER -> if (request.parameters.size != 1) return false
    }

    val counterAccessorName = counterPart(propertyKind).getAccessorName(propertyName)
    return target.findMethodsByName(counterAccessorName, false).isEmpty()
  }

  private fun counterPart(propertyKind: PropertyKind): PropertyKind {
    return when (propertyKind) {
      GETTER, BOOLEAN_GETTER -> SETTER
      SETTER -> {
        val expectedType = request.parameters.single().second.singleOrNull()
        if (expectedType != null && PsiType.BOOLEAN == JvmPsiConversionHelper.getInstance(project).convertType(expectedType.theType)) {
          BOOLEAN_GETTER
        }
        else {
          GETTER
        }
      }
    }
  }

  override fun getText(): String {
    val propertyName = getPropertyNameByAccessorName(request.methodName)
    val className = ClassPresentationUtil.getNameForClass(target, false)
    return if (readOnly) {
      message("create.read.only.property.from.usage.full.text", propertyName, className)
    }
    else {
      message("create.property.from.usage.full.text", propertyName, className)
    }
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    CreateFieldAction(target, PropertyRequest(), false).apply {
      if (isAvailable(project, editor, file)) invoke(project, editor, file)
    }
  }

  override fun getActionGroup(): JvmActionGroup = if (readOnly) CreateReadOnlyPropertyActionGroup else CreatePropertyActionGroup

  inner class PropertyRequest : CreateFieldRequest {

    override fun isValid() = true

    override fun getModifiers() = if (readOnly) listOf(JvmModifier.FINAL) else emptyList()

    override fun getFieldName() = propertyInfo.second

    override fun getFieldType() = request.createPropertyTypeConstraints(propertyInfo.first)

    override fun getTargetSubstitutor() = request.targetSubstitutor

    override fun isConstant(): Boolean = false
  }
}