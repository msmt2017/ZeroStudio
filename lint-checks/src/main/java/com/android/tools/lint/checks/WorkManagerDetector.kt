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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.nameFromSource
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.tryResolve

/** Some lint checks around WorkManager usage. */
class WorkManagerDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(WorkManagerDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Problems with enqueueing work manager continuations. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "EnqueueWork",
        briefDescription = "WorkManager Enqueue",
        explanation =
          """
                `WorkContinuations` cannot be enqueued automatically.  You must call `enqueue()` \
                on a `WorkContinuation` to have it and its parent continuations enqueued inside \
                `WorkManager`.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val CLASS_WORK_MANAGER = "androidx.work.WorkManager"
    private const val CLASS_WORK_CONTINUATION = "androidx.work.WorkContinuation"
    private const val METHOD_BEGIN_WITH = "beginWith"
    private const val METHOD_BEGIN_UNIQUE_WORK = "beginUniqueWork"
    private const val METHOD_ENQUEUE = "enqueue"
    private const val METHOD_ENQUEUE_SYNC = "enqueueSync"
    private const val METHOD_ENQUEUE_UNIQUE = "enqueueUniquePeriodicWork"
    private const val METHOD_THEN = "then"
    private const val METHOD_COMBINE = "combine"
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(METHOD_BEGIN_WITH, METHOD_BEGIN_UNIQUE_WORK, METHOD_THEN, METHOD_COMBINE)
  }

  private fun isEnqueueCall(call: UCallExpression): Boolean {
    val methodName = getMethodName(call)
    if (
      methodName == METHOD_ENQUEUE ||
        methodName == METHOD_ENQUEUE_SYNC ||
        methodName == METHOD_ENQUEUE_UNIQUE
    ) {
      // TODO: check that it's called on the WorkContinuation?
      return true
    } else if (methodName == METHOD_THEN || methodName == METHOD_COMBINE) {
      // Implicitly enqueued by the then/combine methods on the WorkContinuation
      return true
    }

    return false
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (
      !context.evaluator.isMemberInClass(method, CLASS_WORK_MANAGER) &&
        !context.evaluator.isMemberInClass(method, CLASS_WORK_CONTINUATION)
    ) {
      return
    }

    val surrounding = node.getParentOfType<UMethod>(UMethod::class.java, true) ?: return

    if (surrounding.returnType?.canonicalText == CLASS_WORK_CONTINUATION) {
      return
    }

    var enqueued = false

    val visitor =
      object : DataFlowAnalyzer(listOf(node)) {
        override fun receiver(call: UCallExpression) {
          if (isEnqueueCall(call)) {
            enqueued = true
          }
        }

        override fun argument(call: UCallExpression, reference: UElement) {
          val methodName = getMethodName(call)
          if (methodName == METHOD_COMBINE) {
            enqueued = true
          } else {
            // Used in a list etc: start to track the list
            val parent = skipParenthesizedExprUp(call.uastParent)
            if (parent is UQualifiedReferenceExpression) {
              val listVariable = parent.receiver.skipParenthesizedExprDown().tryResolve()
              if (listVariable is PsiLocalVariable) {
                references.add(listVariable)
              } else {
                // List factory method?
                val parentParent = skipParenthesizedExprUp(parent.uastParent)
                if (parentParent is ULocalVariable) {
                  addVariableReference(parentParent)
                }
              }
            } else if (parent is ULocalVariable) {
              // Some direct list construction call, such as listOf() in Kotlin
              addVariableReference(parent)
            }
          }
        }

        override fun returnsSelf(call: UCallExpression): Boolean {
          val methodName = getMethodName(call)
          if (methodName == "synchronous") {
            return true
          }
          return super.returnsSelf(call)
        }
      }
    surrounding.accept(visitor)

    if (!enqueued && !(visitor.failedResolve && surrounding.anyCall(::isEnqueueCall))) {
      val name =
        (skipParenthesizedExprUp(skipParenthesizedExprUp(node.uastParent)?.uastParent)
            as? ULocalVariable)
          ?.nameFromSource
      val nameString = if (name != null) "`$name` " else ""
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        "WorkContinuation ${nameString}not enqueued: did you forget to call `enqueue()`?",
      )
    }
  }
}
