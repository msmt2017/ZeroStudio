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

package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.nameFromSource
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.elements.isGetter
import org.jetbrains.kotlin.asJava.elements.isSetter
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isNewArray

/**
 * Detector warning about private inner classes and constructors which require a synthetic accessor
 * to be generated, thereby unnecessarily increasing overhead (methods, extra dispatch). Relevant
 * only in large projects and especially libraries.
 */
class SyntheticAccessorDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(SyntheticAccessorDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
          id = "SyntheticAccessor",
          briefDescription = "Synthetic Accessor",
          explanation =
            """
                A private inner class which is accessed from the outer class will force \
                the compiler to insert a synthetic accessor; this means that you are \
                causing extra overhead. This is not important in small projects, but is \
                important for large apps running up against the 64K method handle limit, \
                and especially for **libraries** where you want to make sure your library \
                is as small as possible for the cases where your library is used in an \
                app running up against the 64K limit.
                """,
          moreInfo = null,
          category = Category.PERFORMANCE,
          priority = 2,
          severity = Severity.WARNING,
          androidSpecific = true,
          enabledByDefault = false,
          implementation = IMPLEMENTATION,
        )
        .setAliases(listOf("SyntheticAccessorCall", "PrivateMemberAccessBetweenOuterAndInnerClass"))
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UCallExpression::class.java, USimpleNameReferenceExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        if (node.isNewArray() || node.isArrayInitializer()) {
          return
        }
        val containingClass = node.getContainingUClass() ?: return

        val method = node.resolve()
        if (method == null) {
          // default constructor
          val classRef = node.classReference ?: return
          val target = classRef.resolve() as? PsiClass ?: return
          if (!context.evaluator.isPrivate(target)) {
            return
          }
          if (target.isEquivalentTo(containingClass.javaPsi)) {
            return
          }

          if (!isSameCompilationUnit(target, node)) {
            return
          }

          reportError(context, node, target, target)
        } else {
          if (!context.evaluator.isPrivate(method)) {
            return
          } else if (isCallToPrivateCtorDueToValueClassType(node)) {
            return
          }

          val aClass = method.containingClass ?: return
          if (aClass == containingClass.javaPsi) {
            return
          }

          if (!isSameCompilationUnit(aClass, node)) {
            return
          }

          val from = node.getContainingUClass()
          if (from != null && from.nameFromSource == "Companion") {
            // TODO: Companion objects can be named with a different name;
            // we need be able to look this up in UAST
            // Another way to do it is
            //  from.psi.modifierList.text.contains("companion")
            return
          }

          // Mention it's an implicit constructor here?
          reportError(context, node, method, aClass)
        }
      }

      private fun isCallToPrivateCtorDueToValueClassType(node: UCallExpression): Boolean {
        // Reaching here (or calling this) means the resolved [method] is `private`.
        // But, one exceptional case: primary constructor whose parameters have `value` class type.
        // In that case, the constructor is created as `private` to hide it from Java.
        // However, in Kotlin, a synthetic constructor (w/ marker) is created and used.
        // Alas, that "synthetic" one is not visible to Java either, and not created by LC.
        // Therefore, the UAST resolution ends up with the original private constructor.
        // To avoid false alarms, we check
        //  1) if the call-site is Kotlin; and
        //  2) if the resolved [method] is a constructor with `value` class parameter.
        if (!isKotlin(node.lang)) return false
        if (!node.isConstructorCall()) return false
        val sourcePsi = node.sourcePsi as? KtCallElement ?: return false
        analyze(sourcePsi) {
          val call = sourcePsi.resolveToCall()?.successfulConstructorCallOrNull() ?: return false
          val parameters = call.partiallyAppliedSymbol.signature.valueParameters
          return parameters.any { param -> typeForValueClass(param.returnType) }
        }
      }

      private fun KaSession.typeForValueClass(type: KaType): Boolean {
        val symbol = type.expandedSymbol as? KaNamedClassSymbol ?: return false
        return symbol.isInline
      }

      private fun isSameCompilationUnit(aClass: PsiClass, node: UElement): Boolean {
        val file1 = UastLintUtils.getContainingFile(aClass)
        val file2 = UastLintUtils.getContainingFile(node.sourcePsi)
        return file1 == file2
      }

      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val resolved = node.resolve() ?: return

        if (!(resolved is PsiMethod || resolved is PsiField)) {
          return
        }
        val member = resolved as PsiMember
        if (!context.evaluator.isPrivate(member)) {
          return
        }
        if (resolved is PsiField && isKotlin(resolved.language)) {
          // We don't recommend changing 'private' Kotlin fields to 'internal', because
          // that's just trading a synthetic accessor for a property accessor.
          return
        }
        val constant = ConstantEvaluator.evaluate(context, node)
        if (constant != null) {
          return // constant expression: inlined by javac or kotlinc
        }

        val containingClass = node.getContainingUClass() ?: return
        val memberClass = member.containingClass
        if (memberClass == null || memberClass.isEquivalentTo(containingClass.javaPsi)) {
          return
        }

        if (!isSameCompilationUnit(memberClass, node)) {
          return
        }

        reportError(context, node, member, memberClass)
      }
    }
  }

  private fun reportError(
    context: JavaContext,
    node: UElement,
    member: PsiMember,
    target: PsiClass,
  ) {
    val location =
      if (node is UCallExpression) {
        context.getCallLocation(node, true, false)
      } else {
        context.getLocation(node)
      }

    val isKotlin = isKotlin(member.language)
    val name = if (isKotlin) "Make internal" else "Make package protected"

    val fixRange =
      if (member is KtLightMethod && (member.isGetter || member.isSetter)) {
        // For Kotlin property accessors we have to modify the property declaration instead.
        val ktProperty = member.kotlinOrigin as? KtProperty ?: return
        context.getLocation(ktProperty)
      } else {
        context.getLocation(member)
      }

    val fix =
      fix()
        .replace()
        .name(name)
        .sharedName(name)
        .range(fixRange)
        .text("private ")
        .with(if (isKotlin) "internal " else "")
        .autoFix()
        .build()

    val memberType =
      if (member is PsiField) {
        "field `${member.name}`"
      } else if (member is PsiMethod) {
        if (member.isConstructor) {
          if (context.evaluator.isStatic(member)) {
            return
          }
          if (isKotlin) {
            // Sealed class? This will create a private constructor we can't delete
            if (context.evaluator.isSealed(member)) {
              return
            }
            if (context.evaluator.isSealed(target)) {
              return
            }
          }
          "constructor"
        } else {
          "method `${member.name}`"
        }
      } else {
        "member"
      }
    val message =
      "Access to `private` $memberType of class `${target.name}` requires synthetic accessor"
    context.report(ISSUE, node, location, message, fix)
  }
}
