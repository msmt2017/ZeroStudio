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

package com.android.tools.lint.checks;

import static com.android.utils.CharSequences.regionMatches;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.GradleScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.CharSequences;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UComment;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UFile;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Looks for issues in Java or Kotlin comments */
public class CommentDetector extends ResourceXmlDetector
        implements SourceCodeScanner, GradleScanner {
    private static final String STOPSHIP_COMMENT = "STOPSHIP";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    CommentDetector.class,
                    EnumSet.of(
                            Scope.RESOURCE_FILE,
                            Scope.JAVA_FILE,
                            Scope.GRADLE_FILE,
                            Scope.MANIFEST,
                            Scope.PROPERTY_FILE),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.GRADLE_SCOPE,
                    Scope.MANIFEST_SCOPE,
                    Scope.PROPERTY_SCOPE);

    /** Looks for hidden code */
    public static final Issue EASTER_EGG =
            Issue.create(
                            "EasterEgg",
                            "Code contains easter egg",
                            "An \"easter egg\" is code deliberately hidden in the code, both from"
                                + " potential users and even from other developers. This lint check"
                                + " looks for code which looks like it may be hidden from sight.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    // necessary because the scope (resource file) implies Android
                    .setAndroidSpecific(false)
                    .setEnabledByDefault(false);

    /** Looks for special comment markers intended to stop shipping the code */
    public static final Issue STOP_SHIP =
            Issue.create(
                            "StopShip",
                            "Code contains `STOPSHIP` marker",
                            "Using the comment `// STOPSHIP` can be used to flag code that is"
                                + " incomplete but checked in. This comment marker can be used to"
                                + " indicate that the code should not be shipped until the issue is"
                                + " addressed, and lint will look for these. In Gradle projects,"
                                + " this is only checked for non-debug (release) builds.\n"
                                + "\n"
                                + "In Kotlin, the `TODO()` method is also treated as a stop ship"
                                + " marker; you can use it to make incomplete code compile, but it"
                                + " will throw an exception at runtime and therefore should be"
                                + " fixed before shipping releases.",
                            Category.CORRECTNESS,
                            10,
                            Severity.FATAL,
                            IMPLEMENTATION)
                    // necessary because the scope (resource file) implies Android
                    .setAndroidSpecific(false)
                    .setEnabledByDefault(false);

    private static final String ESCAPE_STRING = "\\u002a\\u002f";

    /** Constructs a new {@linkplain CommentDetector} check */
    public CommentDetector() {}

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UFile.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new CommentChecker(context);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        checkXml(context, document);
    }

    private static void checkXml(@NonNull XmlContext context, Node node) {
        if (node.getNodeType() == Node.COMMENT_NODE) {
            String source = node.getNodeValue();
            checkComment(context, node, source, 0, 0, source.length());
        }

        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            checkXml(context, children.item(i));
        }
    }

    private static void checkComment(
            @NonNull Context context,
            @Nullable Object node,
            @NonNull CharSequence source,
            @SuppressWarnings("SameParameterValue") int offset,
            @SuppressWarnings("SameParameterValue") int start,
            int end) {
        char prev = 0;
        char c;
        for (int i = start; i < end - 2; i++, prev = c) {
            c = source.charAt(i);
            if (prev == '\\') {
                if (c == 'u' || c == 'U') {
                    if (regionMatches(
                            source, true, i - 1, ESCAPE_STRING, 0, ESCAPE_STRING.length())) {
                        String message =
                                "Code might be hidden here; found unicode escape sequence which is"
                                        + " interpreted as comment end, compiled code follows";
                        if (context instanceof JavaContext && node instanceof UElement) {
                            JavaContext javaContext = (JavaContext) context;
                            UElement javaNode = (UElement) node;
                            if (Lint.isKotlin(javaNode.getSourcePsi())) {
                                // The Kotlin compiler does not interpret unicode escapes in the
                                // same way as javac which leads to the escape mechanism
                                continue;
                            }
                            Location location =
                                    javaContext.getRangeLocation(
                                            javaNode, offset + i - 1, ESCAPE_STRING.length());
                            javaContext.report(EASTER_EGG, javaNode, location, message);
                        } else if (context instanceof XmlContext && node instanceof Node) {
                            XmlContext xmlContext = (XmlContext) context;
                            Node xmlNode = (Node) node;
                            Location location =
                                    xmlContext.getLocation(xmlNode, i, i + ESCAPE_STRING.length());
                            xmlContext.report(EASTER_EGG, xmlNode, location, message);
                        } else {
                            // Plain location
                            Location location =
                                    Location.create(
                                            context.file,
                                            source,
                                            i - 1,
                                            i - 1 + ESCAPE_STRING.length());
                            context.report(STOP_SHIP, location, message);
                        }
                    }
                } else {
                    i++;
                }
            } else if (prev == 'S'
                    && c == 'T'
                    && regionMatches(
                            source, i - 1, STOPSHIP_COMMENT, 0, STOPSHIP_COMMENT.length())) {

                String message =
                        "`STOPSHIP` comment found; points to code which must be fixed prior "
                                + "to release";
                Incident incident;
                if (context instanceof JavaContext && node instanceof UElement) {
                    JavaContext javaContext = (JavaContext) context;
                    UElement javaNode = (UElement) node;
                    Location location =
                            javaContext.getRangeLocation(
                                    javaNode, offset + i - 1, STOPSHIP_COMMENT.length());
                    LintFix fix = createRemoveStopShipFix();
                    incident = new Incident(STOP_SHIP, javaNode, location, message, fix);
                } else if (context instanceof XmlContext && node instanceof Node) {
                    XmlContext xmlContext = (XmlContext) context;
                    Node xmlNode = (Node) node;
                    Location location =
                            xmlContext.getLocation(xmlNode, i, i + STOPSHIP_COMMENT.length());
                    LintFix fix = createRemoveStopShipFix();
                    incident = new Incident(STOP_SHIP, xmlNode, location, message, fix);
                } else {
                    // Plain location
                    Location location =
                            Location.create(
                                    context.file,
                                    source,
                                    i - 1,
                                    i + -1 + STOPSHIP_COMMENT.length());
                    LintFix fix = createRemoveStopShipFix();
                    incident = new Incident(STOP_SHIP, location, message, fix);
                }
                context.report(incident, new LintMap());
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        // Only flag this issue in release mode?? (but in the IDE, always
        // flag it)
        if (!context.getDriver().isIsolated()) {
            Boolean releaseMode = getReleaseMode(context);
            return releaseMode != Boolean.FALSE;
        } else {
            return true;
        }
    }

    @NonNull
    private static LintFix createRemoveStopShipFix() {
        // TODO: Remove comment if that's all that remains
        return LintFix.create()
                .name("Remove STOPSHIP")
                .replace()
                .pattern("(\\s*STOPSHIP)")
                .with("")
                .build();
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("TODO");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        String message =
                "`TODO` call found; points to code which must be fixed prior " + "to release";
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String fqName = containingClass.getQualifiedName();
        // See libraries/stdlib/jvm/build/stdlib-declarations.json
        if (!"kotlin.StandardKt__StandardKt".equals(fqName)
                && !"kotlin.StandardKt".equals(fqName)) {
            return;
        }

        Location location = context.getLocation(node);
        LintFix fix = fix().name("Remove TODO").replace().all().with("").reformat(true).build();
        context.report(STOP_SHIP, node, location, message, fix);
    }

    /**
     * Returns true iff the current variant is a release build. Returns null if we don't know (e.g.
     * it's not a Gradle project, or we could not obtain a Gradle model.)
     */
    @Nullable
    private static Boolean getReleaseMode(@NonNull Context context) {
        Project project = context.getMainProject();
        LintModelVariant variant = project.getBuildVariant();
        if (variant != null) {
            return !variant.getDebuggable();
        }

        return null;
    }

    // Gradle: Don't attempt to visit the file

    @Override
    public boolean getCustomVisitor() {
        return true;
    }

    @Override
    public void visitBuildScript(@NonNull GradleContext context) {
        JavaContext ktsContext = context.getKtsContext();
        if (ktsContext != null) {
            // Kts files
            UFile file = ktsContext.getUastFile();
            if (file != null) {
                new CommentChecker(ktsContext).visitFile(file);
            }
        } else {
            // Groovy: just do simple text scanning for block and line comments
            CharSequence contents = context.getContents();
            if (contents == null) {
                return;
            }
            int length = contents.length();
            final int STATE_INITIAL = 1;
            final int STATE_SLASH = 2;
            final int STATE_LINE_COMMENT = 3;
            final int STATE_BLOCK_COMMENT = 4;
            int state = STATE_INITIAL;
            int offset = 0;
            int startComment = 0;
            while (offset < length) {
                char c = contents.charAt(offset);
                switch (state) {
                    case STATE_INITIAL:
                        {
                            if (c == '/') {
                                state = STATE_SLASH;
                            }
                            offset++;
                            continue;
                        }
                    case STATE_SLASH:
                        {
                            if (c == '/') {
                                startComment = offset + 1;
                                state = STATE_LINE_COMMENT;
                            } else if (c == '*') {
                                startComment = offset + 1;
                                state = STATE_BLOCK_COMMENT;
                            } else {
                                state = STATE_INITIAL;
                                continue;
                            }
                            offset++;
                            continue;
                        }
                    case STATE_LINE_COMMENT:
                        {
                            if (c == '\n') {
                                checkComment(context, null, contents, 0, startComment, offset);
                                state = STATE_INITIAL;
                            }
                            offset++;
                            continue;
                        }
                    case STATE_BLOCK_COMMENT:
                        {
                            if (c == '*'
                                    && offset < contents.length() - 1
                                    && contents.charAt(offset + 1) == '/') {
                                checkComment(context, null, contents, 0, startComment, offset);
                                state = STATE_INITIAL;
                                offset += 2;
                                continue;
                            }
                            offset++;
                            continue;
                        }
                    default:
                        assert false : state;
                }
            }
            if (state == STATE_LINE_COMMENT) {
                // File ended with a line comment
                checkComment(context, null, contents, 0, startComment, length);
            }
        }
    }

    // Property files
    @Override
    public void run(@NonNull Context context) {
        CharSequence content = context.getContents();
        if (content == null) {
            return;
        }
        int lineBegin = 0;
        int length = content.length();
        while (lineBegin < length) {
            int lineEnd = CharSequences.indexOf(content, '\n', lineBegin);
            if (lineEnd == -1) {
                lineEnd = length;
            }
            for (int i = lineBegin; i < lineEnd; i++) {
                char c = content.charAt(i);
                if (!Character.isWhitespace(c)) {
                    if (c == '#' || c == '!') {
                        checkComment(context, null, content, 0, lineBegin, lineEnd);
                    }
                    break;
                }
            }
            lineBegin = lineEnd + 1;
        }
    }

    private static class CommentChecker extends UElementHandler {
        private final JavaContext mContext;

        CommentChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitFile(@NonNull UFile node) {
            for (UComment comment : node.getAllCommentsInFile()) {
                String contents = comment.getText();
                checkComment(mContext, comment, contents, 0, 0, contents.length());
            }
        }
    }
}
