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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DEBUGGABLE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

/** Checks for hardcoded debug mode in manifest files */
public class HardcodedDebugModeDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "HardcodedDebugMode",
                            "Hardcoded value of `android:debuggable` in the manifest",
                            "It's best to leave out the `android:debuggable` attribute from the manifest. "
                                    + "If you do, then the tools will automatically insert `android:debuggable=true` when "
                                    + "building an APK to debug on an emulator or device. And when you perform a "
                                    + "release build, such as Exporting APK, it will automatically set it to `false`.\n"
                                    + "\n"
                                    + "If on the other hand you specify a specific value in the manifest file, then "
                                    + "the tools will always use it. This can lead to accidentally publishing "
                                    + "your app with debug information.",
                            Category.SECURITY,
                            5,
                            Severity.FATAL,
                            new Implementation(
                                    HardcodedDebugModeDetector.class, Scope.MANIFEST_SCOPE))
                    .addMoreInfo("https://goo.gle/HardcodedDebugMode");

    /** Constructs a new {@link HardcodedDebugModeDetector} check */
    public HardcodedDebugModeDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singleton(ATTR_DEBUGGABLE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (ANDROID_URI.equals(attribute.getNamespaceURI())) {
            // if (attribute.getOwnerElement().getTagName().equals(TAG_APPLICATION)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Avoid hardcoding the debug mode; leaving it out allows debug and "
                            + "release builds to automatically assign one");
        }
    }
}
