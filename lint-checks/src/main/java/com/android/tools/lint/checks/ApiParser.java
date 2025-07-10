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

import static com.android.SdkConstants.ATTR_ID;

import com.android.annotations.Nullable;
import com.android.tools.lint.checks.ApiLookup.UnsupportedVersionException;
import com.android.tools.lint.detector.api.ExtensionSdk;

import kotlin.text.StringsKt;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parser for the simplified XML API format version 1. */
class ApiParser extends DefaultHandler {
    public static final int MAX_SUPPORTED_VERSION = 4;

    private static final String NODE_CLASS = "class";
    private static final String NODE_FIELD = "field";
    private static final String NODE_METHOD = "method";
    private static final String NODE_EXTENDS = "extends";
    private static final String NODE_IMPLEMENTS = "implements";
    private static final String NODE_SDK = "sdk";
    private static final String NODE_API = "api";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_SHORT_NAME = "shortname";
    private static final String ATTR_SINCE = "since";
    private static final String ATTR_SDKS = "sdks";
    private static final String ATTR_DEPRECATED = "deprecated";
    private static final String ATTR_REMOVED = "removed";
    private static final String ATTR_REFERENCE = "reference";
    private static final String ATTR_VERSION = "version";

    // Grow class list
    private final Map<String, ApiClass> mClasses = new HashMap<>(7000);
    private final Map<String, ApiClassOwner<ApiClass>> mContainers = new HashMap<>(1200);
    private final List<ExtensionSdk> mSdks = new ArrayList<>();

    private ApiClass mCurrentClass;

    ApiParser() {}

    Map<String, ApiClass> getClasses() {
        return mClasses;
    }

    Map<String, ApiClassOwner<ApiClass>> getContainers() {
        return mContainers;
    }

    public List<ExtensionSdk> getExtensionSdks() {
        return mSdks;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (localName == null || localName.isEmpty()) {
            localName = qName;
        }

        try {
            if (NODE_METHOD.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                String sdks = getSdks(attributes, true);
                int since = getSince(attributes);
                int deprecatedIn = getDeprecatedIn(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addMethod(name, sdks, since, deprecatedIn, removedIn);
            } else if (NODE_FIELD.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                String sdks = getSdks(attributes, true);
                int since = getSince(attributes);
                int deprecatedIn = getDeprecatedIn(attributes);
                int removedIn = getRemovedIn(attributes);

                // Perform a few API corrections; see issue 73514594
                if (since == (28 << MAJOR_SHIFT)
                        && (name.equals("navigationBarDividerColor")
                                || name.equals("windowLightNavigationBar")
                                        && mCurrentClass.getName().equals("android/R$attr"))) {
                    since = toVersionInt(27, 0);
                }

                mCurrentClass.addField(name, sdks, since, deprecatedIn, removedIn);
            } else if (NODE_IMPLEMENTS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addInterface(name, since, removedIn);
            } else if (NODE_EXTENDS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                int since = getSince(attributes);
                int removedIn = getRemovedIn(attributes);
                mCurrentClass.addSuperClass(name, since, removedIn);
            } else if (NODE_CLASS.equals(localName)) {
                String name = attributes.getValue(ATTR_NAME);
                String sdks = getSdks(attributes, false);
                int since = getVersionAttribute(attributes, ATTR_SINCE, 1);
                int deprecatedIn = getVersionAttribute(attributes, ATTR_DEPRECATED, 0);
                int removedIn = getVersionAttribute(attributes, ATTR_REMOVED, 0);
                mCurrentClass = addClass(name, sdks, since, deprecatedIn, removedIn);
            } else if (NODE_SDK.equals(localName)) {
                String id = attributes.getValue(ATTR_ID);
                String name = attributes.getValue(ATTR_NAME);
                String shortName = attributes.getValue(ATTR_SHORT_NAME);
                String reference = attributes.getValue(ATTR_REFERENCE);
                if (reference != null) {
                    reference = reference.replace('/', '.').replace('$', '.');
                }
                mSdks.add(new ExtensionSdk(name, shortName, Integer.decode(id), reference));

            } else if (NODE_API.equals(localName)) {
                String versionString = attributes.getValue(ATTR_VERSION);
                if (versionString != null) {
                    // Only enforce on the major version. Backwards compatible adjustments
                    // can be made as long as only the minor version changes.
                    String major = StringsKt.substringBefore(versionString, '.', versionString);
                    int version = Integer.parseInt(major);
                    if (version > MAX_SUPPORTED_VERSION) {
                        throw new UnsupportedVersionException(version, MAX_SUPPORTED_VERSION);
                    }
                }
            }
        } finally {
            super.startElement(uri, localName, qName, attributes);
        }
    }

    private ApiClass addClass(
            String name, String sdks, int apiLevel, int deprecatedIn, int removedIn) {
        // There should not be any duplicates.
        ApiClass cls = mClasses.get(name);
        assert cls == null;
        cls = new ApiClass(name, sdks, apiLevel, deprecatedIn, removedIn);
        mClasses.put(name, cls);

        String containerName = cls.getContainerName();
        int len = containerName.length();
        boolean isClass = len < name.length() && name.charAt(len) == '$';
        ApiClassOwner<ApiClass> container = mContainers.get(containerName);
        if (container == null) {
            container = new ApiClassOwner<>(containerName, isClass);
            mContainers.put(containerName, container);
        } else if (container.isClass() != isClass) {
            throw new RuntimeException("\"" + containerName + "\" is both a package and a class");
        }
        container.addClass(cls);

        return cls;
    }

    private int getSince(Attributes attributes) {
        return getVersionAttribute(attributes, ATTR_SINCE, mCurrentClass.getSince());
    }

    private int getDeprecatedIn(Attributes attributes) {
        return getVersionAttribute(attributes, ATTR_DEPRECATED, mCurrentClass.getDeprecatedIn());
    }

    private int getRemovedIn(Attributes attributes) {
        return getVersionAttribute(attributes, ATTR_REMOVED, mCurrentClass.getRemovedIn());
    }

    @Nullable
    private String getSdks(Attributes attributes, boolean inherit) {
        String sdks = attributes.getValue(ATTR_SDKS);
        if (sdks == null && inherit) {
            sdks = mCurrentClass.getSdks();
        }
        return sdks;
    }

    private int getVersionAttribute(Attributes attributes, String attributeName, int defaultValue) {
        String attributeValue = attributes.getValue(attributeName);
        if (attributeValue == null) {
            return defaultValue;
        }
        return toVersionInt(attributeValue);
    }

    private static final int MAJOR_SHIFT = 8;
    private static final int MINOR_MASK = (1 << 8) - 1;

    public static int toVersionInt(String value) {
        int dot = value.indexOf('.');
        if (dot == -1) {
            return toVersionInt(Integer.parseInt(value), 0);
        }
        int major = Integer.parseInt(value.substring(0, dot));
        int minor = value.endsWith(".0") ? 0 : Integer.parseInt(value.substring(dot + 1));
        return toVersionInt(major, minor);
    }

    public static int toVersionInt(int major, int minor) {
        return (major << MAJOR_SHIFT) + minor;
    }

    public static int getMajorVersion(int versionInt) {
        return versionInt >> MAJOR_SHIFT;
    }

    public static int getMinorVersion(int versionInt) {
        return versionInt & MINOR_MASK;
    }
}
