/*
 * Copyright (C) 2017 The Android Open Source Project
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

/** Represents a method or a field. */
public final class ApiMember {
    @NonNull private final String mSignature;
    private final int mSince;
    private final int mDeprecatedIn;
    private final int mRemovedIn;

    public ApiMember(@NonNull String signature, int since, int deprecatedIn, int removedIn) {
        assert since > 0;
        mSignature = signature;
        mSince = since;
        mDeprecatedIn = deprecatedIn;
        mRemovedIn = removedIn;
    }

    /**
     * If the object represents a method, returns its signature without the return type. If the
     * object represents a field returns its name.
     */
    @NonNull
    public String getSignature() {
        return mSignature;
    }

    /** Returns the API level when the member was introduced. */
    public int getSince() {
        return mSince;
    }

    /**
     * Returns the API level when the member was deprecated, or 0 if the member was not deprecated.
     */
    public int getDeprecatedIn() {
        return mDeprecatedIn;
    }

    /** Returns the API level when the member was removed, or 0 if the member was not removed. */
    public int getRemovedIn() {
        return mRemovedIn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ApiMember other = (ApiMember) o;
        return mSignature.equals(other.mSignature)
                && mSince == other.mSince
                && mDeprecatedIn == other.mDeprecatedIn
                && mRemovedIn == other.mRemovedIn;
    }

    @Override
    public int hashCode() {
        return mSignature.hashCode();
    }

    @Override
    public String toString() {
        return mSignature;
    }
}
