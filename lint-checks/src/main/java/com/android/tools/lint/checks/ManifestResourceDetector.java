/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;

/** Detects references to resources in the manifest that vary by configuration */
public class ManifestResourceDetector extends ResourceXmlDetector {
    /** Using resources in the manifest that vary by configuration */
    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and except "
                            + "for a few specific package attributes such as the application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    new Implementation(ManifestResourceDetector.class, Scope.MANIFEST_SCOPE));

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {}

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (NODE_METADATA.equals(attribute.getOwnerElement().getTagName())) {
            return;
        }
        String value = attribute.getNodeValue();
        if (value.startsWith(PREFIX_RESOURCE_REF) && !isAllowedToVary(attribute)) {
            checkReference(context, attribute, value);
        }
    }

    /**
     * Is the given attribute allowed to reference a resource that has different values across
     * configurations (other than with version qualifiers) ?
     *
     * <p>When the manifest is read, it has a fixed configuration with only the API level set. When
     * strings are read, we can either read the actual string, or a resource reference. For labels
     * and icons, we only read the resource reference -- that is the package manager doesn't need
     * the actual string (like it would need for, say, the name of an activity), but just gets the
     * resource ID, and then clients if they need the actual resource value can load it at that
     * point using their current configuration.
     *
     * <p>To see which specific attributes in the manifest are processed this way, look at
     * android.content.pm.PackageItemInfo to see what pieces of data are kept as raw resource IDs
     * instead of loading their value. (For label resources we also keep the non localized label
     * resource to allow people to specify hardcoded strings instead of a resource reference.)
     *
     * @param attribute the attribute node to look up
     * @return true if this resource is allowed to have delayed configuration values
     */
    private static boolean isAllowedToVary(@NonNull Attr attribute) {
        // This corresponds to the getResourceId() calls in PackageParser
        // where we store the actual resource id such that they can be
        // resolved later
        String name = attribute.getLocalName();
        if (ATTR_LABEL.equals(name)
                || ATTR_ICON.equals(name)
                || ATTR_THEME.equals(name)
                || "description".equals(name)
                || "logo".equals(name)
                || "banner".equals(name)
                || "sharedUserLabel".equals(name)
                || "roundIcon".equals(name)) {
            return ANDROID_URI.equals(attribute.getNamespaceURI());
        }

        return false;
    }

    private static void checkReference(
            @NonNull XmlContext context, @NonNull Attr attribute, @NonNull String value) {
        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && !url.isFramework()) {
            LintClient client = context.getClient();
            Project project = context.getProject();
            ResourceRepository repository = client.getResources(project, PROJECT_ONLY);
            List<ResourceItem> items =
                    repository.getResources(ResourceNamespace.TODO(), url.type, url.name);
            if (items.size() > 1) {
                List<String> list = Lists.newArrayListWithExpectedSize(5);
                for (ResourceItem item : items) {
                    String qualifiers = item.getConfiguration().getQualifierString();
                    // Default folder is okay
                    if (qualifiers.isEmpty()) {
                        continue;
                    }

                    // Version qualifier is okay, as is density qualifiers (or both)
                    int qualifierCount = 1;
                    for (int i = 0, n = qualifiers.length(); i < n; i++) {
                        if (qualifiers.charAt(i) == '-') {
                            qualifierCount++;
                        }
                    }
                    FolderConfiguration configuration = item.getConfiguration();
                    DensityQualifier densityQualifier = configuration.getDensityQualifier();
                    VersionQualifier versionQualifier = configuration.getVersionQualifier();
                    if (qualifierCount == 1
                            && (versionQualifier != null && versionQualifier.isValid()
                                    || densityQualifier != null && densityQualifier.isValid())) {
                        continue;
                    } else if (qualifierCount == 2
                            && densityQualifier != null
                            && densityQualifier.isValid()
                            && versionQualifier != null
                            && versionQualifier.isValid()) {
                        continue;
                    }

                    list.add(qualifiers);
                }
                if (!list.isEmpty()) {
                    Collections.sort(list);
                    String message = getErrorMessage(Joiner.on(", ").join(list));
                    Location location = context.getValueLocation(attribute);

                    // Secondary locations?
                    // Not relevant when running in the IDE and analyzing a single
                    // file; no point highlighting matches in other files (which
                    // will be cleared when you visit them.
                    if (!context.getDriver().isIsolated()) {
                        Location curr = location;
                        for (ResourceItem item : items) {
                            if (!list.contains(item.getConfiguration().getQualifierString())) {
                                continue;
                            }
                            Location secondary =
                                    client.getXmlParser().getValueLocation(client, item);
                            if (secondary != null) {
                                secondary.setMessage("This value will not be used");
                                curr.setSecondary(secondary);
                                curr = secondary;
                            }
                        }
                    }

                    context.report(ISSUE, attribute, location, message);
                }
            }
        }
    }

    @NonNull
    private static String getErrorMessage(@NonNull String qualifiers) {
        return "Resources referenced from the manifest cannot vary by configuration "
                + "(except for version qualifiers, e.g. `-v21`). Found variation in "
                + qualifiers;
    }
}
