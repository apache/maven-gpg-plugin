/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.gpg;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the semver value of GPG.
 * This is in most cases the first line of <code>gpg --version</code>
 *
 *
 * @author Robert Scholte
 * @since 3.0.0
 */
public class GpgVersion implements Comparable<GpgVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.)+(\\d+)");

    private final int[] versionSegments;

    private GpgVersion(int... versionSegments) {
        this.versionSegments = versionSegments;
    }

    public static GpgVersion parse(String rawVersion) {
        final Matcher versionMatcher = VERSION_PATTERN.matcher(rawVersion);
        if (!versionMatcher.find()) {
            throw new IllegalArgumentException("Can't parse version of " + rawVersion);
        }

        final String[] rawVersionSegments = versionMatcher.group(0).split("\\.");

        final int[] versionSegments = new int[rawVersionSegments.length];
        for (int index = 0; index < rawVersionSegments.length; index++) {
            versionSegments[index] = Integer.parseInt(rawVersionSegments[index]);
        }

        return new GpgVersion(versionSegments);
    }

    @Override
    public int compareTo(GpgVersion other) {
        final int[] thisSegments = versionSegments;
        final int[] otherSegments = other.versionSegments;

        int minSegments = Math.min(thisSegments.length, otherSegments.length);

        for (int index = 0; index < minSegments; index++) {
            int compareValue = Integer.compare(thisSegments[index], otherSegments[index]);

            if (compareValue != 0) {
                return compareValue;
            }
        }

        return (thisSegments.length - otherSegments.length);
    }

    /**
     * Verify if this version is before some other version
     *
     * @param other the version to compare with
     * @return {@code true} is this is less than {@code other}, otherwise {@code false}
     */
    public boolean isBefore(GpgVersion other) {
        return this.compareTo(other) < 0;
    }

    /**
     * Verify if this version is at least some other version
     *
     * @param other the version to compare with
     * @return  {@code true}  is this is greater than or equal to {@code other}, otherwise {@code false}
     */
    public boolean isAtLeast(GpgVersion other) {
        return this.compareTo(other) >= 0;
    }

    @Override
    public String toString() {
        if (versionSegments.length == 0) {
            return "";
        }

        final StringBuilder versionStringBuilder = new StringBuilder();
        versionStringBuilder.append(versionSegments[0]);

        for (int index = 1; index < versionSegments.length; index++) {
            versionStringBuilder.append('.').append(versionSegments[index]);
        }

        return versionStringBuilder.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof GpgVersion)) {
            return false;
        }

        final GpgVersion that = (GpgVersion) other;

        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(versionSegments);
    }
}
