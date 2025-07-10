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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.interprocedural.CallGraph
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.android.tools.lint.detector.api.interprocedural.CallTarget
import com.android.tools.lint.detector.api.interprocedural.ContextualEdge
import com.android.tools.lint.detector.api.interprocedural.ContextualNode
import com.android.tools.lint.detector.api.interprocedural.DispatchReceiver
import com.android.tools.lint.detector.api.interprocedural.IntraproceduralDispatchReceiverEvaluator
import com.android.tools.lint.detector.api.interprocedural.ParamContext
import com.android.tools.lint.detector.api.interprocedural.buildContextualCallGraph
import com.android.tools.lint.detector.api.interprocedural.searchForContextualPaths
import com.android.tools.lint.detector.api.interprocedural.shortName
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiVariable
import java.util.EnumSet

data class AnnotatedCallPath(
  val contextualNodes: List<ContextualEdge>,
  val sourceAnnotation: String,
  val sinkAnnotation: String,
)

/** Returns a collection of call paths that violate thread annotations found in source code. */
// public because accessed from tools/adt/idea tests
fun searchForInterproceduralThreadAnnotationViolations(
  callGraph: CallGraph,
  receiverEval: IntraproceduralDispatchReceiverEvaluator,
): Collection<AnnotatedCallPath> {

  fun PsiModifierListOwner.isAnnotatedWith(annotation: String) =
    AnnotationUtil.isAnnotated(
      this,
      annotation,
      AnnotationUtil.CHECK_HIERARCHY xor AnnotationUtil.CHECK_EXTERNAL,
    )

  fun CallTarget.isAnnotatedWith(annotation: String) =
    when (this) {
      is CallTarget.Method -> {
        element.isAnnotatedWith(annotation) ||
          element.javaPsi.containingClass?.isAnnotatedWith(annotation) ?: false
      }
      is CallTarget.Lambda -> element.uAnnotations.any { it.qualifiedName == annotation }
      is CallTarget.DefaultCtor -> element.isAnnotatedWith(annotation)
    }

  val contextualGraph = callGraph.buildContextualCallGraph(receiverEval)
  val uiSearchNodes =
    contextualGraph.contextualNodes.filter {
      it.node.target.isAnnotatedWith(UI_THREAD_ANNOTATION.oldName()) ||
        it.node.target.isAnnotatedWith(UI_THREAD_ANNOTATION.newName())
    }
  val workerSearchNodes =
    contextualGraph.contextualNodes.filter {
      it.node.target.isAnnotatedWith(WORKER_THREAD_ANNOTATION.oldName()) ||
        it.node.target.isAnnotatedWith(WORKER_THREAD_ANNOTATION.newName())
    }

  // Some methods take in a lambda (say) and run it on a different thread.
  // By default our analysis would see that there is no direct call through the parameter,
  // and correctly end the corresponding call path.
  // But we would also like to check that the parameter is able to run on the new thread.
  // To do this we find each parameter with a thread annotation, and treat all contextual
  // receivers for that parameters as if they had the thread annotation directly.
  fun paramSearchNodes(annotation: String) =
    contextualGraph.contextualNodes
      .flatMap { searchNode ->
        searchNode.paramContext.params
          .filter { (param, _) -> (param.javaPsi as PsiVariable).isAnnotatedWith(annotation) }
          .map { it.second } // Pulls out receivers.
      }
      .mapNotNull { receiver ->
        val target =
          when (receiver) {
            is DispatchReceiver.Class -> null
            is DispatchReceiver.Functional -> receiver.toTarget()
          }
        // We use an empty parameter context for the lambda (say) that will be invoked on
        // the new thread, as we don't know what arguments will be used when invoked later.
        target?.let { ContextualNode(callGraph.getNode(it.element), ParamContext.EMPTY) }
      }

  val allUiSearchNodes =
    uiSearchNodes +
      paramSearchNodes(UI_THREAD_ANNOTATION.oldName()) +
      paramSearchNodes(UI_THREAD_ANNOTATION.newName())

  val allWorkerSearchNodes =
    workerSearchNodes +
      paramSearchNodes(WORKER_THREAD_ANNOTATION.oldName()) +
      paramSearchNodes(WORKER_THREAD_ANNOTATION.newName())

  val uiThreadAnnotationName = UI_THREAD_ANNOTATION.defaultName().substringAfterLast(".")
  val workerThreadAnnotationName = WORKER_THREAD_ANNOTATION.defaultName().substringAfterLast(".")

  val uiPaths =
    contextualGraph.searchForContextualPaths(allUiSearchNodes, allWorkerSearchNodes).map {
      AnnotatedCallPath(it, uiThreadAnnotationName, workerThreadAnnotationName)
    }

  val workerPaths =
    contextualGraph.searchForContextualPaths(allWorkerSearchNodes, allUiSearchNodes).map {
      AnnotatedCallPath(it, workerThreadAnnotationName, uiThreadAnnotationName)
    }

  return uiPaths + workerPaths
}

class WrongThreadInterproceduralDetector : Detector(), SourceCodeScanner {
  override fun isCallGraphRequired(): Boolean = true

  override fun analyzeCallGraph(context: Context, callGraph: CallGraphResult) {
    val badPaths =
      searchForInterproceduralThreadAnnotationViolations(
        callGraph.callGraph,
        callGraph.receiverEval,
      )
    for ((searchNodes, sourceAnnotation, sinkAnnotation) in badPaths) {
      if (searchNodes.size == 1) {
        // This means that a node in the graph was annotated with both UiThread and
        // WorkerThread. This can happen if an overriding method changes the annotation.
        continue
      }
      val (_, second) = searchNodes
      val pathBeginning = second.cause
      val parser = context.client.getUastParser(context.project)
      val location = parser.createLocation(pathBeginning)
      val pathStr =
        searchNodes.joinToString(separator = " -> ") { it.contextualNode.node.shortName }
      val sourceStr = sourceAnnotation.substringAfterLast('.')
      val sinkStr = sinkAnnotation.substringAfterLast('.')
      val message =
        "Interprocedural thread annotation violation " + "($sourceStr to $sinkStr):\n$pathStr"
      context.report(ISSUE, location, message, null)
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "WrongThreadInterprocedural",
        briefDescription = "Wrong Thread (Interprocedural)",
        explanation =
          """
                Searches for interprocedural call paths that violate thread annotations \
                in the program. Tracks the flow of instantiated types and lambda \
                expressions to increase accuracy across method boundaries.
                """,
        moreInfo =
          "https://developer.android.com/guide/components/processes-and-threads.html#Threads",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        enabledByDefault = false,
        androidSpecific = true,
        implementation =
          Implementation(
            WrongThreadInterproceduralDetector::class.java,
            EnumSet.of(Scope.ALL_JAVA_FILES),
          ),
      )
  }
}
