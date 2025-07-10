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

import com.android.annotations.NonNull;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.detector.api.ExtensionSdk;
import com.android.utils.XmlUtils;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;

/**
 * Main entry point for API descriptions.
 *
 * <p>To create the {@code Api}, use {@link #parseApi(File) or {@link #parseHiddenApi(URL)}}.
 */
class Api<C extends ApiClassBase> {
    /**
     * Parses simplified API file.
     *
     * @param apiFile the file to read
     * @return a new ApiInfo
     * @throws RuntimeException in case of an error
     */
    @NonNull
    public static Api<ApiClass> parseApi(File apiFile) {
        try (InputStream inputStream = new FileInputStream(apiFile)) {
            return parseApi(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses simplified API input stream. Caller should close stream after use.
     *
     * @param inputStream the API database input stream
     * @return a new ApiInfo
     * @throws RuntimeException in case of an error
     */
    @NonNull
    public static Api<ApiClass> parseApi(InputStream inputStream) {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            XmlUtils.configureSaxFactory(parserFactory, false, false);
            SAXParser parser = XmlUtils.createSaxParser(parserFactory);
            ApiParser apiParser = new ApiParser();
            parser.parse(inputStream, apiParser);
            return new Api<>(
                    apiParser.getClasses(),
                    apiParser.getContainers(),
                    apiParser.getExtensionSdks());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses private API file.
     *
     * @param input the input stream to the private API file (which might be inside a Jar)
     * @return a new Api
     * @throws RuntimeException in case of an error
     */
    @NonNull
    public static Api<PrivateApiClass> parseHiddenApi(URL input) {
        try (InputStream inputStream = input.openStream()) {
            PrivateApiParser privateApiParser = new PrivateApiParser();
            privateApiParser.parse(inputStream);
            return new Api<>(
                    privateApiParser.getClasses(),
                    privateApiParser.getContainers(),
                    Collections.emptyList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, C> mClasses;
    private final Map<String, ApiClassOwner<C>> mContainers;
    private final List<ExtensionSdk> mSdks;
    private final Map<Object, Integer> mSdkIndex = new LinkedHashMap<>();

    // Inserts some extra bulk data into the repository to force some
    // API levels to require two bytes to make sure our encoding/decoding
    // handles that as well.
    public static boolean TEST_TWO_BYTE_APIS = false;

    {
        int next = mSdkIndex.size();
        if (TEST_TWO_BYTE_APIS) {
            for (int i = 300; i < 500; i++) {
                mSdkIndex.put(ApiParser.toVersionInt(i, 0) + ":1", next++);
            }
        }

        // Reserve spots for the base API levels
        for (int i = 0; i <= SdkVersionInfo.HIGHEST_KNOWN_API + 1; i++) {
            mSdkIndex.put(ApiParser.toVersionInt(i, 0), next++);
        }

        // Special API level used in platform development to mean next API level
        mSdkIndex.put(ApiParser.toVersionInt(SdkVersionInfo.CUR_DEVELOPMENT, 0), next);
    }

    private Api(
            @NonNull Map<String, C> classes,
            @NonNull Map<String, ApiClassOwner<C>> containers,
            List<ExtensionSdk> sdks) {
        mClasses = Collections.unmodifiableMap(new MyHashMap<>(classes));
        mContainers = Collections.unmodifiableMap(new MyHashMap<>(containers));
        mSdks = Collections.unmodifiableList(sdks);
    }

    C getClass(String fqcn) {
        return mClasses.get(fqcn);
    }

    Map<String, C> getClasses() {
        return mClasses;
    }

    Map<String, ApiClassOwner<C>> getContainers() {
        return mContainers;
    }

    public short getSdkIndex(@NonNull Object sdks) {
        // Key is Integer api levels or String sdk-keys
        Integer sdkIndex = mSdkIndex.get(sdks);
        if (sdkIndex != null) {
            return sdkIndex.shortValue();
        }
        int index = mSdkIndex.size();
        mSdkIndex.put(sdks, index);
        return (short) index;
    }

    /**
     * Returns all the sdks= strings in the database, in the exact order matching the sdk-indices in
     * the various API objects.
     */
    @NonNull
    public List<String> getSdks() {
        List<String> list = new ArrayList<>();
        // Note: iteration order is significant; should match results from getSdkIndex()
        for (Object key : mSdkIndex.keySet()) {
            if (key instanceof Integer) {
                int value = (Integer) key;
                int major = ApiParser.getMajorVersion(value);
                int minor = ApiParser.getMinorVersion(value);
                if (minor > 0) {
                    list.add(Integer.toString(major) + '.' + minor);
                } else {
                    list.add(Integer.toString(major));
                }
            } else if (key instanceof String) {
                list.add(key.toString());
            } else {
                throw new RuntimeException(key.toString());
            }
        }
        return list;
    }

    @NonNull
    public List<ExtensionSdk> getExtensionSdks() {
        return mSdks;
    }

    /** The hash map that doesn't distinguish between '.', '/', and '$' in the key string. */
    private static class MyHashMap<V> extends Object2ObjectOpenCustomHashMap<String, V> {
        private static final Hash.Strategy<String> myHashingStrategy =
                new Hash.Strategy<>() {
                    @Override
                    public int hashCode(String str) {
                        if (str == null) return 0;
                        int h = 0;
                        for (int i = 0; i < str.length(); i++) {
                            char c = str.charAt(i);
                            c = normalizeSeparator(c);
                            h = 31 * h + c;
                        }
                        return h;
                    }

                    @Override
                    public boolean equals(String s1, String s2) {
                        if (s1 == null) return s2 == null;
                        if (s2 == null) return false;
                        if (s1.length() != s2.length()) return false;

                        for (int i = 0; i < s1.length(); i++) {
                            if (normalizeSeparator(s1.charAt(i))
                                    != normalizeSeparator(s2.charAt(i))) {
                                return false;
                            }
                        }
                        return true;
                    }
                };

        private static char normalizeSeparator(char c) {
            if (c == '/' || c == '$') {
                c = '.';
            }
            return c;
        }

        MyHashMap(Map<String, V> data) {
            super(data, myHashingStrategy);
        }
    }
}
