/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_INPUT_METHOD;
import static com.android.SdkConstants.ATTR_INPUT_TYPE;
import static com.android.SdkConstants.ATTR_PASSWORD;
import static com.android.SdkConstants.ATTR_PHONE_NUMBER;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.tools.lint.detector.api.LintFix.TODO;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks for usability problems in text fields: for example, omitting inputType. */
public class TextFieldDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "TextFields",
                    "Missing `inputType`",
                    "Providing an `inputType` attribute on a text field improves usability "
                            + "because depending on the data to be input, optimized keyboards can be shown "
                            + "to the user (such as just digits and parentheses for a phone number). \n"
                            + "\n"
                            + "The lint detector also looks at the `id` of the view, and if the id offers a "
                            + "hint of the purpose of the field (for example, the `id` contains the phrase "
                            + "`phone` or `email`), then lint will also ensure that the `inputType` contains "
                            + "the corresponding type attributes.\n"
                            + "\n"
                            + "If you really want to keep the text field generic, you can suppress this warning "
                            + "by setting `inputType=\"text\"`.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(TextFieldDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link TextFieldDetector} */
    public TextFieldDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node inputTypeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_INPUT_TYPE);
        String inputType = "";
        if (inputTypeNode != null) {
            inputType = inputTypeNode.getNodeValue();
        }

        if (inputTypeNode == null) {
            String style = element.getAttribute(ATTR_STYLE);
            if (style != null && !style.isEmpty()) {
                LintClient client = context.getClient();
                Project project =
                        context.isGlobalAnalysis()
                                ? context.getMainProject()
                                : context.getProject();
                List<ResourceValue> styles =
                        Lint.getStyleAttributes(
                                project, client, style, ANDROID_URI, ATTR_INPUT_TYPE, false);
                if (styles == null) {
                    return;
                }
                if (!styles.isEmpty()) {
                    ResourceValue value = styles.get(0);
                    inputType = value.getValue();
                    inputTypeNode = element;
                }
            }
        }

        if (inputTypeNode == null || TODO.equals(inputType)) {
            // Also make sure the EditText does not set an inputMethod in which case
            // an inputType might be provided from the input.
            if (element.hasAttributeNS(ANDROID_URI, ATTR_INPUT_METHOD)) {
                return;
            }

            LintFix fix = fix().set().todo(ANDROID_URI, ATTR_INPUT_TYPE).build();
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "This text field does not specify an `inputType`",
                    fix);
            return;
        }

        assert inputType != null; // because inputTypeNode check + return above

        Attr idNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idNode == null) {
            return;
        }
        String id = idNode.getValue();
        if (id.isEmpty()) {
            return;
        }
        if (id.startsWith("editText")) {
            // Just the default label
            return;
        }

        // TODO: See if the name is just the default names (button1, editText1 etc)
        // and if so, do nothing
        // TODO: Unit test this

        if (containsWord(id, "phone", true, true)) {
            if (!inputType.contains("phone")
                    && element.getAttributeNodeNS(ANDROID_URI, ATTR_PHONE_NUMBER) == null) {
                String message =
                        String.format(
                                "The view name (`%1$s`) suggests this is a phone "
                                        + "number, but it does not include '`phone`' in the `inputType`",
                                id);
                reportMismatch(context, idNode, inputTypeNode, message);
            }
            return;
        }

        if (containsWord(id, "width", false, true)
                || containsWord(id, "height", false, true)
                || containsWord(id, "size", false, true)
                || containsWord(id, "length", false, true)
                || containsWord(id, "weight", false, true)
                || containsWord(id, "number", false, true)) {
            if (!inputType.contains("number") && !inputType.contains("phone")) {
                String message =
                        String.format(
                                "The view name (`%1$s`) suggests this is a number, "
                                        + "but it does not include a numeric `inputType` (such as '`numberSigned`')",
                                id);
                reportMismatch(context, idNode, inputTypeNode, message);
            }
            return;
        }

        if (containsWord(id, "password", true, true)) {
            if (!(inputType.contains("Password"))
                    && element.getAttributeNodeNS(ANDROID_URI, ATTR_PASSWORD) == null) {
                String message =
                        String.format(
                                "The view name (`%1$s`) suggests this is a password, "
                                        + "but it does not include '`textPassword`' in the `inputType`",
                                id);
                reportMismatch(context, idNode, inputTypeNode, message);
            }
            return;
        }

        if (containsWord(id, "email", true, true)) {
            if (!inputType.contains("Email")) {
                String message =
                        String.format(
                                "The view name (`%1$s`) suggests this is an e-mail "
                                        + "address, but it does not include '`textEmail`' in the `inputType`",
                                id);
                reportMismatch(context, idNode, inputTypeNode, message);
            }
            return;
        }

        if (endsWith(id, "pin", false, true)) {
            if (!(inputType.contains("numberPassword"))
                    && element.getAttributeNodeNS(ANDROID_URI, ATTR_PASSWORD) == null) {
                String message =
                        String.format(
                                "The view name (`%1$s`) suggests this is a password, "
                                        + "but it does not include '`numberPassword`' in the `inputType`",
                                id);
                reportMismatch(context, idNode, inputTypeNode, message);
            }
            return;
        }

        if ((containsWord(id, "uri") || containsWord(id, "url"))
                && !inputType.contains("textUri")) {
            String message =
                    String.format(
                            "The view name (`%1$s`) suggests this is a URI, "
                                    + "but it does not include '`textUri`' in the `inputType`",
                            id);
            reportMismatch(context, idNode, inputTypeNode, message);
        }

        if ((containsWord(id, "date")) && !inputType.contains("date")) {
            String message =
                    String.format(
                            "The view name (`%1$s`) suggests this is a date, "
                                    + "but it does not include '`date`' or '`datetime`' in the `inputType`",
                            id);
            reportMismatch(context, idNode, inputTypeNode, message);
        }
    }

    private static void reportMismatch(
            XmlContext context, Attr idNode, Node inputTypeNode, String message) {
        Location location;
        if (inputTypeNode != null) {
            location = context.getLocation(inputTypeNode);
            Location secondary = context.getLocation(idNode);
            secondary.setMessage("id defined here");
            location.setSecondary(secondary);
        } else {
            location = context.getLocation(idNode);
        }
        context.report(ISSUE, idNode.getOwnerElement(), location, message);
    }

    /** Returns true if the given sentence contains a given word */
    @VisibleForTesting
    static boolean containsWord(String sentence, String word) {
        return containsWord(sentence, word, false, false);
    }

    /**
     * Returns true if the given sentence contains a given word
     *
     * @param sentence the full sentence to search within
     * @param word the word to look for
     * @param allowPrefix if true, allow a prefix match even if the next character is in the same
     *     word (same case or not an underscore)
     * @param allowSuffix if true, allow a suffix match even if the preceding character is in the
     *     same word (same case or not an underscore)
     * @return true if the word is contained in the sentence
     */
    @VisibleForTesting
    static boolean containsWord(
            String sentence, String word, boolean allowPrefix, boolean allowSuffix) {
        return indexOfWord(sentence, word, allowPrefix, allowSuffix) != -1;
    }

    /** Returns true if the given sentence <b>ends</b> with a given word */
    private static boolean endsWith(
            String sentence, String word, boolean allowPrefix, boolean allowSuffix) {
        int index = indexOfWord(sentence, word, allowPrefix, allowSuffix);

        return index != -1 && index == sentence.length() - word.length();
    }

    /**
     * Returns the index of the given word in the given sentence, if any. It will match across
     * cases, and ignore words that seem to be just a substring in the middle of another word.
     *
     * @param sentence the full sentence to search within
     * @param word the word to look for
     * @param allowPrefix if true, allow a prefix match even if the next character is in the same
     *     word (same case or not an underscore)
     * @param allowSuffix if true, allow a suffix match even if the preceding character is in the
     *     same word (same case or not an underscore)
     * @return true if the word is contained in the sentence
     */
    private static int indexOfWord(
            String sentence, String word, boolean allowPrefix, boolean allowSuffix) {
        if (sentence.isEmpty()) {
            return -1;
        }
        int wordLength = word.length();
        if (wordLength > sentence.length()) {
            return -1;
        }

        char firstUpper = Character.toUpperCase(word.charAt(0));
        char firstLower = Character.toLowerCase(firstUpper);

        int start = 0;
        if (sentence.startsWith(NEW_ID_PREFIX)) {
            start += NEW_ID_PREFIX.length();
        } else if (sentence.startsWith(ID_PREFIX)) {
            start += ID_PREFIX.length();
        }

        for (int i = start, n = sentence.length(), m = n - (wordLength - 1); i < m; i++) {
            char c = sentence.charAt(i);
            if (c == firstUpper || c == firstLower) {
                if (sentence.regionMatches(true, i, word, 0, wordLength)) {
                    if (i <= start && allowPrefix) {
                        return i;
                    }
                    if (i == m - 1 && allowSuffix) {
                        return i;
                    }
                    if (i <= start || (sentence.charAt(i - 1) == '_') || Character.isUpperCase(c)) {
                        if (i == m - 1) {
                            return i;
                        }
                        char after = sentence.charAt(i + wordLength);
                        if (after == '_' || Character.isUpperCase(after)) {
                            return i;
                        }
                    }
                }
            }
        }

        return -1;
    }
}
