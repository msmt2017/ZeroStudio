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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_STRING_ARRAY;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks for arrays with inconsistent item counts */
public class ArraySizeDetector extends ResourceXmlDetector {

    /** Are there differences in how many array elements are declared? */
    public static final Issue INCONSISTENT =
            Issue.create(
                    "InconsistentArrays",
                    "Inconsistencies in array element counts",
                    "When an array is translated in a different locale, it should normally have "
                            + "the same number of elements as the original array. When adding or removing "
                            + "elements to an array, it is easy to forget to update all the locales, and this "
                            + "lint warning finds inconsistencies like these.\n"
                            + "\n"
                            + "Note however that there may be cases where you really want to declare a "
                            + "different number of array items in each configuration (for example where "
                            + "the array represents available options, and those options differ for "
                            + "different layout orientations and so on), so use your own judgment to "
                            + "decide if this is really an error.\n"
                            + "\n"
                            + "You can suppress this error type if it finds false errors in your project.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Multimap<File, Pair<String, Integer>> mFileToArrayCount;

    /** Locations for each array name. Populated during phase 2, if necessary */
    private Map<String, Location> mLocations;

    /** Error messages for each array name. Populated during phase 2, if necessary */
    private Map<String, String> mDescriptions;

    /** Constructs a new {@link ArraySizeDetector} */
    public ArraySizeDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mFileToArrayCount = ArrayListMultimap.create(30, 5);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            boolean haveAllResources = context.getScope().contains(Scope.ALL_RESOURCE_FILES);
            if (!haveAllResources) {
                return;
            }

            // Check that all arrays for the same name have the same number of translations

            LintClient client = context.getClient();
            Set<String> alreadyReported = new HashSet<>();
            Map<String, Integer> countMap = new HashMap<>();
            Map<String, File> fileMap = new HashMap<>();

            // Process the file in sorted file order to ensure stable output
            List<File> keys = new ArrayList<>(mFileToArrayCount.keySet());
            Collections.sort(keys);

            for (File file : keys) {
                Collection<Pair<String, Integer>> pairs = mFileToArrayCount.get(file);
                for (Pair<String, Integer> pair : pairs) {
                    String name = pair.getFirst();

                    if (alreadyReported.contains(name)) {
                        continue;
                    }
                    Integer count = pair.getSecond();

                    Integer current = countMap.get(name);
                    if (current == null) {
                        countMap.put(name, count);
                        fileMap.put(name, file);
                    } else if (!count.equals(current)) {
                        alreadyReported.add(name);

                        if (mLocations == null) {
                            mLocations = new HashMap<>();
                            mDescriptions = new HashMap<>();
                        }
                        mLocations.put(name, null);

                        String thisName = Lint.getFileNameWithParent(client, file);
                        File otherFile = fileMap.get(name);
                        String otherName = Lint.getFileNameWithParent(client, otherFile);
                        String message =
                                String.format(
                                        Locale.ROOT,
                                        "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                                        name,
                                        count,
                                        thisName,
                                        current,
                                        otherName);
                        mDescriptions.put(name, message);
                    }
                }
            }

            //noinspection VariableNotUsedInsideIf
            if (mLocations != null) {
                // Request another scan through the resources such that we can
                // gather the actual locations
                context.getDriver().requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
            mFileToArrayCount = null;
        } else {
            if (mLocations != null) {
                List<String> names = new ArrayList<>(mLocations.keySet());
                Collections.sort(names);
                nameLoop:
                for (String name : names) {
                    Location location = mLocations.get(name);
                    if (location == null) {
                        // Suppressed; see visitElement
                        continue;
                    }
                    // We were prepending locations, but we want to prefer the base folders
                    location = Location.reverse(location);

                    // Make sure we still have a conflict, in case one or more of the
                    // elements were marked with tools:ignore
                    int count = -1;
                    LintDriver driver = context.getDriver();
                    boolean foundConflict = false;
                    Location curr;
                    for (curr = location; curr != null; curr = curr.getSecondary()) {
                        Object clientData = curr.getClientData();
                        if (clientData instanceof Node) {
                            Node node = (Node) clientData;
                            if (driver.isSuppressed(null, INCONSISTENT, node)) {
                                continue nameLoop;
                            }
                            int newCount = Lint.getChildCount(node);
                            if (newCount != count) {
                                if (count == -1) {
                                    count = newCount; // first number encountered
                                } else {
                                    foundConflict = true;
                                    break;
                                }
                            }
                        } else {
                            foundConflict = true;
                            break;
                        }
                    }

                    // Through one or more tools:ignore, there is no more conflict so
                    // ignore this element
                    if (!foundConflict) {
                        continue;
                    }

                    String message = mDescriptions.get(name);
                    context.report(INCONSISTENT, location, message);
                }
            }

            mLocations = null;
            mDescriptions = null;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int phase = context.getPhase();

        Attr attribute = element.getAttributeNode(ATTR_NAME);
        if (attribute == null || attribute.getValue().isEmpty()) {
            if (phase != 1) {
                return;
            }
            context.report(
                    INCONSISTENT,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Missing name attribute in `%1$s` declaration", element.getTagName()));
        } else {
            String name = attribute.getValue();
            if (phase == 1) {
                if (context.getProject().getReportIssues()) {
                    int childCount = Lint.getChildCount(element);

                    if (!context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                        incrementalCheckCount(context, element, name, childCount);
                        return;
                    }

                    mFileToArrayCount.put(context.file, Pair.of(name, childCount));
                }
            } else {
                assert phase == 2;
                if (mLocations.containsKey(name)) {
                    Location location = context.getLocation(element);
                    location.setData(element);
                    location.setMessage(
                            String.format(
                                    Locale.ROOT,
                                    "Declaration with array size (%1$d)",
                                    Lint.getChildCount(element)));
                    location.setSecondary(mLocations.get(name));
                    mLocations.put(name, location);
                }
            }
        }
    }

    private static void incrementalCheckCount(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String name,
            int childCount) {
        LintClient client = context.getClient();
        // This method should never be called in partial analysis mode
        assert context.isGlobalAnalysis();
        Project project = context.getMainProject();
        ResourceRepository resources = client.getResources(project, LOCAL_DEPENDENCIES);
        List<ResourceItem> items =
                resources.getResources(ResourceNamespace.TODO(), ResourceType.ARRAY, name);
        for (ResourceItem item : items) {
            PathString source = item.getSource();
            if (source != null && Lint.isSameResourceFile(context.file, source.toFile())) {
                continue;
            }
            ResourceValue rv = item.getResourceValue();
            if (rv instanceof ArrayResourceValue) {
                ArrayResourceValue arv = (ArrayResourceValue) rv;
                if (childCount != arv.getElementCount()) {
                    // We found an item with a different child count than the current one.
                    // That's an error. But resource repositories aren't all sorted, so
                    // let's sort first to make sure we have a stable/predictable result:
                    for (ResourceItem res :
                            items.stream()
                                    .sorted(Comparator.comparing(Configurable::getConfiguration))
                                    .collect(Collectors.toList())) {
                        ResourceValue resValue = res.getResourceValue();
                        if (resValue instanceof ArrayResourceValue) {
                            ArrayResourceValue arrayResourceValue = (ArrayResourceValue) resValue;
                            if (childCount != arrayResourceValue.getElementCount()) {
                                arv = arrayResourceValue;
                                source = res.getSource();
                                break;
                            }
                        }
                    }
                    String thisName = Lint.getFileNameWithParent(client, context.file);
                    assert source != null;
                    String otherName = Lint.getFileNameWithParent(client, source);
                    String message =
                            String.format(
                                    Locale.ROOT,
                                    "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                                    name,
                                    childCount,
                                    thisName,
                                    arv.getElementCount(),
                                    otherName);

                    context.report(INCONSISTENT, element, context.getLocation(element), message);
                    break;
                }
            }
        }
    }
}
