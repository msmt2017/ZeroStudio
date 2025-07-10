/*
 * Copyright (C) 2019 The Android Open Source Project
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
import java.nio.ByteBuffer;
import java.util.List;

public abstract class ApiClassBase implements Comparable<ApiClassBase> {
    private final String name;

    public ApiClassBase(@NonNull String name) {
        this.name = name;
    }

    /**
     * Returns the name of the class.
     *
     * @return the name of the class
     */
    String getName() {
        return name;
    }

    abstract int computeExtraStorageNeeded(Api<? extends ApiClassBase> info);

    abstract void writeSuperInterfaces(Api<? extends ApiClassBase> info, ByteBuffer buffer);

    abstract void writeMemberData(
            Api<? extends ApiClassBase> info, String member, ByteBuffer buffer);

    // Persistence data: Used when writing out binary data in ApiLookup
    List<String> members;
    // Persistence data: Whether we want to force member names to be include in the database instead
    // of hash codes
    boolean includeNames;

    int index; // class number, e.g. entry in index where the pointer can be found
    int indexOffset; // offset of the class entry
    int memberOffsetBegin; // offset of the first member entry in the class
    int memberOffsetEnd; // offset after the last member entry in the class
    int memberIndexStart; // entry in index for first member
    int memberIndexLength; // number of entries

    @NonNull
    public String getContainerName() {
        int index = lastIndexOfSlashOrDollar(name);
        if (index >= 0) {
            return name.substring(0, index);
        }

        return "";
    }

    private static int lastIndexOfSlashOrDollar(String className) {
        for (int i = className.length(); --i >= 0; ) {
            char c = className.charAt(i);
            if (c == '/' || c == '$') {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    public String getSimpleName() {
        int index = name.lastIndexOf('/');
        if (index != -1) {
            return name.substring(index + 1);
        }

        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(@NonNull ApiClassBase other) {
        return name.compareTo(other.name);
    }
}
