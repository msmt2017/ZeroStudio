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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.CLASS_VIEWGROUP;
import static com.android.tools.lint.client.api.JavaEvaluatorKt.TYPE_INT;
import static com.android.tools.lint.detector.api.Lint.getMethodName;

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USwitchExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Collections;
import java.util.List;

/** Looks for ListView scrolling performance: should use view holder pattern */
public class ViewHolderDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ViewHolderDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Using a view inflater unconditionally in an AdapterView */
    public static final Issue ISSUE =
            Issue.create(
                            "ViewHolder",
                            "View Holder Candidates",
                            "When implementing a view Adapter, you should avoid unconditionally"
                                + " inflating a new layout; if an available item is passed in for"
                                + " reuse, you should try to use that one instead. This helps make"
                                + " for example `ListView` scrolling much smoother.",
                            Category.PERFORMANCE,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/ui/layout/recyclerview#ViewHolder")
                    .setAndroidSpecific(true);

    private static final String GET_VIEW = "getView";
    static final String INFLATE = "inflate";

    /** Constructs a new {@link ViewHolderDetector} check */
    public ViewHolderDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new ViewAdapterVisitor(context);
    }

    private static class ViewAdapterVisitor extends UElementHandler {
        private final JavaContext context;

        public ViewAdapterVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitMethod(@NonNull UMethod method) {
            if (isViewAdapterMethod(context, method.getJavaPsi())) {
                InflationVisitor visitor = new InflationVisitor(context);
                method.accept(visitor);
                visitor.finish();
            }
        }

        /**
         * Returns true if this method looks like it's overriding android.widget.Adapter's getView
         * method: getView(int position, View convertView, ViewGroup parent)
         */
        private static boolean isViewAdapterMethod(JavaContext context, PsiMethod node) {
            JavaEvaluator evaluator = context.getEvaluator();
            return GET_VIEW.equals(node.getName())
                    && evaluator.parametersMatch(node, TYPE_INT, CLASS_VIEW, CLASS_VIEWGROUP);
        }
    }

    private static class InflationVisitor extends AbstractUastVisitor {
        private final JavaContext context;
        private List<UCallExpression> nodes;
        private boolean haveConditional;

        public InflationVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            if (UastExpressionUtils.isMethodCall(node)) {
                checkMethodCall(node);
            }
            return super.visitCallExpression(node);
        }

        private void checkMethodCall(UCallExpression node) {
            UExpression receiver = node.getReceiver();
            //noinspection VariableNotUsedInsideIf
            if (receiver != null) {
                String methodName = getMethodName(node);
                if (INFLATE.equals(methodName) && node.getValueArgumentCount() >= 1) {
                    // See if we're inside a conditional
                    boolean insideIf = false;
                    //noinspection unchecked
                    if (UastUtils.getParentOfType(
                                    node, true, UIfExpression.class, USwitchExpression.class)
                            != null) {
                        insideIf = true;
                        haveConditional = true;
                    }
                    if (!insideIf) {
                        // Rather than reporting immediately, we only report if we didn't
                        // find any conditionally executed inflate statements in the method.
                        // This is because there are cases where getView method is complicated
                        // and inflates not just the top level layout but also children
                        // of the view, and we don't want to flag these. (To be more accurate
                        // should perform flow analysis and only report unconditional inflation
                        // of layouts that wind up flowing to the return value; that requires
                        // more work, and this simple heuristic is good enough for nearly all test
                        // cases I've come across.
                        if (nodes == null) {
                            nodes = Lists.newArrayList();
                        }
                        nodes.add(node);
                    }
                }
            }
        }

        public void finish() {
            if (!haveConditional && nodes != null) {
                for (UCallExpression node : nodes) {
                    String message =
                            "Unconditional layout inflation from view adapter: "
                                    + "Should use View Holder pattern (use recycled view passed "
                                    + "into this method as the second parameter) for smoother "
                                    + "scrolling";
                    context.report(ISSUE, node, context.getLocation(node), message);
                }
            }
        }
    }
}
