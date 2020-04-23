package org.apache.maven.plugins.gpg;

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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GpgVersion}.
 */
public class GpgVersionTest
{
    @Test
    public void test()
    {
        assertTrue( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ).isAtLeast( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ) ) );
        assertTrue( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ).isAtLeast( GpgVersion.parse( "2.1" ) ) );
        assertTrue( GpgVersion.parse( "gpg (GnuPG/MacGPG2) 2.2.10" ).isAtLeast( GpgVersion.parse( "2.2.10" ) ) );
        assertTrue( GpgVersion.parse( "gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)" )
                .isAtLeast( GpgVersion.parse( "2.0.26" ) ) );
    }

    @Test
    public void testOpposite()
    {
        assertFalse( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ).isBefore( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ) ) );
        assertFalse( GpgVersion.parse( "gpg (GnuPG) 2.2.1" ).isBefore( GpgVersion.parse( "2.1" ) ) );
        assertFalse( GpgVersion.parse( "gpg (GnuPG/MacGPG2) 2.2.10" ).isBefore( GpgVersion.parse( "2.2.10" ) ) );
        assertFalse( GpgVersion.parse( "gpg (GnuPG) 2.0.26 (Gpg4win 2.2.3)" )
                .isBefore( GpgVersion.parse( "2.0.26" ) ) );
    }
}
