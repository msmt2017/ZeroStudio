/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.ResourceReference.Companion.get
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getBaseName
import com.android.utils.Pair
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import java.io.IOException
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Looks for layout inflation calls passing null as the view root. */
class LayoutInflationDetector : LayoutDetector(), SourceCodeScanner {
  private var layoutsWithRootLayoutParams: MutableSet<String>? = null
  private var pendingErrors: MutableList<Pair<String?, Location?>>? = null

  override fun afterCheckRootProject(context: Context) {
    val pendingErrors = pendingErrors ?: return
    for (pair in pendingErrors) {
      val inflatedLayout = pair.first
      if (
        layoutsWithRootLayoutParams == null ||
          !layoutsWithRootLayoutParams!!.contains(inflatedLayout)
      ) {
        // No root layout parameters on the inflated layout: no need to complain
        continue
      }
      val location = pair.second ?: continue
      context.report(ISSUE, location, ERROR_MESSAGE)
    }
  }

  // ---- Implements XmlScanner ----

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement
    if (root != null) {
      val attributes = root.attributes
      var i = 0
      val n = attributes.length
      while (i < n) {
        val attribute = attributes.item(i) as Attr
        if (
          attribute.localName != null &&
            attribute.localName.startsWith(SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX)
        ) {
          val layouts =
            layoutsWithRootLayoutParams
              ?: run {
                val new = HashSet<String>(20)
                layoutsWithRootLayoutParams = new
                new
              }
          layouts.add(getBaseName(context.file.name))
          break
        }
        i++
      }
    }
  }

  // ---- implements SourceCodeScanner ----

  override fun getApplicableMethodNames(): List<String> {
    return listOf(ViewHolderDetector.INFLATE)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    node.receiver ?: return
    val arguments = node.valueArguments
    if (arguments.size < 2) {
      return
    }
    val second = arguments[1]
    if (!second.isNullLiteral()) {
      return
    }
    val first = arguments[0]
    val reference = get(first) ?: return
    if (isUsedWithAlertDialog(context, node)) {
      return
    }
    val layoutName = reference.name
    if (context.scope.contains(Scope.RESOURCE_FILE)) {
      // We're doing a full analysis run: we can gather this information
      // incrementally
      if (!context.driver.isSuppressed(context, ISSUE, node)) {
        val pending =
          pendingErrors
            ?: run {
              val new = ArrayList<Pair<String?, Location?>>()
              pendingErrors = new
              new
            }
        val location = context.getLocation(second)
        pending.add(Pair.of(layoutName, location))
      }
    } else if (hasLayoutParams(context, layoutName)) {
      context.report(ISSUE, node, context.getLocation(second), ERROR_MESSAGE)
    }
  }

  private fun hasLayoutParams(context: JavaContext, name: String): Boolean {
    val client = context.client
    val project = context.project
    val scope = if (context.isGlobalAnalysis()) LOCAL_DEPENDENCIES else PROJECT_ONLY
    val resources = client.getResources(project, scope)
    val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, name)
    if (items.isEmpty()) {
      // Couldn't find layout: uncertain.
      // (In partial analysis, we only look at the local project dependencies; if the
      // layout is in a different module, we won't find it, so we'll just stay uncertain.)
      return true
    }
    for (item in items) {
      val source = item.source ?: return true // Not certain.
      try {
        val parser = client.createXmlPullParser(source)
        if (parser != null && hasLayoutParams(parser)) {
          return true
        }
      } catch (e: XmlPullParserException) {
        context.log(e, "Could not read/parse inflated layout")
        return true // not certain
      } catch (e: IOException) {
        context.log(e, "Could not read/parse inflated layout")
        return true
      }
    }
    return false
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        LayoutInflationDetector::class.java,
        Scope.JAVA_AND_RESOURCE_FILES,
        Scope.JAVA_FILE_SCOPE,
      )

    /** Passing in a null parent to a layout inflater. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "InflateParams",
        briefDescription = "Layout Inflation without a Parent",
        explanation =
          """
                    When inflating a layout, avoid passing in null as the parent view, since \
                    otherwise any layout parameters on the root of the inflated layout will be \
                    ignored.""",
        moreInfo =
          //noinspection LintImplUnexpectedDomain
          "https://www.bignerdranch.com/blog/understanding-androids-layoutinflater-inflate/",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    private const val ERROR_MESSAGE =
      "Avoid passing `null` as the view root (needed to resolve layout parameters on the inflated layout's root element)"

    /**
     * Is this call to the layout inflater used for the Alert Dialog? If so, a null root is okay.
     * See for example "Every Rule Has An Exception" here:
     * https://wundermanthompsonmobile.com/2013/05/layout-inflation-as-intended/
     */
    private fun isUsedWithAlertDialog(context: JavaContext, call: UCallExpression): Boolean {
      val variable = call.getParentOfType<UElement>(UVariable::class.java) ?: return false
      val method = variable.getParentOfType<UMethod>(UMethod::class.java) ?: return false
      val sourcePsi = variable.sourcePsi
      val javaPsi = variable.javaPsi
      val isAlertBuilderUsage = Ref(false)
      method.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            checkUsage(node)
            return super.visitSimpleNameReferenceExpression(node)
          }

          private fun checkUsage(node: USimpleNameReferenceExpression) {
            val resolved = node.resolve() ?: return
            if (resolved != sourcePsi && resolved != javaPsi) {
              return
            }
            val setViewCall = node.uastParent as? UCallExpression ?: return
            if ("setView" != setViewCall.methodName) {
              return
            }
            val receiver = setViewCall.receiver ?: return
            val psiType = receiver.getExpressionType() ?: return
            if (isAlertBuilder(psiType.canonicalText)) {
              isAlertBuilderUsage.set(true)
            } else {
              val evaluator = context.evaluator
              val typeClass = evaluator.getTypeClass(psiType) ?: return
              // Look for create method returning an AlertDialog
              for (m in typeClass.methods) {
                val returnType = m.returnType ?: continue
                if (returnType is PsiPrimitiveType) {
                  continue
                }
                val returnClass = evaluator.getTypeClass(returnType) ?: continue

                // In builders, most methods return self so avoid inheritance check
                if (returnClass === typeClass) {
                  continue
                }
                if (isAlertBuilder(evaluator, returnClass)) {
                  isAlertBuilderUsage.set(true)
                  break
                }
              }
            }
          }
        }
      )
      return isAlertBuilderUsage.get()
    }

    private fun isAlertBuilder(s: String): Boolean {
      return when (s) {
        "android.app.AlertDialog.Builder",
        "android.support.v7.app.AlertDialog.Builder",
        "androidx.appcompat.app.AlertDialog.Builder",
        "com.google.android.material.dialog.MaterialAlertDialogBuilder" -> true
        else -> s.contains("AlertDialog")
      }
    }

    private fun isAlertBuilder(evaluator: JavaEvaluator, cls: PsiClass): Boolean {
      return if (!evaluator.inheritsFrom(cls, "android.app.Dialog", true)) {
        false
      } else
        evaluator.inheritsFrom(cls, "android.app.AlertDialog", false) ||
          evaluator.inheritsFrom(cls, "android.support.v7.app.AlertDialog", false) ||
          evaluator.inheritsFrom(cls, "androidx.appcompat.app.AlertDialog", false)
    }

    @JvmStatic
    @VisibleForTesting
    @Throws(XmlPullParserException::class, IOException::class)
    fun hasLayoutParams(parser: XmlPullParser): Boolean {
      while (true) {
        val event = parser.next()
        if (event == XmlPullParser.START_TAG) {
          for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).startsWith(SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX)) {
              val prefix = parser.getAttributePrefix(i)
              if (
                prefix != null &&
                  prefix.isNotEmpty() &&
                  SdkConstants.ANDROID_URI == parser.getNamespace(prefix)
              ) {
                return true
              }
            }
          }
          return false
        } else if (event == XmlPullParser.END_DOCUMENT || event == XmlPullParser.END_TAG) {
          return false
        }
      }
    }
  }
}
