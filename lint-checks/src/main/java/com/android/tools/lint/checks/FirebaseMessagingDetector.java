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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;

public class FirebaseMessagingDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(FirebaseMessagingDetector.class, Scope.JAVA_FILE_SCOPE);

    private static final String FIREBASE_MESSAGING_SERVICE =
            "com.google.firebase.messaging.FirebaseMessagingService";

    public static final Issue MISSING_TOKEN_REFRESH =
            Issue.create(
                            "MissingFirebaseInstanceTokenRefresh",
                            "Missing Firebase Messaging Callback",
                            "Apps that use Firebase Cloud Messaging should implement the "
                                    + "`FirebaseMessagingService#onNewToken()` callback in order to "
                                    + "observe token changes.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://firebase.google.com/docs/cloud-messaging/android/client#monitor-token-generation")
                    .setAndroidSpecific(true);

    /** Constructs a new {@link FirebaseMessagingDetector} */
    public FirebaseMessagingDetector() {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (FIREBASE_MESSAGING_SERVICE.equals(declaration.getQualifiedName())) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if (method.getName().equals("onNewToken")) {
                return;
            }
        }

        context.report(
                MISSING_TOKEN_REFRESH,
                declaration,
                context.getNameLocation(declaration),
                "Apps that use Firebase Cloud Messaging should implement "
                        + "`onNewToken()` in order to observe token changes");
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(FIREBASE_MESSAGING_SERVICE);
    }
}
