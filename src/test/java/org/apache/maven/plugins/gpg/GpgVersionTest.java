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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GpgVersion}.
 */
class GpgVersionTest {
    @Test
    void test() {
        assertTrue(GpgVersion.parse("gpg (GnuPG) 2.2.1").isAtLeast(GpgVersion.parse("gpg (GnuPG) 2.2.1")));
        assertTrue(GpgVersion.parse("gpg (GnuPG) 2.2.1").isAtLeast(GpgVersion.parse("2.1")));
        assertTrue(GpgVersion.parse("gpg (GnuPG/MacGPG2) 2.2.10").isAtLeast(GpgVersion.parse("2.2.10")));
        assertTrue(GpgVersion.parse("gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)").isAtLeast(GpgVersion.parse("2.0.26")));
    }

    @Test
    void opposite() {
        assertFalse(GpgVersion.parse("gpg (GnuPG) 2.2.1").isBefore(GpgVersion.parse("gpg (GnuPG) 2.2.1")));
        assertFalse(GpgVersion.parse("gpg (GnuPG) 2.2.1").isBefore(GpgVersion.parse("2.1")));
        assertFalse(GpgVersion.parse("gpg (GnuPG/MacGPG2) 2.2.10").isBefore(GpgVersion.parse("2.2.10")));
        assertFalse(GpgVersion.parse("gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)").isBefore(GpgVersion.parse("2.0.26")));
    }

    @Test
    void equality() {
        assertEquals(GpgVersion.parse("gpg (GnuPG) 2.2.1"), GpgVersion.parse("gpg (GnuPG) 2.2.1"));
        assertEquals(GpgVersion.parse("gpg (GnuPG) 2.2.1"), GpgVersion.parse("2.2.1"));
        assertEquals(GpgVersion.parse("gpg (GnuPG/MacGPG2) 2.2.10"), GpgVersion.parse("2.2.10"));
        assertEquals(GpgVersion.parse("gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)"), GpgVersion.parse("2.0.26"));

        assertEquals(
                GpgVersion.parse("gpg (GnuPG) 2.2.1").hashCode(),
                GpgVersion.parse("gpg (GnuPG) 2.2.1").hashCode());
        assertEquals(
                GpgVersion.parse("gpg (GnuPG) 2.2.1").hashCode(),
                GpgVersion.parse("2.2.1").hashCode());
        assertEquals(
                GpgVersion.parse("gpg (GnuPG/MacGPG2) 2.2.10").hashCode(),
                GpgVersion.parse("2.2.10").hashCode());
        assertEquals(
                GpgVersion.parse("gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)").hashCode(),
                GpgVersion.parse("2.0.26").hashCode());

        assertNotEquals(GpgVersion.parse("gpg (GnuPG) 2.2.1"), GpgVersion.parse("2.2.0"));
        assertNotEquals(GpgVersion.parse("gpg (GnuPG) 2.2.1"), GpgVersion.parse("2.2"));

        assertNotEquals(
                GpgVersion.parse("gpg (GnuPG) 2.2.1").hashCode(),
                GpgVersion.parse("2.2.0").hashCode());
        assertNotEquals(
                GpgVersion.parse("gpg (GnuPG) 2.2.1").hashCode(),
                GpgVersion.parse("2.2").hashCode());
    }
}
