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

import com.android.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/** Represents a package or a class containing inner classes. */
public class ApiClassOwner<C extends ApiClassBase> implements Comparable<ApiClassOwner> {
    private final String mName;
    private final boolean isClass;
    private final List<C> mClasses = new ArrayList<>(100);

    // Persistence data: Used when writing out binary data in ApiLookup
    int indexOffset; // offset of the package entry

    ApiClassOwner(@NonNull String name, boolean isClass) {
        mName = name;
        this.isClass = isClass;
    }

    /** Returns the fully qualified name of the container. */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Returns true if this container is a class, or false if it is a package. */
    public boolean isClass() {
        return isClass;
    }

    /**
     * If this container is a package, returns the classes in this package, or, if this container is
     * a class, the inner classes.
     *
     * @return the classes in this container
     */
    @NonNull
    public List<C> getClasses() {
        return mClasses;
    }

    void addClass(@NonNull C cls) {
        mClasses.add(cls);
    }

    @Override
    public int compareTo(@NonNull ApiClassOwner other) {
        return mName.compareTo(other.mName);
    }

    @Override
    public String toString() {
        return mName;
    }
}
