/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Checks for problems with wakelocks (such as failing to release them) which can lead to
 * unnecessary battery usage.
 */
private typealias Node = ControlFlowGraph.Node<UElement>

private typealias Edge = ControlFlowGraph.Edge<UElement>

class WakelockDetector : Detector(), ClassScanner, SourceCodeScanner {
  companion object {
    const val ANDROID_APP_ACTIVITY: String = "android.app.Activity"
    private const val PARTIAL_WAKE_LOCK = 0x00000001
    private const val ACQUIRE_CAUSES_WAKEUP = 0x10000000

    val IMPLEMENTATION = Implementation(WakelockDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Problems using wakelocks */
    @JvmField
    val ISSUE: Issue =
      create(
        id = "Wakelock",
        briefDescription = "Incorrect `WakeLock` usage",
        explanation =
          """
          Failing to release a wakelock properly can keep the Android device in a high power mode, \
          which reduces battery life. There are several causes of this, such as releasing the wake \
          lock in `onDestroy()` instead of in `onPause()`, failing to call `release()` in all \
          possible code paths after an `acquire()`, and so on.

          NOTE: If you are using the lock just to keep the screen on, you should strongly consider \
          using `FLAG_KEEP_SCREEN_ON` instead. This window flag will be correctly managed by the \
          platform as the user moves between applications and doesn't require a special permission. \
          See https://developer.android.com/reference/android/view/WindowManager.LayoutParams.html#FLAG_KEEP_SCREEN_ON.
          """,
        category = Category.PERFORMANCE,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using non-timeout version of wakelock acquire */
    @JvmField
    val TIMEOUT: Issue =
      create(
        id = "WakelockTimeout",
        briefDescription = "Using wakeLock without timeout",
        explanation =
          """
          Wakelocks have two acquire methods: one with a timeout, and one without. You should \
          generally always use the one with a timeout. A typical timeout is 10 minutes. If the task \
          takes longer than it is critical that it happens (i.e. can't use `JobScheduler`) then \
          maybe they should consider a foreground service instead (which is a stronger run guarantee \
          and lets the user know something long/important is happening).
          """,
        category = Category.PERFORMANCE,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val WAKELOCK_OWNER = "android.os.PowerManager.WakeLock"
    private const val POWER_MANAGER_OWNER = "android.os.PowerManager"
    private const val RELEASE_METHOD = "release"
    private const val ACQUIRE_METHOD = "acquire"
    private const val IS_HELD_METHOD = "isHeld"
    private const val NEW_WAKE_LOCK_METHOD = "newWakeLock"

    private const val SEEN_EXCEPTION = 1
    private const val SEEN_EXIT = 2
  }

  override fun afterCheckRootProject(context: Context) {
    if (!hasRelease && firstAcquireLocation != null) {
      context.report(
        ISSUE,
        firstAcquireLocation!!,
        "Found a wakelock `acquire()` but no `release()` calls anywhere",
      )
    }
  }

  /** Whether any `acquire()` calls have been encountered. */
  private var hasAcquireCall = false

  /**
   * The location of the first `acquire` call, if any (and only if we're not doing isolated (single
   * file) analysis)
   */
  private var firstAcquireLocation: Location? = null

  /** Whether any `release()` calls have been encountered */
  private var hasRelease = false

  override fun getApplicableMethodNames(): List<String> =
    listOf(ACQUIRE_METHOD, RELEASE_METHOD, NEW_WAKE_LOCK_METHOD)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      ACQUIRE_METHOD -> {
        if (node.valueArgumentCount > 0) {
          // acquire(long timeout) does not require a corresponding release
          return
        }

        if (!context.evaluator.isMemberInClass(method, WAKELOCK_OWNER)) {
          return
        }

        if (
          !hasRelease &&
            !hasAcquireCall &&
            firstAcquireLocation == null &&
            !context.driver.isIsolated() &&
            !context.driver.isSuppressed(context, ISSUE, node)
        ) {
          firstAcquireLocation = context.getLocation(node)
        }

        hasAcquireCall = true

        if (context.isEnabled(TIMEOUT)) {
          val location = context.getLocation(node)
          val fix =
            fix()
              .name("Set timeout to 10 minutes")
              .replace()
              .pattern("acquire\\s*\\(()\\s*\\)")
              .with("10*60*1000L /*10 minutes*/")
              .build()

          context.report(
            TIMEOUT,
            node,
            location,
            "" +
              "Provide a timeout when requesting a wakelock with " +
              "`PowerManager.Wakelock.acquire(long timeout)`. This will ensure the OS will " +
              "cleanup any wakelocks that last longer than you intend, and will save your " +
              "user's battery.",
            fix,
          )
        }

        // If the wakelock is a field, it's quite possible/likely that the release is
        // in a different method, even if we have a local release. (Previously, the
        // code assumed that if we see both an acquire and a release in the same method,
        // this was a local use of the wakelock which was always intended to be cleaned
        // up, but as b/349491177 shows, another possible scenario is releasing locks
        // if some operation failed but otherwise leaving it for another method.)

        val lockDeclaration = node.receiver?.skipParenthesizedExprDown()?.tryResolve()
        if (lockDeclaration is PsiField) {
          return
        }

        // Perform flow analysis in this method to see if we're
        // performing an acquire/release block, where there are code paths
        // between the acquire and release which can result in the
        // release call not getting reached.
        val containingMethod = node.getParentOfType<UMethod>()
        if (containingMethod != null) {
          checkFlow(context, containingMethod, node)
        }
      }
      RELEASE_METHOD -> {
        if (!context.evaluator.isMemberInClass(method, WAKELOCK_OWNER)) {
          return
        }

        hasRelease = true

        // See if the release is happening in an onDestroy method, in an activity.
        val containingMethod = node.getParentOfType<UMethod>()
        if (containingMethod != null && containingMethod.name == "onDestroy") {
          val containingClass = containingMethod.javaPsi.containingClass
          if (
            containingClass != null &&
              context.evaluator.inheritsFrom(containingClass, ANDROID_APP_ACTIVITY)
          ) {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "Wakelocks should be released in `onPause`, not `onDestroy`",
            )
          }
        }
      }
      NEW_WAKE_LOCK_METHOD -> {
        if (!context.evaluator.isMemberInClass(method, POWER_MANAGER_OWNER)) {
          return
        }

        // Constant values are copied into the bytecode, so we have to compare
        // values; however, that means the values are part of the API
        val arguments = node.valueArguments
        if (arguments.size == 2) {
          val constant = ConstantEvaluator.evaluate(context, arguments.first())
          if (constant is Int) {
            val both = PARTIAL_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP
            if ((constant and both) == both) {
              context.report(
                ISSUE,
                node,
                context.getLocation(arguments.first()),
                "Should not set both `PARTIAL_WAKE_LOCK` and `ACQUIRE_CAUSES_WAKEUP`. " +
                  "If you do not want the screen to turn on, get rid of " +
                  "`ACQUIRE_CAUSES_WAKEUP`",
              )
            }
          }
        }
      }
    }
  }

  private fun UCallExpression.isReleaseCall(): Boolean {
    return methodName == RELEASE_METHOD &&
      resolve()?.containingClass?.qualifiedName == WAKELOCK_OWNER
  }

  private fun checkFlow(context: JavaContext, method: UMethod, acquire: UCallExpression) {
    val contents = context.getContents() ?: return
    val offset = method.sourcePsi?.textOffset ?: return
    if (contents.indexOf(RELEASE_METHOD, offset) == -1) {
      // Didn't find both acquire and release in this method; no point in doing
      // local flow analysis
      return
    }

    val releaseCalls = mutableListOf<UCallExpression>()
    method.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.isReleaseCall()) {
            releaseCalls.add(node)
          }
          return super.visitCallExpression(node)
        }
      }
    )
    if (releaseCalls.isEmpty()) {
      return
    }

    val graph =
      ControlFlowGraph.create(
        method,
        builder =
          object : ControlFlowGraph.Companion.Builder(strict = false, trackCallThrows = true) {
            override fun canThrow(reference: UElement, method: PsiMethod): Boolean {
              val name = method.name
              if (name == ACQUIRE_METHOD || name == RELEASE_METHOD || name == IS_HELD_METHOD) {
                return false
              }
              return super.canThrow(reference, method)
            }

            override fun checkBranchPaths(conditional: UExpression): ControlFlowGraph.FollowBranch {
              // If you check for isHeld
              val selector = conditional.findSelector()
              if (selector is UCallExpression) {
                val resolved = selector.resolve()
                if (
                  resolved?.name == IS_HELD_METHOD &&
                    resolved.containingClass?.qualifiedName == WAKELOCK_OWNER
                ) {
                  return ControlFlowGraph.FollowBranch.THEN
                }
              } else if (selector is UBinaryExpression) {
                // If lock != null { lock.release } is fine
                val condition = selector.operator
                if (
                  condition == UastBinaryOperator.NOT_EQUALS ||
                    condition == UastBinaryOperator.IDENTITY_NOT_EQUALS
                ) {
                  if (selector.rightOperand.isNullLiteral()) {
                    return ControlFlowGraph.FollowBranch.THEN
                  }
                }
              }
              return super.checkBranchPaths(conditional)
            }
          },
      )

    val exitPaths = mutableListOf<List<Edge>>()
    val releaseNodes = mutableListOf<Node>()
    val acquireNode = graph.getNode(acquire) ?: return
    val status = dfs(graph, acquireNode, exitPaths, releaseNodes)
    if ((status and SEEN_EXIT) != 0) {
      val viaException = (status and SEEN_EXCEPTION) != 0

      val exitPath = ControlFlowGraph.describePath(exitPaths.first())
      val message = StringBuilder()
      message.append("The `release()` call is not always reached")
      if (exitPath.isNotEmpty() || viaException) {
        message.append(" (")
        if (viaException) {
          message.append("because of a possible exception")
          if (exitPath.isNotEmpty()) {
            message.append(" in the path ").append(exitPath)
          }
        } else {
          message.append("can exit the method via path $exitPath")
        }
        message.append("; use try/finally to ensure `release` is always called)")
      }

      if (releaseNodes.isEmpty()) {
        releaseCalls.mapNotNull { graph.getNode(it) }.forEach { releaseNodes.add(it) }
      }

      if (releaseNodes.size == 0) {
        return
      }
      val call = releaseNodes.first().instruction
      val location: Location =
        context.getCallLocation(
          call as UCallExpression,
          includeReceiver = false,
          includeArguments = false,
        )
      var last = location
      for (i in 1 until releaseNodes.size) {
        val release = releaseNodes[i]
        val element = release.instruction
        val secondary =
          context.getCallLocation(
            element as UCallExpression,
            includeReceiver = false,
            includeArguments = false,
          )
        last.secondary = secondary
        last = secondary
      }
      context.report(ISSUE, acquire, location, message.toString())
    }
  }

  private fun dfs(
    graph: ControlFlowGraph<UElement>,
    startNode: Node,
    exitPaths: MutableList<List<Edge>>,
    releaseNodes: MutableList<Node>,
  ): Int {
    return graph.dfs(
      ControlFlowGraph.IntBitsDomain,
      object : ControlFlowGraph.DfsRequest<UElement, Int>(startNode) {
        override fun visitNode(node: Node, path: List<Edge>, status: Int): Int {
          return if (node.isExit()) {
            exitPaths.add(path)
            SEEN_EXIT or (if (path.any { it.isException }) SEEN_EXCEPTION else 0)
          } else {
            0
          }
        }

        override fun prune(node: Node, path: List<Edge>, status: Int): Boolean {
          val instruction = node.instruction
          return if (instruction is UCallExpression && instruction.isReleaseCall()) {
            releaseNodes.add(node)
            true
          } else {
            false
          }
        }

        override fun isDone(status: Int): Boolean {
          return (status and SEEN_EXIT) != 0
        }
      },
    )
  }
}
