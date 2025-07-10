/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;

/** Detector that looks for leaked credentials in strings. */
public class StringAuthLeakDetector extends Detector implements SourceCodeScanner {

    /** Looks for hidden code */
    public static final Issue AUTH_LEAK =
            Issue.create(
                            "AuthLeak",
                            "Code might contain an auth leak",
                            "Strings in java apps can be discovered by decompiling apps, this lint check looks "
                                    + "for code which looks like it may contain an url with a username and password",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(StringAuthLeakDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/AuthLeak");

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(ULiteralExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new AuthLeakChecker(context);
    }

    private static class AuthLeakChecker extends UElementHandler {
        private static final String LEGAL_CHARS = "([\\w_.!~*\'()%;&=+$,-]+)"; // From RFC 2396
        private static final Pattern AUTH_REGEXP =
                Pattern.compile(
                        "([\\w+.-]+)://" + LEGAL_CHARS + ':' + LEGAL_CHARS + '@' + LEGAL_CHARS);

        private final JavaContext context;

        private AuthLeakChecker(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitLiteralExpression(@NonNull ULiteralExpression node) {
            if (node.getValue() instanceof String) {
                String str = (String) node.getValue();
                if (str.length() > 512) {
                    // Java regex matching can be very slow, so abort if the string is too long.
                    // See https://swtch.com/~rsc/regexp/regexp1.html for details.
                    return;
                }
                Matcher matcher = AUTH_REGEXP.matcher(str);
                if (matcher.find()) {
                    String password = matcher.group(3);
                    if (password == null) {
                        return;
                    }
                    if (password.startsWith("%") && password.endsWith("s")) {
                        // Make sure it really looks like a formatting string and isn't actually a
                        // password that happens to start with % and end with s
                        Matcher format = StringFormatDetector.FORMAT.matcher(password);
                        if (format.matches()) {
                            return;
                        }
                    }
                    Location location =
                            context.getRangeLocation(
                                    node, matcher.start() + 1, matcher.end() - matcher.start());
                    context.report(AUTH_LEAK, node, location, "Possible credential leak");
                }
            }
        }
    }
}
