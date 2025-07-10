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

import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.PREFIX_BINDING_EXPR;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_TWOWAY_BINDING_EXPR;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Joiner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks for missing onClick handlers */
public class OnClickDetector extends LayoutDetector implements SourceCodeScanner {

    /** Missing onClick handlers */
    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context "
                            + "to invoke when the view is clicked. This name must correspond to a public method "
                            + "that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or "
                            + "'\\\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES,
                            Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Location.Handle> names;
    private Map<String, List<String>> similar;

    /** Constructs a new {@link OnClickDetector} */
    public OnClickDetector() {}

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (names != null && !names.isEmpty() && context.getScope().contains(Scope.JAVA_FILE)) {
            List<String> missing = new ArrayList<>(names.keySet());
            Collections.sort(missing);
            LintDriver driver = context.getDriver();
            for (String name : missing) {
                Handle handle = names.get(name);

                Object clientData = handle.getClientData();
                if (clientData instanceof Node) {
                    if (driver.isSuppressed(null, ISSUE, (Node) clientData)) {
                        continue;
                    }
                }

                Location location = handle.resolve();
                String message =
                        String.format(
                                "Corresponding method handler '`public void "
                                        + "%1$s(android.view.View)`' not found",
                                name);
                List<String> matches = similar != null ? similar.get(name) : null;
                if (matches != null) {
                    Collections.sort(matches);
                    message +=
                            String.format(
                                    " (did you mean `%1$s` ?)", Joiner.on(", ").join(matches));
                }
                context.report(ISSUE, location, message);
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Nullable
    private static String validateJavaIdentifier(@NonNull String text) {
        if (Lint.isJavaKeyword(text)) {
            return "cannot be a Java keyword";
        }

        int len = text.length();
        if (len == 0) {
            return "cannot be empty";
        }

        if (!Character.isJavaIdentifierStart(text.charAt(0))) {
            return "cannot start with the character '`" + text.charAt(0) + "`'";
        }

        for (int i = 1; i < len; i++) {
            if (!Character.isJavaIdentifierPart(text.charAt(i))) {
                return "cannot contain the character '`" + text.charAt(i) + "`'";
            }
        }

        return null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Project project = context.getProject();
        if (!project.getReportIssues()) {
            // If this is a library project not being analyzed, ignore it
            return;
        }

        String value = attribute.getValue();
        if (value.isEmpty() || value.trim().isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "`onClick` attribute value cannot be empty");
        } else if (value.startsWith(PREFIX_BINDING_EXPR)
                || value.startsWith(PREFIX_TWOWAY_BINDING_EXPR)) {
            // Data binding: can't evaluate the expression to see if all expression values
            // are valid yet
            //noinspection UnnecessaryReturnStatement
            return;
        } else if (value.contains(" ")) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "There should be no spaces in the `onClick` handler name");
        } else if (!value.startsWith(PREFIX_RESOURCE_REF)) { // Not resolved
            // Replace unicode characters with the actual value since that's how they
            // appear in the method names
            if (value.contains("\\u")) {
                Pattern pattern = Pattern.compile("\\\\u(\\d\\d\\d\\d)");
                Matcher matcher = pattern.matcher(value);
                StringBuilder sb = new StringBuilder(value.length());
                int remainder = 0;
                while (matcher.find()) {
                    sb.append(value.substring(0, matcher.start()));
                    String unicode = matcher.group(1);
                    int hex = Integer.parseInt(unicode, 16);
                    sb.append((char) hex);
                    remainder = matcher.end();
                }
                sb.append(value.substring(remainder));
                value = sb.toString();
            }

            String validationError = validateJavaIdentifier(value);
            if (validationError != null) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "`onClick` handler method name " + validationError);
                return;
            }

            if (names == null) {
                names = new HashMap<>();
            }
            Handle handle = context.createLocationHandle(attribute);
            handle.setClientData(attribute);

            names.put(value, handle);

            if (!context.getScope().contains(Scope.JAVA_FILE)) {
                // Incremental editing: Look to see if we know the immediate activity
                Element root = attribute.getOwnerDocument().getDocumentElement();
                String ctx = root.getAttributeNS(TOOLS_URI, ATTR_CONTEXT);
                if (!ctx.isEmpty()) {
                    if (ctx.startsWith(".") || !ctx.contains(".")) {
                        String pkg = project.getPackage();
                        if (pkg != null) {
                            ctx = pkg + (ctx.startsWith(".") ? "" : ".") + ctx;
                        }
                    }
                    UastParser parser = context.getClient().getUastParser(project);
                    if (parser != null) {
                        JavaEvaluator evaluator = parser.getEvaluator();
                        PsiClass cls = evaluator.findClass(ctx);
                        // The context can only be an Activity (we do not allow fragments or other
                        // holders).
                        if (cls != null && evaluator.extendsClass(cls, CLASS_ACTIVITY, false)) {
                            boolean found = false;
                            PsiMethod[] methods = cls.findMethodsByName(value, true);
                            for (PsiMethod method : methods) {
                                boolean rightArguments =
                                        method.getParameterList().getParametersCount() == 1
                                                && evaluator.parameterHasType(
                                                        method, 0, CLASS_VIEW);
                                if (rightArguments) {
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                String message =
                                        String.format(
                                                "Corresponding method handler "
                                                        + "'`public void %1$s(android.view.View)`' not "
                                                        + "found",
                                                value);
                                context.report(
                                        ISSUE,
                                        attribute,
                                        context.getValueLocation(attribute),
                                        message);
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (names == null) {
            // No onClick attributes in the XML files
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();

        for (PsiMethod method : declaration.getMethods()) {
            // TODO: Remember methods of the same names if they don't have the right arguments?
            String methodName = method.getName();
            boolean rightArguments =
                    method.getParameterList().getParametersCount() == 1
                            && evaluator.parameterHasType(method, 0, CLASS_VIEW);
            if (!names.containsKey(methodName)) {
                if (rightArguments) {
                    // See if there's a possible typo instead
                    for (String n : names.keySet()) {
                        if (Lint.isEditableTo(n, methodName, 2)) {
                            recordSimilar(n, declaration, method);
                            break;
                        }
                    }
                }
                continue;
            }

            if (rightArguments) {
                // Found: remove from list to be checked
                names.remove(methodName);

                // Make sure the method is public
                if (!evaluator.isPublic(method)) {
                    Location location = context.getNameLocation(method);
                    String message =
                            String.format(
                                    "`onClick` handler `%1$s(View)` must be public", methodName);
                    context.report(ISSUE, method, location, message);
                } else if (evaluator.isStatic(method)) {
                    PsiElement locationNode = method;
                    PsiModifierList modifierList = method.getModifierList();
                    // Try to find the static modifier itself
                    if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                        PsiElement child = modifierList.getFirstChild();
                        while (child != null) {
                            if (child instanceof PsiKeyword
                                    && PsiKeyword.STATIC.equals(child.getText())) {
                                locationNode = child;
                                break;
                            }
                            child = child.getNextSibling();
                        }
                    }
                    Location location = context.getLocation(locationNode);
                    String message =
                            String.format(
                                    "`onClick` handler `%1$s(View)` should not be static",
                                    methodName);
                    context.report(ISSUE, method, location, message);
                }

                if (names.isEmpty()) {
                    names = null;
                    return;
                }
            }
        }
    }

    private void recordSimilar(String name, UClass containingClass, PsiMethod method) {
        if (similar == null) {
            similar = new HashMap<>();
        }
        List<String> list = similar.computeIfAbsent(name, k -> new ArrayList<>());
        String signature = containingClass.getName() + '#' + method.getName();
        list.add(signature);
    }
}
