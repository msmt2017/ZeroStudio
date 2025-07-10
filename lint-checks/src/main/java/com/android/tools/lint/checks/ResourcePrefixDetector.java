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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_DECLARE_STYLEABLE;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.sdklib.SdkVersionInfo.camelCaseToUnderlines;
import static com.android.sdklib.SdkVersionInfo.underlinesToCamelCase;
import static com.android.utils.SdkUtils.startsWithIgnoreCase;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.model.LintModelModule;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Ensure that resources in Gradle projects which specify a resource prefix conform to the given
 * name
 *
 * <p>TODO: What about id's?
 */
public class ResourcePrefixDetector extends Detector implements BinaryResourceScanner, XmlScanner {
    /** The main issue discovered by this detector */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all end "
                            + "up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.BINARY_RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link ResourcePrefixDetector} */
    public ResourcePrefixDetector() {}

    private String mPrefix;
    private String mUnderlinePrefix;
    private String mCamelPrefix;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_RESOURCES, TAG_DECLARE_STYLEABLE);
    }

    @Nullable
    private static String computeResourcePrefix(@NonNull Project project) {
        LintModelModule model = project.getBuildModule();
        if (model != null) {
            return model.getResourcePrefix();
        } else {
            return null;
        }
    }

    private void updatePrefix(@Nullable Context context) {
        if (context == null) {
            mPrefix = mUnderlinePrefix = mCamelPrefix = null;
        } else {
            mPrefix = computeResourcePrefix(context.getProject());
            if (mPrefix == null) {
                mUnderlinePrefix = mCamelPrefix = null;
            } else if (mPrefix.indexOf('_') != -1) {
                mUnderlinePrefix = mPrefix;
                mCamelPrefix = underlinesToCamelCase(mPrefix);
            } else {
                mCamelPrefix = mPrefix;
                mUnderlinePrefix = camelCaseToUnderlines(mPrefix);
            }
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        updatePrefix(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        updatePrefix(null);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            ResourceFolderType folderType = xmlContext.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String name = Lint.getBaseName(context.file.getName());
                if (!libraryPrefixMatches(mUnderlinePrefix, name)) {
                    // Attempt to report the error on the root tag of the associated
                    // document to make suppressing the error with a tools:suppress
                    // attribute etc possible
                    if (xmlContext.document != null) {
                        Element root = xmlContext.document.getDocumentElement();
                        if (root != null) {
                            xmlContext.report(
                                    ISSUE,
                                    root,
                                    xmlContext.getElementLocation(root),
                                    getErrorMessage(name, folderType));
                            return;
                        }
                    }
                    context.report(
                            ISSUE,
                            Location.create(context.file),
                            getErrorMessage(name, folderType));
                }
            }
        }
    }

    private String getErrorMessage(@NonNull String name, @NonNull ResourceFolderType folderType) {
        assert mPrefix != null && !name.startsWith(mPrefix);
        return String.format(
                "Resource named '`%1$s`' does not start "
                        + "with the project's resource prefix '`%2$s`'; rename to '`%3$s`' ?",
                name, mPrefix, Lint.computeResourceName(mPrefix, name, folderType));
    }

    // --- Implements XmlScanner ----

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        for (Element item : XmlUtils.getSubTags(element)) {
            Attr nameAttribute = item.getAttributeNode(ATTR_NAME);
            if (nameAttribute != null) {
                String name = nameAttribute.getValue();
                if (name.indexOf(':') != -1) {
                    // Don't flag names in other namespaces, such as android:textColor
                    continue;
                }
                if (!libraryPrefixMatches(mUnderlinePrefix, name)
                        && !libraryPrefixMatches(mCamelPrefix, name)) {
                    String message = getErrorMessage(name, ResourceFolderType.VALUES);
                    context.report(
                            ISSUE, nameAttribute, context.getLocation(nameAttribute), message);
                }
            }
        }
    }

    /** Perform a prefix comparison and return true if the prefix matches */
    @VisibleForTesting
    static boolean libraryPrefixMatches(@NonNull String prefix, @NonNull String name) {
        // To allow this matching we perform two conversion
        if (name.startsWith(prefix)) {
            return true;
        }

        // For styleables, allow case insensitive prefix match
        if (startsWithIgnoreCase(name, prefix)) {
            return true;
        }

        // If the prefix ends with a "_" it's not required, e.g. prefix "foo_"
        // should accept FooView as a styleable, and shouldn't require
        // foo_View or Foo_View.
        return prefix.endsWith("_") && name.regionMatches(true, 0, prefix, 0, prefix.length() - 1);
    }

    // ---- Implements BinaryResourceScanner ---

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mUnderlinePrefix != null) {
            ResourceFolderType folderType = context.getResourceFolderType();
            if (folderType != null && folderType != ResourceFolderType.VALUES) {
                String name = Lint.getBaseName(context.file.getName());
                if (!name.startsWith(mUnderlinePrefix)) {
                    // Turns out the Gradle plugin will generate raw resources
                    // for renderscript. We don't want to flag these.
                    // We don't have a good way to recognize them today.
                    String path = context.file.getPath();
                    if (path.endsWith(".bc") && folderType == ResourceFolderType.RAW) {
                        return;
                    }

                    Location location = Location.create(context.file);
                    context.report(ISSUE, location, getErrorMessage(name, folderType));
                }
            }
        }
    }
}
