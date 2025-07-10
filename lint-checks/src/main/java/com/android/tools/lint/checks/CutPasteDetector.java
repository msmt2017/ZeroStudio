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

import static com.android.SdkConstants.RESOURCE_CLZ_ID;
import static com.android.tools.lint.checks.ViewTypeDetector.FIND_VIEW_BY_ID;
import static com.android.tools.lint.checks.ViewTypeDetector.REQUIRE_VIEW_BY_ID;
import static com.android.tools.lint.detector.api.Lint.isKotlin;

import static org.jetbrains.uast.UastUtils.skipParenthesizedExprUp;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.google.common.collect.Maps;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UArrayAccessExpression;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UBlockExpression;
import org.jetbrains.uast.UBreakExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UContinueExpression;
import org.jetbrains.uast.UDoWhileExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UForEachExpression;
import org.jetbrains.uast.UForExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULabeledExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.ULoopExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.USwitchExpression;
import org.jetbrains.uast.UWhileExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Detector looking for cut &amp; paste issues */
public class CutPasteDetector extends Detector implements SourceCodeScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "CutPasteId",
                            "Likely cut & paste mistakes",
                            "This lint check looks for cases where you have cut & pasted calls to"
                                + " `findViewById` but have forgotten to update the R.id field."
                                + " It's possible that your code is simply (redundantly) looking up"
                                + " the field repeatedly, but lint cannot distinguish that from a"
                                + " case where you for example want to initialize fields `prev` and"
                                + " `next` and you cut & pasted `findViewById(R.id.prev)` and"
                                + " forgot to update the second initialization to `R.id.next`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            new Implementation(CutPasteDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    private UMethod lastMethod;
    private Map<String, UCallExpression> ids;
    private Map<String, String> lhs;
    private Map<String, String> callOperands;

    /** Constructs a new {@link CutPasteDetector} check */
    public CutPasteDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(FIND_VIEW_BY_ID, REQUIRE_VIEW_BY_ID);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod calledMethod) {
        // If it's in a comparison, don't do anything
        UElement callParent = skipParenthesizedExprUp(call.getUastParent());
        if (callParent instanceof UBinaryExpression
                && !UastExpressionUtils.isAssignment(callParent)) {
            return;
        }

        String leftSide = getLhs(call);
        if (leftSide == null) {
            return;
        }

        UMethod method = UastUtils.getParentOfType(call, UMethod.class, false);
        if (method == null) {
            return; // prevent doing the same work for multiple findViewById calls in same method
        } else if (method != lastMethod) {
            ids = Maps.newHashMap();
            lhs = Maps.newHashMap();
            callOperands = Maps.newHashMap();
            lastMethod = method;
        }

        String callOperand = call.getReceiver() != null ? call.getReceiver().asSourceString() : "";

        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }
        UExpression first = arguments.get(0);
        if (first instanceof UReferenceExpression) {
            UReferenceExpression psiReferenceExpression = (UReferenceExpression) first;
            String id = psiReferenceExpression.getResolvedName();
            if (id == null) {
                if (psiReferenceExpression.getReferenceNameElement() != null) {
                    // Directly fall back to the unqualified selector, which is what 'resolvedName'
                    // would ever return anyway.  This checker assumes that due to resource merging,
                    // pkg1.R.id.foo is equivalent to pkg2.R.id.foo.
                    id = psiReferenceExpression.getReferenceNameElement().asRenderString();
                } else {
                    // Give up to avoid false positives.
                    return;
                }
            }

            UElement operand =
                    (first instanceof UQualifiedReferenceExpression)
                            ? ((UQualifiedReferenceExpression) first).getReceiver()
                            : null;
            if (operand instanceof UReferenceExpression) {
                UReferenceExpression type = (UReferenceExpression) operand;
                if (RESOURCE_CLZ_ID.equals(type.getResolvedName())) {
                    if (ids.containsKey(id)) {
                        if (leftSide.equals(this.lhs.get(id))) {
                            return;
                        }
                        if (!callOperand.equals(callOperands.get(id))) {
                            return;
                        }
                        UCallExpression earlierCall = ids.get(id);
                        if (!isReachableFrom(method, earlierCall, call)) {
                            return;
                        }
                        Location location = context.getLocation(call);
                        Location secondary = context.getLocation(earlierCall);
                        secondary.setMessage("First usage here");
                        location.setSecondary(secondary);
                        context.report(
                                ISSUE,
                                call,
                                location,
                                String.format(
                                        "The id `%1$s` has already been looked up in this method;"
                                            + " possible cut & paste error?",
                                        first.asSourceString()));
                    } else {
                        ids.put(id, call);
                        lhs.put(id, leftSide);
                        callOperands.put(id, callOperand);
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        ids = null;
        lhs = null;
        callOperands = null;
        lastMethod = null;
    }

    @Nullable
    private static String getLhs(@NonNull UCallExpression call) {
        UElement parent = call.getUastParent();
        while (parent != null && !(parent instanceof UBlockExpression)) {
            if (parent instanceof ULocalVariable) {
                return ((ULocalVariable) parent).getName();
            } else if (UastExpressionUtils.isAssignment(parent)) {
                UExpression left = ((UBinaryExpression) parent).getLeftOperand();
                if (left instanceof UReferenceExpression) {
                    return left.asSourceString();
                } else if (left instanceof UArrayAccessExpression) {
                    UArrayAccessExpression aa = (UArrayAccessExpression) left;
                    return aa.getReceiver().asSourceString();
                }
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    static boolean isReachableFrom(
            @NonNull UMethod method, @NonNull UElement from, @NonNull UElement to) {
        ReachabilityVisitor visitor = new ReachabilityVisitor(from, to);
        method.accept(visitor);
        return visitor.isReachable();
    }

    private static class ReachabilityVisitor extends AbstractUastVisitor {

        private final UElement from;
        private final UElement target;

        private boolean isFromReached;
        private boolean isTargetReachable;
        private boolean isFinished;

        private UExpression breakedExpression;
        private UExpression continuedExpression;

        ReachabilityVisitor(UElement from, UElement target) {
            this.from = from;
            this.target = target;
        }

        @Override
        public boolean visitElement(UElement node) {
            if (isFinished || breakedExpression != null || continuedExpression != null) {
                return true;
            }

            if (node.equals(from)) {
                isFromReached = true;
            }

            if (node.equals(target)) {
                isFinished = true;
                if (isFromReached) {
                    isTargetReachable = true;
                }
                return true;
            }

            if (isFromReached) {
                if (node instanceof UReturnExpression) {
                    isFinished = true;
                } else if (node instanceof UBreakExpression) {
                    breakedExpression = getBreakedExpression((UBreakExpression) node);
                } else if (node instanceof UContinueExpression) {
                    UExpression expression = getContinuedExpression((UContinueExpression) node);
                    if (expression != null && UastUtils.isUastChildOf(target, expression, false)) {
                        isTargetReachable = true;
                        isFinished = true;
                    } else {
                        continuedExpression = expression;
                    }
                } else if (UastUtils.isUastChildOf(target, node, false)) {
                    isTargetReachable = true;
                    isFinished = true;
                }
                return true;
            } else {
                if (node instanceof UIfExpression) {
                    UIfExpression ifExpression = (UIfExpression) node;

                    ifExpression.getCondition().accept(this);

                    boolean isFromReached = this.isFromReached;

                    UExpression thenExpression = ifExpression.getThenExpression();
                    if (thenExpression != null) {
                        thenExpression.accept(this);
                    }

                    UExpression elseExpression = ifExpression.getElseExpression();
                    if (elseExpression != null && isFromReached == this.isFromReached) {
                        elseExpression.accept(this);
                    }
                    return true;
                } else if (node instanceof ULoopExpression) {
                    visitLoopExpressionHeader(node);
                    boolean isFromReached = this.isFromReached;

                    ((ULoopExpression) node).getBody().accept(this);

                    if (isFromReached != this.isFromReached
                            && UastUtils.isUastChildOf(target, node, false)) {
                        isTargetReachable = true;
                        isFinished = true;
                    }
                    return true;
                } else if (node instanceof USwitchExpression) {
                    USwitchExpression switchExpression = (USwitchExpression) node;
                    UExpression expression = switchExpression.getExpression();
                    if (expression != null) {
                        expression.accept(this);
                    }
                    boolean isFromReached = this.isFromReached;
                    boolean isKotlin = isKotlin(switchExpression.getSourcePsi());

                    for (UExpression caseExpression : switchExpression.getBody().getExpressions()) {
                        boolean fallthrough = !isKotlin && this.breakedExpression == null;
                        if (fallthrough || isFromReached == this.isFromReached) {
                            caseExpression.accept(this);
                        }
                    }

                    if (node.equals(breakedExpression)) {
                        breakedExpression = null;
                    }
                    return true;
                }
            }

            return false;
        }

        @Override
        public void afterVisitElement(UElement node) {
            if (node.equals(breakedExpression)) {
                breakedExpression = null;
            } else if (node.equals(continuedExpression)) {
                continuedExpression = null;
            }
        }

        private void visitLoopExpressionHeader(UElement node) {
            if (node instanceof UWhileExpression) {
                ((UWhileExpression) node).getCondition().accept(this);
            } else if (node instanceof UDoWhileExpression) {
                ((UDoWhileExpression) node).getCondition().accept(this);
            } else if (node instanceof UForExpression) {
                UForExpression forExpression = (UForExpression) node;

                if (forExpression.getDeclaration() != null) {
                    forExpression.getDeclaration().accept(this);
                }

                if (forExpression.getCondition() != null) {
                    forExpression.getCondition().accept(this);
                }

                if (forExpression.getUpdate() != null) {
                    forExpression.getUpdate().accept(this);
                }
            } else if (node instanceof UForEachExpression) {
                UForEachExpression forEachExpression = (UForEachExpression) node;
                forEachExpression.getForIdentifier().accept(this);
                forEachExpression.getIteratedValue().accept(this);
            }
        }

        private static UExpression getBreakedExpression(UBreakExpression node) {
            UElement parent = node.getUastParent();
            String label = node.getLabel();
            while (parent != null) {
                if (label != null) {
                    if (parent instanceof ULabeledExpression) {
                        ULabeledExpression labeledExpression = (ULabeledExpression) parent;
                        if (labeledExpression.getLabel().equals(label)) {
                            return labeledExpression.getExpression();
                        }
                    }
                } else {
                    if (parent instanceof ULoopExpression || parent instanceof USwitchExpression) {
                        return (UExpression) parent;
                    }
                }
                parent = parent.getUastParent();
            }
            return null;
        }

        private static UExpression getContinuedExpression(UContinueExpression node) {
            UElement parent = node.getUastParent();
            String label = node.getLabel();
            while (parent != null) {
                if (label != null) {
                    if (parent instanceof ULabeledExpression) {
                        ULabeledExpression labeledExpression = (ULabeledExpression) parent;
                        if (labeledExpression.getLabel().equals(label)) {
                            return labeledExpression.getExpression();
                        }
                    }
                } else {
                    if (parent instanceof ULoopExpression) {
                        return (UExpression) parent;
                    }
                }
                parent = parent.getUastParent();
            }
            return null;
        }

        public boolean isReachable() {
            return isTargetReachable;
        }
    }
}
