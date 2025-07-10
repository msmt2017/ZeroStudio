/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiInlineDocTag
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod

/** Looks for block comments that probably were intended to be javadoc or KDoc comments */
class WrongCommentTypeDetector : Detector(), SourceCodeScanner {
  companion object Issues {

    private val IMPLEMENTATION =
      Implementation(WrongCommentTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "WrongCommentType",
        briefDescription = "Wrong Comment Type",
        explanation =
          """
          This check flags any block comments which look like they had been intended to \
          be KDoc or javadoc comments instead.

          If you really want to use Javadoc-like constructs in a block comment, \
          there's a convention you can use: include `(non-Javadoc)` somewhere in \
          the comment, e.g.
          ```
            /* (non-Javadoc)
             * @see org.xml.sax.helpers.DefaultHandler#setDocumentLocator(org.xml.sax.Locator)
             */
            @Override
            public void setDocumentLocator(Locator locator) {
          ```
          (see https://stackoverflow.com/questions/5172841/non-javadoc-meaning)
          """,
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UClass::class.java, UField::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      private val seen = mutableSetOf<PsiElement>()

      private fun findComment(source: PsiElement): PsiElement? {
        // Find the starting PSI element of the class/method/field to start searching
        // backwards from (across whitespace) to find the preceding comment.
        val begin =
          when (source) {
            is PsiModifierListOwner -> source.modifierList
            is KtParameter -> source.modifierList ?: source
            is KtModifierListOwner -> {
              // In some cases, the KDOC comment is not *before* the modifier
              // list, but *inside* of it. For example, consider the following two
              // source code snippets, which only differ in that the second one has
              // an extra space at the beginning
              //    " /* @test Test */\nclass Test" =>
              //         KtImportList PsiWhiteSpace PsiComment PsiWhiteSpace PsiClass
              //                                                                |
              //                                                         LeafPsiElement("class") ...
              //    "/* @test Test */\nclass Test" =>
              //          KtImportList         PsiClass
              //                            /      |        \
              //                   PsiComment PsiWhiteSpace LeafPsiElement("class") ...
              //
              // Special case this by looking for the comment as the first child; if found
              // we're done:
              if (source.firstChild is PsiComment) {
                return source.firstChild
              } else source
            }
            else -> return null
          }
        begin ?: return null
        var curr = begin.prev()
        while (curr != null) {
          when (curr) {
            is PsiComment -> {
              return curr
            }
            is PsiWhiteSpace -> {
              curr = curr.prev()
            }
            else -> {
              return null
            }
          }
        }
        return null
      }

      private fun isPrivate(element: UElement): Boolean {
        return when (val source = element.sourcePsi) {
          is PsiModifierListOwner -> {
            context.evaluator.isPrivate(source)
          }
          is KtModifierListOwner -> {
            source.modifierList?.hasModifier(KtTokens.PRIVATE_KEYWORD) == true
          }
          else -> false
        }
      }

      private fun checkComment(element: UElement) {
        val source = element.sourcePsi ?: return
        if (!seen.add(source)) {
          // prevent duplicates for things like properties where a single source element maps to
          // multiple UAST elements
          return
        }
        if (isPrivate(element)) {
          return
        }
        val comment = findComment(source) ?: return
        if (comment is PsiDocComment || comment is KDoc) {
          return
        }

        val text = comment.text
        if (!text.startsWith("/*") || text.startsWith("/**")) {
          return
        }
        if (text.contains("(non-Javadoc)")) {
          return
        }
        val tag = getFirstTag(comment, text) ?: return
        val commentType = if (isKotlin(element.getLanguage())) "KDoc" else "javadoc"
        val tagText = tag.text.substringBefore("\n")
        val delta = text.indexOf(tagText)
        val location =
          if (delta != -1) {
            context.getRangeLocation(comment, delta, tagText.length)
          } else {
            context.getLocation(comment)
          }
        context.report(
          ISSUE,
          comment,
          location,
          "This block comment looks like it was intended to be a $commentType comment",
          fix()
            .replace()
            .text("/*")
            .with("/**")
            .range(context.getLocation(comment))
            .autoFix()
            .build(),
        )
      }

      private fun getFirstTag(node: PsiElement, comment: String): PsiElement? {
        if (!comment.contains("@") && !comment.contains("[")) {
          return null
        }
        val content =
          "/**\n" +
            comment.removeSurrounding("/*", "*/").split("\n").joinToString("\n") {
              "* ${it.trim().removePrefix("*").trim()}"
            } +
            "*/"

        try {
          if (isKotlin(node.language)) {
            val docComment = createKDocFromText(node.project, content)

            for (section in docComment.getAllSections()) {
              var curr = section.firstChild ?: return null
              while (true) {
                val tag = curr
                if (tag is KDocTag && isValidTagName(tag.name)) {
                  return tag
                } else if (tag is LeafPsiElement) {
                  val type = tag.elementType
                  if (type == KDocTokens.MARKDOWN_INLINE_LINK) {
                    return tag
                  }
                }
                curr = curr.nextSibling ?: break
              }
            }
            return null
          } else {
            val factory = JavaPsiFacade.getElementFactory(node.project)
            val docComment = factory.createDocCommentFromText(content)
            for (child in docComment.children) {
              if (child is PsiInlineDocTag) {
                val name = child.name
                // TODO: Should we include {@code} too? And what about {@see} ?
                if (name == "link" || name == "linkplain" || name == "inheritDoc") {
                  return child
                }
              }
            }
            // (docComment.tags does not seem to include PsiInlineDocTags, so they're
            // handled separately above)
            return docComment.tags.firstOrNull { isValidTagName(it.name) }
          }
        } catch (ignore: Throwable) {
          // Malformed comments
          if (LintClient.isUnitTest) {
            throw ignore
          }
          return null
        }
      }

      private fun isValidTagName(name: String?): Boolean {
        // Only allow lower cased doc tags -- most tags are, and this
        // avoids false positives on commented out code that contains
        // annotations (such as /** @Deprecated */ which would otherwise
        // look like a doc tag -- thankfully, annotations tend to be
        // capitalized
        name ?: return false
        return name[0].isLowerCase()
      }

      private fun PsiElement.prev(): PsiElement? {
        return prevSibling ?: parent?.prev()
      }

      private tailrec fun UElement.getLanguage(): Language {
        sourcePsi?.language?.let {
          return it
        }
        return uastParent!!.getLanguage()
      }

      override fun visitClass(node: UClass) {
        checkComment(node)
      }

      override fun visitMethod(node: UMethod) {
        checkComment(node)
      }

      override fun visitField(node: UField) {
        checkComment(node)
      }
    }

  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    return true
  }
}

// See org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
fun createKDocFromText(project: Project, text: String): KDoc {
  val fileText = "$text fun foo { }"
  val function = KtPsiFactory(project).createDeclaration<KtFunction>(fileText)
  return PsiTreeUtil.findChildOfType(function, KDoc::class.java)!!
}
