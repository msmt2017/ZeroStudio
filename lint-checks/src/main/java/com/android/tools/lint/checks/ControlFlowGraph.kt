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

import com.android.tools.lint.detector.api.asCall
import com.android.tools.lint.detector.api.callNeverReturns
import com.android.tools.lint.detector.api.findCommonParent
import com.android.tools.lint.detector.api.isJava
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isScopingFunction
import com.intellij.psi.CommonClassNames.JAVA_LANG_EXCEPTION
import com.intellij.psi.CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION
import com.intellij.psi.CommonClassNames.JAVA_LANG_THROWABLE
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiSynchronizedStatement
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import java.util.IdentityHashMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.isFalseLiteral
import org.jetbrains.uast.isTrueLiteral
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.AnalyzerException
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue

/**
 * A [ControlFlowGraph] is a graph containing a node for each instruction in a method, and an edge
 * for each possible control flow; usually just "next" for the instruction following the current
 * instruction, but in the case of a branch such as an "if", multiple edges to each successive
 * location, or with a "goto", a single edge to the jumped-to instruction.
 *
 * It also adds edges for abnormal control flow, such as the possibility of a method call throwing a
 * runtime exception.
 *
 * If during developing your detector you'd like to visualize the graph, you can call the [toDot]
 * method to get a graph description, for example
 *
 * ```
 * graph.toDot(render = { it.instruction.javaClass.simpleName })
 * ```
 *
 * (there are examples of nicer visualizations in `ControlFlowGraphTest`) then put it in a file
 * named for example `/tmp/graph.dot`, and then using the graphviz utilities, visualize it like
 * this:
 * ```
 * /opt/homebrew/bin/dot -Kdot -Tpng -o/tmp/image.png /tmp/graph.dot
 * /usr/bin/open /tmp/image.png
 * ```
 */
open class ControlFlowGraph<T : Any> private constructor() {
  /**
   * Map from instructions to nodes.
   *
   * We use an [IdentityHashMap] here because we really want unique graph nodes for unique
   * instructions, and in UAST for example there are various scenarios where two separate [UElement]
   * instances will be considered equal (perhaps because the underlying equals implementation
   * delegates to source PSI elements).
   *
   * Here's an example:
   * ```
   * list.let {
   *   println(it)
   * }
   * list.let {
   *   println(it)
   * }
   * ```
   *
   * When the lambda bodies are exactly the same, the `KotlinUImplicitReturnExpression` instances in
   * each separate lambda return equal and end up sharing the same graph node, which leads to an
   * invalid control flow graph.
   */
  private val nodeMap = IdentityHashMap<T, Node<T>>(40)
  /** Nodes in insert order, since we can't use a [LinkedHashMap] */
  private val nodeList = mutableListOf<Node<T>>()

  /** Adds a control flow edge to this graph */
  internal fun addSuccessor(from: T?, to: T?, label: String? = null) {
    from ?: return
    to ?: return
    getOrCreate(from).addSuccessor(getOrCreate(to), label)
  }

  /** Adds an exception edge to this graph. The type should be the exception type. */
  internal fun addException(from: T?, to: T?, exceptionType: String) {
    from ?: return
    to ?: return
    getOrCreate(from).addExceptionPath(getOrCreate(to), exceptionType)
  }

  /**
   * Looks up (and if necessary) creates a graph node for the given instruction
   *
   * @param instruction the instruction
   * @return the control flow graph node corresponding to the given instruction
   */
  internal open fun getOrCreate(instruction: T): Node<T> =
    nodeMap.getOrPut(instruction) { Node(instruction).also(nodeList::add) }

  /** Looks up the given graph node for the given instruction. */
  fun getNode(element: T): Node<T>? {
    return nodeMap[element]
  }

  /** Returns all nodes in the graph */
  fun getAllNodes(): Collection<Node<T>> {
    return nodeList
  }

  override fun toString(): String {
    return toDot()
  }

  /**
   * Dumps the control flow graph as a dot graph
   * (https://en.wikipedia.org/wiki/DOT_(graph_description_language) which you can render with
   * something like this: `dot -Tpng -o/tmp/graph.png toString.dot` (or copy-paste into one of the
   * many online services for copy/pasting dot commands and viewing the result)
   */
  fun toDot(
    start: T? = null,
    end: T? = null,
    label: String? = null,
    renderNode: (Node<T>) -> String = { it.instruction.toString() },
    renderEdge: (Node<T>, Edge<T>, Int) -> String = { _, edge, index -> edge.label ?: "s${index}" },
  ): String {
    // make sure parent references are initialized since this is lazy
    val nodes = getAllNodes()

    val sb = StringBuilder()
    sb.append(
      """
      digraph {
        labelloc="t"
        ${if (label != null) "label=\"$label\"\n" else ""}
        node [shape=box]
        subgraph cluster_instructions {
          penwidth=0;
      """
        .trimIndent()
    )
    sb.append('\n')
    val keys = LinkedHashMap<Node<T>, String>()
    var key = 0xA
    for (node in nodes) {
      val id = String.format("N%03x", key++).uppercase()
      keys[node] = id
      sb.append("    ").append(id).append(" [label=\"")
      val instruction = node.instruction
      val describe = renderNode(node).escapeDot()
      assert(describe.isNotBlank())
      sb.append(describe)
      sb.append("\"")
      if (instruction == end) {
        sb.append(",shape=ellipse")
      } else if (instruction == start) {
        sb.append(",shape=ellipse")
      }
      sb.append("]\n")
    }
    sb.append("\n")

    for (node in nodes) {
      node.successors.forEachIndexed { i, edge ->
        val nodeKey = keys[node]!!
        val successor = edge.to
        val successorKey = keys[successor]!!
        sb
          .append("    ")
          .append(nodeKey)
          .append(" -> ")
          .append(successorKey)
          .append(" [label=\" ${renderEdge(node, edge, i)} \"]")
        sb.append("\n")
      }

      node.exceptions.forEach { edge ->
        val exception = edge.to
        val type = edge.label
        val nodeKey = keys[node]!!
        val exceptionKey = keys[exception] ?: "exceptionKeyMissing"
        sb.append("    ").append(nodeKey).append(" -> ").append(exceptionKey)
        sb.append(" [label=\" ${if (type == FINALLY_KEY) "finally" else type} \",style=dashed]")
        sb.append("\n")
      }
    }

    sb.append(
      """
          }
        }"""
        .trimIndent()
    )
    return sb.toString()
  }

  private fun String.escapeDot(): String {
    return replace("\"", "'").replace("\n", "\\n")
  }

  /** Returns all the entry points in the graph (nodes that have no in-bound edges) */
  fun getEntryPoints(): List<Node<T>> {
    return nodeList.filter { it.referenceCount == 0 }
  }

  /** Search from the given node towards the target. */
  fun <C> dfs(domain: Domain<C>, request: DfsRequest<T, C>): C {
    nodeMap.values.forEach { it.visit = 0 }

    fun visit(node: Node<T>, initial: C, path: PersistentList<Edge<T>>, seenException: Boolean): C {
      if (node.visit != 0) {
        return initial
      }
      node.visit = 1

      val status = request.visitNode(node, path, initial)
      if (request.isDone(status)) {
        return status
      }

      if (request.prune(node, path, status)) {
        return status
      }

      val successors =
        // If we're on an exceptional flow, only follow exceptional flows
        if (seenException && request.followExceptionalFlow && node.exceptions.isNotEmpty())
          node.exceptions.asSequence()
        else node.successors.asSequence() + node.exceptions.asSequence()

      return successors.fold(domain.id) { result, edge ->
        domain
          .join(
            visit(
              edge.to,
              status,
              path.add(edge),
              (seenException || edge.isException) &&
                (!request.followExceptionalFlow || !request.consumesException(edge)),
            ),
            result,
          )
          .also { if (request.isDone(it)) return it }
      }
    }

    return visit(request.startNode, domain.id, persistentListOf(), seenException = false)
  }

  /** Configuration for a DFS search */
  abstract class DfsRequest<T : Any, C>(
    /** The node to begin the search from */
    val startNode: Node<T>
  ) {
    /**
     * Visits a reachable control flow node. The arguments are the node itself, the path taken to
     * get to this node, the current "value" computed by the previous [visitNode] calls up to this
     * point.
     *
     * The method should return a new value. The [isDone] lambda will be used to determine if this
     * value means we're done.
     *
     * The [path] is an immutable value that's safe to share
     */
    abstract fun visitNode(node: Node<T>, path: List<Edge<T>>, status: C): C

    /**
     * Determines whether the currently computed value means we're done and should exit out of the
     * depth first search.
     */
    open fun isDone(status: C): Boolean = false

    /**
     * Like [visitNode], but this method can be used to prune a sub graph; the return value
     * indicates whether we should stop at this node. It does not change the value computed for this
     * node by [visitNode]. See the WakelockDetector for example. We use the [visitNode] method to
     * search for exit points out of the method; if we find one, we want the overall computation to
     * end and indicate that there is a possible exit. However, if we find the release call itself,
     * we don't want to conclude that everything is safe; we need to keep searching *other* paths
     * (but not the current one, since for this particular path we've reached a release-call).
     * That's what this method is used for.
     *
     * The [path] is an immutable value that's safe to share
     */
    open fun prune(node: Node<T>, path: List<Edge<T>>, status: C): Boolean = false

    /**
     * Whether to only follow exceptional paths (when available) once we've already taken an
     * exceptional path.
     *
     * For example, consider the following case:
     * ```
     *  try {
     *      randomCall()
     *  } finally {
     *      cleanup()
     *  }
     *  next()
     * ```
     *
     * This gives us the following graph:
     * ```
     *    ╭─ { try { random…up() } next() }
     *  ╭─╰→ try { randomCa…y { cleanup() }
     *  ╰→╭─ { randomCall1() }
     *  ╭─╰→ randomCall1()               ─╮ FileNotFoundException
     *  ╰→╭─ { cleanup() }               ←╯
     *  ╭─╰→ cleanup()                   ─╮ FileNotFoundException
     *  ╰→╭─ next()                       ┆
     *    ╰→ *exit*                      ←╯
     * ```
     *
     * By following the edges here, it's possible to flow via the exception path
     * (FileNotFoundException) form randomCall1 through the finally block and into the `next` call:
     * ```
     * try → randomCall1() → java.io.FileNotFoundException → cleanup() → next() → exit
     * ```
     *
     * This isn't possible at runtime. If the [followExceptionalFlow] mode is turned on, the DFS
     * visitor will *only* follow exceptional flows from nodes where at least one exceptional edge
     * is present -- unless the exception is "consumed" by the [consumesException] override. For AST
     * elements, this would be the case when flowing into a catch block.
     */
    open val followExceptionalFlow: Boolean = false

    /**
     * If [followExceptionalFlow] is true, checks whether the given edge consumes the exception
     * status
     */
    open fun consumesException(edge: Edge<T>): Boolean = false
  }

  data class Domain<C>(
    /** Identity element of [join], i.e. forall x, join(x, initial) = join(initial, x) = x */
    val id: C,

    /** How to merge values of [C] when combining results from multiple branches */
    val join: (C, C) -> C,
  )

  /**
   * Branch decisions in the control flow graph: should we follow both branches or do we know which
   * particular branches are relevant?
   */
  enum class FollowBranch {
    BOTH,
    THEN,
    ELSE,
  }

  /** An edge in the control flow graph */
  class Edge<T : Any>(
    /** Starting node */
    val from: Node<T>,
    /** Ending node: control flows to this node */
    val to: Node<T>,
    /**
     * The edge label, if any. This can for example be "else" for a node flowing out from an if
     * statement node.
     *
     * As a special case, for exceptions, the label is always the fully qualified name of the
     * exception type.
     *
     * As a special case, finally-edges have the label [FINALLY_KEY].
     */
    val label: String? = null,
    /** Whether this edge represents an exceptional flow. */
    val isException: Boolean,
  ) {
    operator fun component1(): Node<T> = from

    operator fun component2(): Node<T> = to

    operator fun component3(): String? = label

    operator fun component4(): Boolean = isException

    override fun toString(): String {
      return "$label:$to"
    }
  }

  /**
   * A [Node] is a node in the control flow graph for a method, pointing to the instruction and its
   * possible successors
   */
  class Node<T : Any>(
    /** The instruction in the program */
    val instruction: T
  ) : Sequence<Edge<T>> {
    private var _successors: MutableList<Edge<T>>? = null
    private var _exceptions: MutableList<Edge<T>>? = null

    /** Any normal successors (e.g. following instruction, or goto or conditional flow) */
    val successors: List<Edge<T>>
      get() = _successors ?: emptyList()

    /** Any abnormal successors (e.g. the handler to go to following an exception) */
    val exceptions: List<Edge<T>>
      get() {
        return _exceptions ?: emptyList()
      }

    /** A tag for use during depth-first-search iteration of the graph etc */
    var visit: Int = 0

    /** Number of other nodes pointing to this node */
    var referenceCount: Int = 0
      private set

    /** Whether this is an exit-point node */
    internal var exit: Boolean = false

    /**
     * Is this node the exit marker (meaning we left the method, via return, or exception, or
     * implicit return, etc.)
     */
    fun isExit(): Boolean {
      return exit
    }

    /** Is this node a "leaf", meaning it has no outgoing edges? */
    fun isLeaf(): Boolean {
      return _successors == null && _exceptions == null
    }

    /**
     * Is this a linear node, meaning that control flow proceeds linearly through the node, without
     * any conditional branching.
     */
    fun isLinear(): Boolean {
      return _exceptions == null && _successors?.size == 1
    }

    internal fun addSuccessor(node: Node<T>, label: String? = null) {
      val successors = _successors ?: mutableListOf<Edge<T>>().also { _successors = it }
      if (successors.any { it.to == node && it.label == label }) {
        return
      }
      successors.add(Edge(this, node, label, false))
      node.referenceCount++
    }

    internal fun addExceptionPath(node: Node<T>, exceptionType: String) {
      // At runtime, exception handlers are searched in order, and only the first matching
      // handler is used. Ideally, we'd also check for the type's inheritance when checking
      // if the map already contains a handler for this type.
      val exceptions = _exceptions ?: mutableListOf<Edge<T>>().also { _exceptions = it }
      if (exceptions.any { it.to == node && it.label == exceptionType }) {
        return
      }
      exceptions.add(Edge(this, node, exceptionType, true))
      node.referenceCount++
    }

    override fun iterator(): Iterator<Edge<T>> {
      return (successors.asSequence() + exceptions.asSequence()).iterator()
    }

    override fun toString(): String {
      return instruction.toString()
    }

    /**
     * Returns true if there is a path from this node to the given [target] node. For more general
     * purpose graph searching, see [ControlFlowGraph.dfs].
     */
    fun flowsTo(target: Node<T>): Boolean {
      val visited = hashSetOf<Node<T>>()

      fun flowsTo(source: Node<T>, target: Node<T>): Boolean =
        visited.add(source) && (source == target || source.any { flowsTo(it.to, target) })

      return flowsTo(this, target)
    }
  }

  companion object {
    const val FINALLY_KEY = "finally"

    /** Marker interface on interfaces that have a single function */
    private const val FUNCTIONAL_INTERFACE_CLASS = "java.lang.FunctionalInterface"
    /** *Prefix* for the function interfaces -- Function1, Function2, Function3, etc. */
    private const val KOTLIN_FUNCTION_PREFIX = "kotlin.jvm.functions.Function"
    /** * *Prefix* for the function interfaces w/ reflection info -- KFunction* */
    private const val KOTLIN_KFUNCTION_PREFIX = "kotlin.reflect.KFunction"
    /** Jetpack Compose marker interface */
    private const val COMPOSABLE_CLASS = "androidx.compose.runtime.Composable"

    private val DEFAULT_EXCEPTIONS_JAVA: List<String> = listOf(JAVA_LANG_RUNTIME_EXCEPTION)
    private val DEFAULT_EXCEPTIONS_KOTLIN: List<String> = listOf(JAVA_LANG_EXCEPTION)
    private val DEFAULT_EXCEPTIONS_STRICT: List<String> = listOf(JAVA_LANG_THROWABLE)

    val BoolDomain = Domain(false, Boolean::or)
    val IntBitsDomain = Domain(0, Int::or)
    val UnitDomain = Domain(Unit) { _, _ -> }

    private fun String?.isFunctionInterface(): Boolean {
      if (this == null) return false
      return this.startsWith(KOTLIN_FUNCTION_PREFIX) || this.startsWith(KOTLIN_KFUNCTION_PREFIX)
    }

    /**
     * Creates a new [ControlFlowGraph] and populates it with the flow control for the given method.
     * If the optional `initial` parameter is provided with an existing graph, then the graph is
     * simply populated, not created. This allows subclassing of the graph instance, if necessary.
     *
     * @param classNode the class containing the method to be analyzed
     * @param method the method to be analyzed
     * @return a [ControlFlowGraph] with nodes for the control flow in the given method
     * @throws AnalyzerException if the underlying bytecode library is unable to analyze the method
     *   bytecode
     */
    @Throws(AnalyzerException::class)
    fun create(classNode: ClassNode, method: MethodNode): ControlFlowGraph<AbstractInsnNode> {
      val graph = ControlFlowGraph<AbstractInsnNode>()
      val instructions = method.instructions

      // Create a flow control graph using ASM5's analyzer. According to the ASM 4 guide
      // (download.forge.objectweb.org/asm/asm4-guide.pdf) there are faster ways to construct
      // it, but those require a lot more code.
      val interpreter = BasicInterpreter()
      val analyzer =
        object : Analyzer<BasicValue>(interpreter) {
          override fun newControlFlowEdge(insn: Int, successor: Int) {
            // Update the information as of whether the `this` object has been
            // initialized at the given instruction.
            val from = instructions[insn]
            val to = instructions[successor]
            graph.addSuccessor(from, to)
          }

          override fun newControlFlowExceptionEdge(insn: Int, tcb: TryCatchBlockNode): Boolean {
            val from = instructions[insn]
            exception(from, tcb)
            return super.newControlFlowExceptionEdge(insn, tcb)
          }

          /**
           * Adds an exception try block node to this graph. This is called for all instructions in
           * the range of the tcb.
           */
          private fun exception(from: AbstractInsnNode, tcb: TryCatchBlockNode) {
            // Add tcb's to all instructions in the range
            // Add exception edges for all method calls in the range
            // All methods can throw exceptions. Notably, Kotlin does not have checked exceptions.
            if (
              from.type == AbstractInsnNode.METHOD_INSN ||
                (from.type == AbstractInsnNode.INSN && from.opcode == Opcodes.ATHROW)
            ) {
              // Method call or throw instruction; add exception edge to handler
              //
              // If `tcb.type == null`, this is a `finally` block. `finally` blocks passed to here
              // are still considered exceptions because they are handled different from `finally`
              // blocks that are arrived at through normal execution. When a `finally` block is
              // reached via an exception, it will rethrow (e.g. ATHROW) at the end, whereas
              // `finally` blocks reached through normal code execution are inlined and do not end
              // with an ATHROW instruction. Both code paths for `finally` are compiled into the
              // bytecode separately.
              graph.addException(from, tcb.handler, tcb.type)
            }
          }
        }

      analyzer.analyze(classNode.name, method)
      for (node in graph.getAllNodes()) {
        val instruction = node.instruction
        val opcode = instruction.opcode
        if (
          opcode == Opcodes.RETURN ||
            opcode == Opcodes.ARETURN ||
            opcode == Opcodes.LRETURN ||
            opcode == Opcodes.IRETURN ||
            opcode == Opcodes.DRETURN ||
            opcode == Opcodes.FRETURN
        ) {
          node.exit = true
        } else if (node.isLeaf()) {
          // Implicit return
          node.exit = true
        }
      }
      return graph
    }

    /**
     * Builder used during construction of a UAST control flow graph - helps to make decisions about
     * whether method calls can throw, etc.
     */
    open class Builder(
      /**
       * Whether the control flow graph should be constructed for "strict" enforcement, assuming
       * worst case scenarios. For example, in this mode, [trackCallThrows] defaults to true, such
       * that any method call is considered throwing unless it is clearly safe; the [allowPure] flag
       * allows the control flow graph to look at methods with source code to discover if they only
       * look like simple methods with no calls, but in strict mode they will also have to be final.
       * Finally, the code to discover which exceptions are thrown from a method will look at the
       * declared exceptions (a throws statement in Java and Throws annotation in Kotlin) and if
       * found, it will assume *only* those exceptions are thrown, but in strict mode it will always
       * add in the default exceptions as well. Finally, the exceptions thrown normally default to
       * RuntimeException for Java and Exception for Kotlin, but in strict mode, they're all assumed
       * to throw Throwable (which includes errors like out of memory etc.)
       */
      val strict: Boolean,
      /**
       * Whether to automatically add exception edges for any calls found, mapping to the correct
       * exception handler or the method exit if there is no applicable surrounding exception
       * handler
       */
      val trackCallThrows: Boolean = strict,
      /**
       * Whether to add unchecked exception edges for Java calls that do not declare they throw
       * anything. This only takes effect if trackCallThrows=true, and it only changes the behavior
       * of Java calls; Kotlin calls ignore this setting and add edges for exceptions whenever
       * trackCallThrows=true. If strict=true, this setting is ignored because it adds edges for
       * [java.lang.Throwable] on all Java and Kotlin calls.
       */
      val trackUncheckedExceptions: Boolean = trackCallThrows,
      /**
       * If true, for potential method calls, if the method body is available and simple, look at it
       * and determine whether it looks safe enough to assume it won't throw an exception under
       * normal circumstances (e.g. simple getters (without qualified expressions which can throw a
       * null pointer exception on the receiver)) or some usually safe well known operations such as
       * calling "isEmpty()" and so on. Helps avoid large number of false positives (at least unless
       * a very high safety bar is required).
       */
      private val allowPure: Boolean = true,
      /**
       * Whether we should automatically connect an edge from a method call to each of its lambda
       * parameters -- in other words, whether we assume that a method will unconditionally call
       * into the lambda.
       *
       * Certain lambda functions (such as the Kotlin scoping functions -- let, apply, with, run)
       * will always be executed, so the call graph will connect the call with the lambda parameter.
       * Interestingly, in the Kotlin standard library there is a `contracts{}` clause which makes
       * this a guarantee: `contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }`
       *
       * In general, lambda code may not be executed by the call -- it may be stored for some other
       * side effect to deal with it for example. The [callLambdaParameters] property controls
       * whether the control flow graph should connect these.
       */
      val callLambdaParameters: Boolean = !strict,
    ) {
      /** Allow subclasses to prune flow control graph branches for specific scenarios. */
      open fun checkBranchPaths(conditional: UExpression): FollowBranch {
        return FollowBranch.BOTH
      }

      /** Whether the given reference to a method can throw an exception */
      open fun canThrow(reference: UElement, method: PsiMethod): Boolean {
        if (trackCallThrows && allowPure && isSafe(method)) {
          return false
        }
        return trackCallThrows
      }

      /**
       * If the given method reference can throw an exception, returns the list of exceptions thrown
       */
      open fun methodThrows(reference: UElement, method: PsiMethod): List<String>? {
        if (!trackCallThrows) {
          return null
        }
        if (!canThrow(reference, method)) {
          return null
        }

        val explicitThrows = method.throwsList.referencedTypes
        if (explicitThrows.isNotEmpty()) {
          // If there is an explicit throws, we'll assume that the method
          // is careful to declare what it may throw, including runtime
          // exceptions, so we won't also add getDefaultMethodExceptions(reference)
          return explicitThrows.map { it.canonicalText }
        }

        return getDefaultMethodExceptions(reference)
      }

      /**
       * Is the given method definitely final? It will be final if it's marked as final, or if it's
       * a member of a final class. In Java, final classes are explicitly marked as final. In
       * Kotlin, they're not marked as open.
       */
      private fun isFinal(method: PsiMethod): Boolean {
        if (method.modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          // (This will be the case for Kotlin non-open functions too; UAST adds a final modifier)
          return true
        }
        val containingClass = method.containingClass ?: return false
        val modifierList = containingClass.modifierList

        return modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)
      }

      /** Is the given method "safe" in the sense that it definitely won't throw any exceptions? */
      private fun isSafe(method: PsiMethod?): Boolean {
        // Can't analyze bytecode
        method ?: return false
        if (!strict && method.throwsList.referencedTypes.isNotEmpty()) {
          return false
        }

        if (strict && !isFinal(method)) {
          return false
        }

        val name = method.name
        when (name) {
          "emptyList",
          "isEmpty",
          "listOf",
          "equals" -> return true
        }

        if (isScopingFunction(method)) {
          return true
        }

        val body = UastFacade.getMethodBody(method) ?: return false
        return isSafe(body)
      }

      private fun isSafe(element: UExpression?): Boolean {
        when (element) {
          null -> return true
          is UBlockExpression -> return element.expressions.all(::isSafe)
          is ULiteralExpression -> return true
          // try/catch: assume it's catching everything relevant
          is UTryExpression -> return element.catchClauses.isNotEmpty()

          // USimpleNameReferenceExpression may be a property reference so not always safe;
          // consider whether to look into this
          is USimpleNameReferenceExpression -> return true
          is UReturnExpression -> {
            val expression = element.returnExpression ?: return true
            return isSafe(expression)
          }
          is UParenthesizedExpression -> return isSafe(element.expression)
          is UastEmptyExpression -> return true
          is UPolyadicExpression -> {
            if (element is UBinaryExpression && element.resolveOperator() != null) {
              return false
            }
            return element.operands.all(::isSafe)
          }
          is UIfExpression -> {
            return isSafe(element.condition) &&
              isSafe(element.thenExpression) &&
              isSafe(element.elseExpression)
          }
          is UPostfixExpression -> return isSafe(element.operand)
          is UPrefixExpression -> return isSafe(element.operand)
          is UDeclarationsExpression ->
            return element.declarations.all {
              it is ULocalVariable && (it.uastInitializer == null || isSafe(it.uastInitializer))
            }
          is UQualifiedReferenceExpression -> {
            if (element.accessType.name == "?.") {
              return isSafe(element.receiver) && isSafe(element.selector)
            } else if (
              element.receiver is UThisExpression || element.receiver is USuperExpression
            ) {
              return isSafe(element.selector)
            }
            return false
          }

          // Note: Not handling UCallExpression here (we could resolve then call
          // isSafe on the method bodies recursively.) That's costly; we'll err on
          // the side of caution instead.

          else -> return false
        }
      }

      /**
       * List of exceptions thrown by an unknown method call. In Java, with checked exceptions, we
       * assume it's a runtime exception; in Kotlin, it can be anything.
       */
      fun getDefaultMethodExceptions(reference: UElement): List<String> {
        val defaults =
          if (strict) {
            // strict mode takes precedence over all other exception-handling settings
            DEFAULT_EXCEPTIONS_STRICT
          } else if (isKotlin(reference.lang)) {
            // Kotlin is not allowed to ignore unchecked exceptions, so always return the default
            // exceptions list
            DEFAULT_EXCEPTIONS_KOTLIN
          } else if (trackUncheckedExceptions) { // !strict && !kotlin
            DEFAULT_EXCEPTIONS_JAVA
          } else {
            emptyList()
          }

        val catches =
          reference
            .getParentOfType<UTryExpression>()
            ?.catchClauses
            ?.map { it.types }
            ?.flatten()
            ?.map { it.canonicalText }
            ?.filter { !defaults.contains(it) }
            ?.ifEmpty {
              return defaults
            } ?: return defaults

        return defaults + catches
      }
    }

    /**
     * Creates an AST-based control flow graph.
     *
     * We visit the nodes of the AST and add control flow edges into the graph. If there is an
     * implicit return node, we add that one into the graph as well to make analysis looking for
     * exit points easier.
     *
     * Various things handled:
     * - Control structures (if/else, try/catch, for, while, etc.)
     * - Implicit calls, e.g. "+" and "[]" overloaded operators, as well as property references
     * - Short circuit evaluation
     */
    fun create(method: UMethod, builder: Builder): ControlFlowGraph<UElement> {
      val graph =
        object : ControlFlowGraph<UElement>() {
          // There are scenarios (exposed by the unit tests) where UAST will recreate
          // ULambdaExpression
          // instances on the fly; this wreaks havoc on the element-to-node mapping, so special
          // case this by using the source PSI element mappings for these. (We can't use source PSI
          // elements as map keys in general since for example for properties, we have a 1-many
          // mapping from PSI elements to UAST elements.)
          private val lambdas = mutableMapOf<KtLambdaExpression, Node<UElement>>()

          override fun getOrCreate(instruction: UElement): Node<UElement> {
            if (instruction is ULambdaExpression) {
              val sourcePsi = instruction.sourcePsi
              if (sourcePsi is KtLambdaExpression) {
                lambdas[sourcePsi]?.let {
                  return it
                }
                val node = super.getOrCreate(instruction)
                lambdas[sourcePsi] = node
                return node
              }
            }
            return super.getOrCreate(instruction)
          }
        }
      graph.getOrCreate(method).exit = true
      // noinspection UnnecessaryVariable
      val exitMarker = method
      val nextStack = ArrayDeque<UElement>()
      nextStack.addLast(exitMarker)

      /** Edge type for the pending nodes */
      var pendingType: String? = null

      /** List of pending nodes that have not yet been linked to the next successor. */
      val pending = mutableListOf<UElement>()

      /**
       * Map of jump sources (e.g. break, continue and return statements) to the corresponding jump
       * target (e.g. loops, methods)
       */
      val pendingJumps = mutableMapOf<UElement, MutableList<UElement>>()

      /**
       * Map of the list of throwing nodes (e.g. throw statements, calls that can throw exceptions,
       * etc.) associated with each try/catch handler. The values are pairs of the throwing call and
       * the exception thrown, if known.
       */
      val pendingThrows = mutableMapOf<UElement, MutableList<Pair<UElement, List<String>>>>()

      val isKotlin = isKotlin(method.lang)

      method.uastBody?.let { graph.getOrCreate(it) }
      method.uastBody?.accept(
        object : AbstractUastVisitor() {
          override fun visitElement(node: UElement): Boolean {
            if (
              node is UIdentifier ||
                // KotlinClassViaConstructorUSimpleReferenceExpression is one of these
                // yet somehow isn't invoked as visitSimpleNameReferenceExpression
                node is USimpleNameReferenceExpression
            ) {
              return true
            }
            return super.visitElement(node)
          }

          override fun afterVisitElement(node: UElement) {
            if (node is UIdentifier || node is USimpleNameReferenceExpression) {
              return
            }
            flushPending(node)
            if (node !is UReturnExpression && node !is UThrowExpression) {
              pending.add(node)
            }
          }

          private fun flushPending(node: UElement) {
            for (prev in pending) {
              if (node == method.uastBody) {
                graph.addSuccessor(prev, method, pendingType) // special exit marker
              } else {
                graph.addSuccessor(prev, node, pendingType)
              }
            }
            pending.clear()
            pendingType = null
          }

          private fun addJumpTarget(from: UElement, jumpTarget: UElement) {
            val list =
              pendingJumps[jumpTarget]
                ?: mutableListOf<UElement>().also { pendingJumps[jumpTarget] = it }
            list.add(from)
          }

          // Also link any element-specific jumps recorded from
          // nested nodes (typically jump targets like breaks and continues)
          private fun processPendingJumps(node: UElement) {
            pendingJumps.remove(node)?.let(pending::addAll)
          }

          /**
           * Adds a throwing call ([node]) of a given or unknown exception [types] to the nearest
           * handler (try/catch or surrounding method exit) found around the [context] node.
           */
          private fun addThrowingCall(
            node: UElement,
            types: List<String>,
            context: UElement?,
          ): Boolean {
            var curr = context
            while (curr != method) {
              if (curr is UTryExpression) {
                break
              }
              curr = curr?.uastParent ?: break
            }
            if (curr != null) {
              val list =
                pendingThrows[curr]
                  ?: mutableListOf<Pair<UElement, List<String>>>().also {
                    pendingThrows[curr!!] = it
                  }
              list.add(Pair(node, types))
            }
            return curr != null
          }

          override fun visitBlockExpression(node: UBlockExpression): Boolean {
            flushPending(node)
            pending.add(node)

            if (!isKotlin && node.sourcePsi is PsiSynchronizedStatement) {
              // Weirdly JavaUSynchronizedExpression is just a UBlockExpression with
              // its own visitor (which visits the lock expression last instead of
              // first as expected), so override and handle that here.
              var lockExpression: UElement? = null
              val block = node
              val expressions = node.expressions
              // Find the lock expression; we can't access it directly because
              // JavaUSynchronizedExpression doesn't expose it anywhere except
              // via a visitor.
              node.accept(
                object : AbstractUastVisitor() {
                  override fun visitElement(node: UElement): Boolean {
                    if (node.uastParent === block && !expressions.contains(node)) {
                      lockExpression = node
                    }
                    return super.visitElement(node)
                  }
                }
              )
              lockExpression?.accept(this)
            }

            for (element in node.expressions) {
              element.accept(this)
            }
            return true
          }

          override fun afterVisitBlockExpression(node: UBlockExpression) {}

          override fun visitExpressionList(node: UExpressionList): Boolean {
            flushPending(node)
            pending.add(node)

            // Really want to look for KotlinSpecialExpressionKinds.ELVIS instead,
            // but it's an internal API
            if (isKotlin && node.kind.name == "elvis") {
              for (element in node.expressions) {
                if (element is UIfExpression) {
                  visitIfExpression(element)
                } else {
                  element.accept(this)
                }
              }
              return true
            }

            for (element in node.expressions) {
              element.accept(this)
            }
            return true
          }

          // Skip anonymous classes
          override fun visitClass(node: UClass): Boolean {
            return true
          }

          // Skip nested methods -- for now
          override fun visitMethod(node: UMethod): Boolean {
            return true
          }

          override fun visitAnnotation(node: UAnnotation): Boolean {
            return true
          }

          private var functions: MutableMap<PsiElement, UElement>? = null
          private var lambdaExits: MutableMap<UElement, List<UElement>>? = null

          private fun registerLambdaElement(psiElement: PsiElement?, element: UElement) {
            psiElement ?: return

            val functions =
              functions ?: mutableMapOf<PsiElement, UElement>().also { functions = it }
            @Suppress("UElementAsPsi")
            functions[psiElement] = element
          }

          private fun registerLambdaExits(element: UElement, exits: List<UElement>) {
            val lambdaExits =
              lambdaExits ?: mutableMapOf<UElement, List<UElement>>().also { lambdaExits = it }
            lambdaExits[element] = exits
          }

          override fun visitVariable(node: UVariable): Boolean {
            val uastInitializer = node.uastInitializer
            if (uastInitializer is ULambdaExpression) {
              // Includes Kotlin local functions
              registerLambdaElement(uastInitializer.javaPsi, node)
              registerLambdaElement(node.sourcePsi, node)
              registerLambdaElement(node.javaPsi, node)
              registerLambdaExits(node, pending.toList())
              val before = pending.toList()
              pending.clear()
              handleLambdaExpression(uastInitializer)
              registerLambdaExits(node, pending.toList())
              pending.clear()
              pending.addAll(before)
            } else if (uastInitializer is UCallableReferenceExpression) {
              val resolved = uastInitializer.resolve()
              if (resolved != null) {
                val function = functions?.get(resolved)
                if (function != null) {
                  registerLambdaElement(node.javaPsi, function)
                  registerLambdaElement(resolved, function)
                }
              }
            } else if (uastInitializer is UObjectLiteralExpression) {
              registerLambdaElement(uastInitializer.javaPsi, node)
              registerLambdaElement(node.sourcePsi, node)
              registerLambdaElement(node.javaPsi, node)
              registerLambdaExits(node, pending.toList())
              val before = pending.toList()
              pending.clear()
              handleObjectLiteralExpression(uastInitializer)
              registerLambdaExits(node, pending.toList())
              pending.clear()
              pending.addAll(before)
            }

            return super.visitVariable(node)
          }

          override fun visitIfExpression(node: UIfExpression): Boolean {
            flushPending(node)
            pending.add(node)

            val condition = node.condition
            condition.accept(this)

            val branchAction =
              if (condition.isTrueLiteral()) {
                FollowBranch.THEN
              } else if (condition.isFalseLiteral()) {
                FollowBranch.ELSE
              } else {
                builder.checkBranchPaths(condition)
              }

            val before = pending.toList()

            var afterThen = emptyList<UElement>()
            if (branchAction == FollowBranch.BOTH || branchAction == FollowBranch.THEN) {
              val thenExpression = node.thenExpression
              if (thenExpression != null && thenExpression !is UastEmptyExpression) {
                pendingType = "then"
                thenExpression.accept(this)
              }

              afterThen = pending.toList()
              if (branchAction == FollowBranch.BOTH) {
                pending.clear()
                pending.addAll(before)
              }
            }

            if (branchAction == FollowBranch.BOTH || branchAction == FollowBranch.ELSE) {
              val elseExpression = node.elseExpression
              if (elseExpression != null && elseExpression !is UastEmptyExpression) {
                pendingType = "else"
                elseExpression.accept(this)
              }
              pending.addAll(afterThen)
            }

            // Already handled children directly
            return true
          }

          override fun afterVisitIfExpression(node: UIfExpression) {}

          /**
           * Add the given try-catch [node] to the control flow graph.
           *
           * The normal flow is that we flow into the try clause, and from there, any exits flow
           * into the finally-clause and from there out of the try-catch statement.
           *
           * Any throwing calls within the try-clause are linked via exception edges into each of
           * the catch clauses. And the catch clauses then flow via normal (non-exception) edges
           * into the finally-clause.
           *
           * If there are no catch clauses, any throwing calls within the try-clause, or if there
           * are throwing calls within the catch-clauses, these are linked via exceptional edges to
           * the finally-clause. And, from there, all the exit points from the finally-clause then
           * bubble up to handling within the next surrounding try/catch statement. This means we
           * can have a blocking call with an exception edge to the nearest finally statement, and
           * from there to the next outer finally statement, and finally from there exiting the
           * method abnormally.
           */
          override fun visitTryExpression(node: UTryExpression): Boolean {
            val psiElement = node.sourcePsi ?: return true

            flushPending(node)
            pending.add(node)

            node.resourceVariables.acceptList(this)
            val tryClause = node.tryClause
            tryClause.accept(this)

            // Flows out of the try block and the catch blocks. Track
            // these so we can accumulate them to all point to the final
            // block.
            val normalExits = pending.toMutableList()
            pending.clear()

            val catchClauses = node.catchClauses
            for (catchClause in catchClauses) {
              pending.add(catchClause)
              pendingType = "catch"
              catchClause.body.accept(this)
              normalExits.addAll(pending)
              pending.clear()
            }

            pending.addAll(normalExits)
            val finallyClause = node.finallyClause
            if (finallyClause != null) {
              pendingType = FINALLY_KEY
              finallyClause.accept(this)
              pendingType = null
            }

            // Link from any pending exceptions inside the method
            val pairs = pendingThrows.remove(node)
            if (pairs != null) {
              for ((from, types) in pairs) {
                // See where the throw is coming from
                var prev: UElement? = from
                var curr = from.uastParent
                while (curr !== node) {
                  prev = curr
                  curr = curr?.uastParent ?: break
                }

                if (prev === tryClause && catchClauses.isNotEmpty()) {
                  val unhandled = mutableListOf<String>()
                  for (type in types) {
                    var caught = false

                    if (type == FINALLY_KEY) { // not a real type
                      assert(types.size == 1)
                      break
                    }

                    val typeClass =
                      JavaPsiFacade.getInstance(psiElement.project)
                        .findClass(type, psiElement.resolveScope)

                    catchLoop@ for (catchClause in catchClauses) {
                      for (psiType in catchClause.types) {
                        val catchType = psiType.canonicalText
                        if (
                          catchType == type || InheritanceUtil.isInheritor(typeClass, catchType)
                        ) {
                          caught = true
                          graph.addException(from, catchClause, type)
                          break@catchLoop
                        } else if (InheritanceUtil.isInheritor(psiType, type)) {
                          // The catch is a subclass of the throwable. That means that
                          // it's *possible* the exception is caught (so we should draw
                          // an edge) but we're not done; the exception may be of a different
                          // type, so we should continue matching.
                          graph.addException(from, catchClause, catchType)
                        }
                      }
                    }
                    if (!caught) {
                      // This type has not been caught; bubble it outward
                      unhandled.add(type)
                      continue
                    }
                  }
                  if (unhandled.isNotEmpty()) {
                    if (finallyClause != null) {
                      graph.addException(from, finallyClause, FINALLY_KEY)
                      for (finallyExit in pending) {
                        addThrowingCall(finallyExit, unhandled, node.uastParent)
                      }
                    } else {
                      addThrowingCall(from, unhandled, node.uastParent)
                    }
                  }
                  continue
                }
                if (prev == finallyClause) {
                  // The throw is from within the finally-clause; bubble it outward
                  addThrowingCall(from, types, node.uastParent)
                } else if (finallyClause != null) {
                  // We have a throw from within the try-clause or a catch-clause and we
                  // have a finally-block. We should direct the call to the finally-clause
                  if (normalExits.contains(from)) {
                    graph.addSuccessor(from, finallyClause, FINALLY_KEY)
                  }
                  for (type in types) {
                    graph.addException(from, finallyClause, type)
                  }

                  // We should also make sure that after the finally-statement we then
                  // continue bubbling the throw outwards:
                  for (finallyExit in pending) {
                    addThrowingCall(finallyExit, types, node.uastParent)
                  }
                }
              }
            }
            return true
          }

          override fun afterVisitTryExpression(node: UTryExpression) {
            // should never be called, we're overriding visitTryExpression without calling it
            assert(false)
          }

          // Add any exception edges from here
          private fun addExceptions(
            node: UElement,
            call: UCallExpression,
            method: PsiMethod? = call.resolve(),
          ) {
            method ?: return // can't find method -- ignore or report? Unclear.
            val types = builder.methodThrows(call, method)
            if (types != null) {
              addThrowingCall(node, types, node.uastParent)
            }
          }

          /**
           * If we have created local functions or lambda definitions (and we're invoking it
           * directly), look up the called [UElement] for the function/lambda declaration.
           */
          private fun findInvokedLambda(
            psiElement: PsiElement,
            node: UCallExpression,
            resolved: PsiMethod?,
          ): UElement? {
            val map = functions ?: return null

            map[psiElement]?.let {
              return it
            }
            map[psiElement.unwrapped]?.let {
              return it
            }

            // Currently, if we call a local function, UAST will resolve into a method
            // of type `UastFakeSourceLightMethod`. However, that class has no support
            // for looking up the corresponding source PSI element (other than via
            // reflection, which is extra messy since the "original" field is also
            // internal, so the method name is mangled.) Instead, use the analysis
            // API.
            // to handle these classes.
            val sourcePsi = node.sourcePsi
            if (sourcePsi is KtElement) {
              analyze(sourcePsi) {
                val symbol = getFunctionLikeSymbol(sourcePsi)
                val psi = symbol?.psi
                if (psi != null) {
                  map[psi]?.let {
                    return it
                  }
                }
              }
            }

            // Kotlin lambda invocations:
            val containingClass = resolved?.containingClass
            if (containingClass != null) {
              if (
                resolved.name == "invoke" && containingClass.qualifiedName.isFunctionInterface()
              ) {
                val variable = node.receiver?.tryResolve()
                if (variable != null) {
                  map[variable]?.let {
                    return it
                  }
                }
              }

              // For Java lambda invocations, look for @FunctionalInterface, e.g. "test" on
              // predicate, "run" on Interface, etc.
              if (
                containingClass.annotations.any { it.qualifiedName == FUNCTIONAL_INTERFACE_CLASS }
              ) {
                val variable = node.receiver?.tryResolve()
                if (variable != null) {
                  map[variable]?.let {
                    return it
                  }
                }
              }
            }

            return null
          }

          private fun handleLocalOrLambdaInvocations(node: UCallExpression, resolved: PsiMethod?) {
            val psiElement =
              resolved
                ?: node.receiver?.tryResolve()?.let { functions?.get(it) }?.sourcePsi
                ?: return

            val localFunc = findInvokedLambda(psiElement, node, resolved)
            if (
              localFunc != null &&
                localFunc is UVariable &&
                (localFunc.uastInitializer is ULambdaExpression ||
                  localFunc.uastInitializer is UObjectLiteralExpression)
            ) {
              graph.addSuccessor(node, localFunc.uastInitializer)
              lambdaExits?.get(localFunc)?.let {
                pending.clear()
                pending.addAll(it)
              }
            }
          }

          override fun visitCallExpression(node: UCallExpression): Boolean {
            flushPending(node)
            val resolved = node.resolve()
            if (resolved != null) {
              if (callNeverReturns(node)) {
                handleThrow(node, null)
                return super.visitCallExpression(node)
              }
            }

            pending.add(node)

            if (functions != null) {
              handleLocalOrLambdaInvocations(node, resolved)
            }

            if (builder.trackCallThrows) {
              addExceptions(node, node, resolved)
            }

            if (
              (node.valueArguments.size == 1 || node.valueArguments.size == 2) &&
                node.valueArguments.last() is ULambdaExpression &&
                (resolved != null && isScopingFunction(resolved) ||
                  resolved == null && isScopingFunction(node))
            ) {
              // The scoping functions are special: we will *always* flow directly into
              // the lambda and directly back out to the call successor, so draw these
              // edges directly
              for (argument in node.valueArguments) {
                if (argument is ULambdaExpression) {
                  handleLambdaExpression(argument)
                } else {
                  argument.accept(this)
                }
              }
              return true
            } else if (
              node.valueArguments.lastOrNull() is ULambdaExpression &&
                isComposeFunction(resolved) &&
                node.sourcePsi is KtCallExpression
            ) {
              val last = (node.sourcePsi as KtCallExpression).valueArguments.lastOrNull()
              if (last != null && !last.isNamed()) {
                // Visit the other arguments and handle lambdas according to build configuration
                // setting (e.g. include lambda connections based on whether we're in strict mode
                // etc)
                val others = node.valueArguments.subList(0, node.valueArguments.size - 1)
                if (others.isNotEmpty()) {
                  visitCallArguments(node, others)
                }
                // Unconditionally include the last lambda
                handleLambdaExpression(node.valueArguments.last() as ULambdaExpression)
                return true
              }
              visitCallArguments(node)
              return true
            } else if (node.valueArguments.any { it is ULambdaExpression }) {
              // For any other lambdas, don't include the lambdas -- unless the builder
              // is configured to include lambda edges.
              assert(pending.size == 1 && pending[0] == node)
              visitCallArguments(node)
              return true
            } else {
              return super.visitCallExpression(node)
            }
          }

          private fun isComposeFunction(method: PsiMethod?): Boolean {
            method ?: return false
            return method.annotations.any { it.qualifiedName == COMPOSABLE_CLASS }
          }

          private fun visitCallArguments(
            node: UCallExpression,
            arguments: List<UExpression> = node.valueArguments,
          ) {
            if (builder.callLambdaParameters) {
              // For all the lambda arguments, flow from the method into the lambda, and
              // then out of the lambda back into the call:
              for (argument in arguments) {
                if (argument is ULambdaExpression) {
                  pending.clear()
                  pending.add(node)
                  handleLambdaExpression(argument)
                  for (exit in pending) {
                    if (exit != node) {
                      graph.addSuccessor(exit, node)
                    }
                  }
                }
              }

              pending.clear()
              pending.add(node)
            }

            // Visit all the arguments to get normal argument evaluation flow, e.g. if
            // we have foo(bar(), {}, baz(), {}) the control flow will be foo -> bar ->
            // baz. (This will ignore lambda arguments since we've overridden
            // visitLambdaExpression to be a no-op.)
            for (argument in arguments) {
              argument.accept(this)
            }
          }

          override fun afterVisitCallExpression(node: UCallExpression) {}

          /**
           * This visits a lambda expression, but we call this explicitly when suitable, and
           * visitLambdaExpression is a no-op. That way, if the code contains a lambda expression in
           * the middle of somewhere, e.g. `var x = { foo() }` we don't automatically create a flow
           * into the lambda body; this is done carefully from function calls etc. -- see
           * [visitCallExpression].
           */
          private fun handleLambdaExpression(node: ULambdaExpression) {
            flushPending(node)
            pending.add(node)
            node.body.accept(this)
            processPendingJumps(node)
          }

          override fun visitLambdaExpression(node: ULambdaExpression): Boolean {
            // See (and call) handleLambdaExpression instead if you know that execution
            // should flow into it, as in the case of the Kotlin scoping functions fo example;
            // see visitCallExpression.
            return true
          }

          override fun afterVisitLambdaExpression(node: ULambdaExpression) {}

          override fun visitLabeledExpression(node: ULabeledExpression): Boolean {
            flushPending(node)
            pending.add(node)
            node.expression.accept(this)
            return true
          }

          override fun afterVisitLabeledExpression(node: ULabeledExpression) {}

          override fun visitPrefixExpression(node: UPrefixExpression): Boolean {
            if (builder.trackCallThrows) {
              node.asCall()?.let { addExceptions(node, it) }
            }
            return super.visitPrefixExpression(node)
          }

          override fun visitPostfixExpression(node: UPostfixExpression): Boolean {
            if (builder.trackCallThrows) {
              node.asCall()?.let { addExceptions(node, it) }
            }
            return super.visitPostfixExpression(node)
          }

          override fun visitUnaryExpression(node: UUnaryExpression): Boolean {
            if (builder.trackCallThrows) {
              node.asCall()?.let { addExceptions(node, it) }
            }
            return super.visitUnaryExpression(node)
          }

          override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
            if (builder.trackCallThrows) {
              node.asCall()?.let { addExceptions(node, it) }
            }

            flushPending(node)
            pending.add(node)

            node.leftOperand.accept(this)

            val shortCircuit =
              node.operator == UastBinaryOperator.LOGICAL_AND ||
                node.operator == UastBinaryOperator.LOGICAL_OR
            val short = if (shortCircuit) pending.toList() else emptyList()

            node.rightOperand.accept(this)
            pending.addAll(short)

            return true
          }

          override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
            if (builder.trackCallThrows) {
              node.asCall()?.let { addExceptions(node, it) }
            }
            return super.visitArrayAccessExpression(node)
          }

          override fun afterVisitThrowExpression(node: UThrowExpression) {
            flushPending(node)

            val type = node.thrownExpression.getExpressionType()
            handleThrow(node, type)
          }

          private fun handleThrow(node: UElement, type: PsiType?) {
            addThrowingCall(
              node,
              type?.canonicalText?.let(::listOf) ?: builder.getDefaultMethodExceptions(node),
              node.uastParent,
            )
          }

          private fun afterVisitJumpExpression(node: UJumpExpression, isBreak: Boolean = true) {
            flushPending(node)

            val jumpTarget = node.jumpTarget
            if (jumpTarget != null) {
              // Find the common ancestor of the current node and the
              // jump target. If there are any try/finally's between the
              // node and this common target (not including the shared target),
              // the jump needs to go via the finally-block instead.
              val common = findCommonParent(node, jumpTarget)!!
              var curr: UElement = node
              while (curr !== common) {
                val tryExpression = curr
                if (tryExpression is UTryExpression) {
                  val finallyClause = tryExpression.finallyClause
                  if (finallyClause != null) {
                    addThrowingCall(node, listOf(FINALLY_KEY), finallyClause)
                    return
                  }
                }
                curr = curr.uastParent ?: break
              }

              if (isBreak) {
                addJumpTarget(node, jumpTarget)
              } else {
                // UAST seems to duplicate the jump target; therefore,
                // search for the parent loop to make sure we get the correct
                // node identity.
                var n: UElement = node
                var target = jumpTarget
                while (true) {
                  if (n == jumpTarget) {
                    target = n
                    break
                  }
                  n = n.uastParent ?: break
                }
                if (target is UForExpression && target.update != null) {
                  target = target.update
                } else if (target is UDoWhileExpression) {
                  target = target.condition
                }
                graph.addSuccessor(node, target, node.label)
              }
            } else {
              graph.addSuccessor(node, exitMarker, node.label)
            }
          }

          override fun afterVisitReturnExpression(node: UReturnExpression) {
            afterVisitJumpExpression(node)
          }

          override fun afterVisitBreakExpression(node: UBreakExpression) {
            afterVisitJumpExpression(node)
          }

          override fun afterVisitContinueExpression(node: UContinueExpression) {
            afterVisitJumpExpression(node, isBreak = false)
          }

          @Suppress("UnstableApiUsage")
          override fun afterVisitYieldExpression(node: UYieldExpression) {
            afterVisitJumpExpression(node)
            // Unlike other jump expressions we *also* call super since
            // a yield isn't an unconditional jump
            super.afterVisitYieldExpression(node)
          }

          override fun visitForExpression(node: UForExpression): Boolean {
            flushPending(node)
            pending.add(node)

            node.declaration?.accept(this)
            node.condition?.accept(this)
            val conditionExit = pending.toList()
            node.body.accept(this)
            node.update?.accept(this)

            graph.addSuccessor(node.update, node.condition ?: node, "loop")
            node.condition?.let { pending.add(it) }
            pending.clear()

            // Also include any arbitrarily nested break/continue jumps
            processPendingJumps(node)

            pending.addAll(conditionExit)

            return true
          }

          override fun afterVisitForExpression(node: UForExpression) {}

          override fun visitForEachExpression(node: UForEachExpression): Boolean {
            flushPending(node)
            pending.add(node)
            node.body.accept(this)

            // Point back to beginning of loop
            for (element in pending) {
              graph.addSuccessor(element, node, "loop")
            }

            pending.clear()
            pending.add(node)

            // Also include any arbitrarily nested break/continue jumps
            processPendingJumps(node)

            return true
          }

          override fun afterVisitForEachExpression(node: UForEachExpression) {}

          override fun visitWhileExpression(node: UWhileExpression): Boolean {
            flushPending(node)
            pending.add(node)
            node.body.accept(this)

            // Point back to beginning of loop
            for (element in pending) {
              graph.addSuccessor(element, node, "loop")
            }

            pending.clear()
            pending.add(node)

            // Also include any arbitrarily nested break/continue jumps
            processPendingJumps(node)

            return true
          }

          override fun afterVisitWhileExpression(node: UWhileExpression) {}

          override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
            flushPending(node)
            pending.add(node)
            node.body.accept(this)
            node.condition.accept(this)

            // Point back to beginning of loop
            for (element in pending) {
              graph.addSuccessor(element, node, "loop")
            }

            // Also include any arbitrarily nested break/continue jumps
            processPendingJumps(node)

            return true
          }

          override fun afterVisitDoWhileExpression(node: UDoWhileExpression) {}

          override fun visitSwitchExpression(node: USwitchExpression): Boolean {
            flushPending(node)
            pending.add(node)

            val switchExpression = node.expression
            switchExpression?.accept(this)

            val fallthrough = isJava(node.lang)
            val exits = mutableListOf<UElement>()

            val pendingBefore = pending.toMutableList()

            val fallthroughNodes = mutableListOf<UElement>()

            val randomAccess = !isKotlin
            var prevCaseExits: List<UElement> = pendingBefore

            // Link to all case expressions
            var hasDefault = false
            for (expression in node.body.expressions) {
              val clauseExpression = expression as USwitchClauseExpression
              val bodyExpression = clauseExpression as? USwitchClauseExpressionWithBody
              val cases = clauseExpression.caseValues
              if (
                cases.isEmpty() ||
                  // Really want to do "it is JavaUDefaultCaseExpression" but it's
                  // an internal API. So relying on another telltale sign:
                  cases.any { it.asRenderString() == "else" }
              ) {
                hasDefault = true
              }

              val branch =
                if (switchExpression == null && cases.size == 1) {
                  val branch = builder.checkBranchPaths(cases[0])
                  if (branch == FollowBranch.ELSE) {
                    continue
                  }
                  branch
                } else {
                  FollowBranch.BOTH
                }

              pending.clear()

              if (randomAccess) {
                pending.addAll(pendingBefore)
              } else {
                pending.addAll(prevCaseExits)
              }

              for (case in cases) {
                case.accept(this)
              }

              if (!randomAccess) {
                prevCaseExits = pending.toList()
              }

              if (switchExpression == null) {
                assert(!fallthrough)
                pendingBefore.clear()
                pendingBefore.addAll(pending)
              }

              if (bodyExpression != null) {
                pending.addAll(fallthroughNodes)
                fallthroughNodes.clear()
                bodyExpression.accept(this)
              }

              if (fallthrough) {
                fallthroughNodes.addAll(pending)
              } else {
                exits.addAll(pending)
                pending.clear()
              }

              if (branch == FollowBranch.THEN) {
                hasDefault = true
                break
              }
            }

            if (!hasDefault) {
              pending.addAll(pendingBefore)
            }
            pending.addAll(fallthroughNodes)
            pending.addAll(exits)

            afterVisitSwitchExpression(node)
            return true
          }

          override fun afterVisitSwitchExpression(node: USwitchExpression) {
            // Also include any arbitrarily nested break/continue jumps
            processPendingJumps(node)
          }

          override fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean {
            if (node is USwitchClauseExpressionWithBody) {
              node.body.accept(this)
            }
            return true
          }

          override fun afterVisitSwitchClauseExpression(node: USwitchClauseExpression) {}

          override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean {
            // See (and call) handleLambdaExpression instead if you know that execution
            // should flow into it, as in the case of the Kotlin scoping functions fo example;
            // see visitCallExpression.
            return true
          }

          private fun handleObjectLiteralExpression(node: UObjectLiteralExpression) {
            flushPending(node)
            pending.add(node)
            visitCallArguments(node)
            val declaration = node.declaration
            val constructors = declaration.methods.filter { it.isConstructor }
            if (constructors.size == 1) {
              constructors[0].uastBody?.accept(this)
            }
            val methods = declaration.methods.filter { !it.isConstructor }
            if (methods.size == 1) {
              methods[0].uastBody?.accept(this)
            }
          }

          override fun afterVisitObjectLiteralExpression(node: UObjectLiteralExpression) {}

          override fun visitParameter(node: UParameter): Boolean {
            // For example in a catch block
            return true
          }

          override fun afterVisitParameter(node: UParameter) {}

          private fun UQualifiedReferenceExpression.isSafeExpression(): Boolean {
            // Would be better to use
            //   if (node.accessType == KotlinQualifiedExpressionAccessTypes.SAFE)
            // but that API is internal. Could also use accessType.name == "?.".
            return isKotlin && sourcePsi is KtSafeQualifiedExpression
          }

          private fun UElement?.isSafeExpression(): Boolean {
            return isKotlin && this is UQualifiedReferenceExpression && isSafeExpression()
          }

          override fun visitQualifiedReferenceExpression(
            node: UQualifiedReferenceExpression
          ): Boolean {
            flushPending(node)
            pending.add(node)

            if (node.isSafeExpression()) {
              val receiver = node.receiver
              receiver.accept(this)
              var outer: UElement = node
              var curr: UElement = node
              while (true) {
                val parent = curr.uastParent
                if (parent.isSafeExpression()) {
                  outer = parent as UQualifiedReferenceExpression
                  curr = parent
                } else {
                  break
                }
              }
              for (exit in pending) {
                addJumpTarget(exit, outer)
              }
              node.selector.accept(this)
              processPendingJumps(node)

              return true
            }
            return super.visitQualifiedReferenceExpression(node)
          }

          override fun afterVisitQualifiedReferenceExpression(
            node: UQualifiedReferenceExpression
          ) {}

          private fun KtProperty.hasCustomGetter() = getter?.hasBody() ?: false

          private fun KtProperty.hasCustomSetter() = setter?.hasBody() ?: false

          // Skip simple atomic nodes?
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (isKotlin) {
              val resolved = node.resolve()
              if (resolved is PsiMethod) {
                val property = resolved.unwrapped
                if (property is KtProperty) {
                  if (
                    property.hasDelegate() ||
                      property.hasCustomGetter() ||
                      property.hasCustomSetter()
                  ) {
                    val types = builder.methodThrows(node, resolved)
                    if (types != null) {
                      flushPending(node)
                      pending.add(node)
                      addThrowingCall(node, types, node.uastParent)
                    }
                  }
                }
              }
            }

            return true
          }

          override fun afterVisitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ) {}

          override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
            return true
          }

          override fun afterVisitLiteralExpression(node: ULiteralExpression) {}

          override fun afterVisitPolyadicExpression(node: UPolyadicExpression) {
            if (node.operands.size == 1 && node.sourcePsi is KtStringTemplateExpression) {
              return // Ignore the UPolyadicExpression wrapper for KT string literals (KTIJ-27448).
            }
            super.afterVisitPolyadicExpression(node)
          }
        }
      )

      // Finish any pending jump sources; they may jump to outer methods (if we
      // were analyzing a nested method); just point all of these to the exit
      // marker
      for (sources in pendingJumps.values) {
        for (source in sources) {
          graph.addSuccessor(source, exitMarker)
        }
      }

      // Finish any pending exceptions -- these exit out of the method
      for (sources in pendingThrows.values) {
        for ((source, types) in sources) {
          for (type in types.ifEmpty { builder.getDefaultMethodExceptions(method) }) {
            graph.addException(source, exitMarker, type)
          }
        }
      }

      for (prev in pending) {
        graph.addSuccessor(prev, method, "exit") // special exit marker
      }

      return graph
    }

    /**
     * Describes a path through the control flow graph of [UElement]s. Useful utility method for
     * error messages involving the control flow graph.
     */
    fun describePath(path: List<Edge<UElement>>): String {
      val sb = StringBuilder()

      fun describe(node: Node<UElement>): String? {
        if (node.isExit()) {
          return "exit"
        }
        return when (val instruction = node.instruction) {
          is UCallExpression ->
            (instruction.methodName ?: instruction.methodIdentifier?.name)?.let { "$it()" }
          is UReturnExpression -> "return"
          is UThrowExpression -> "throw"
          is UIfExpression -> "if"
          is UBreakExpression -> "break"
          is UContinueExpression -> "continue"
          is UTryExpression -> "try"
          is UBlockExpression -> null
          is UForExpression,
          is UForEachExpression -> "for"
          is ULoopExpression -> "loop"
          is UUnaryExpression -> instruction.operator.text
          is UCatchClause -> "catch"
          is UPolyadicExpression -> instruction.operator.text
          is USwitchExpression -> {
            val identifier = instruction.switchIdentifier.name
            when {
              identifier != "<error>" -> identifier
              isKotlin(instruction.lang) -> "when"
              else -> "switch"
            }
          }
          else -> null
        }
      }

      if (path.isEmpty()) {
        return ""
      }
      val first = path.first()
      describe(first.from)?.let(sb::append)
      for (edge in path) {
        val label = edge.label
        val next = describe(edge.to)
        if (next != null || label != null) {
          if (sb.isNotEmpty()) {
            // Skip some redundant labels
            if (
              label != null &&
                label != next &&
                label != JAVA_LANG_EXCEPTION &&
                label != JAVA_LANG_RUNTIME_EXCEPTION &&
                label != "catch"
            ) {
              sb.append(" → ")
              sb.append(label)
              if (next == null) {
                continue
              }
              sb.append(' ')
            } else if (next == null) {
              continue
            } else {
              sb.append(" → ")
            }
          }
          sb.append(next)
        }
      }

      return sb.toString()
    }
  }
}
