package org.apache.maven.plugins.gpg.it;

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

import org.apache.maven.shared.invoker.InvocationRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.runners.Parameterized.*;

@RunWith( Parameterized.class )
public class GpgSignArtifactIT
{
    private final File mavenHome;
    private final File localRepository;
    private final File mavenUserSettings;
    private final File gpgHome;

    public GpgSignArtifactIT() throws Exception
    {
        this.mavenHome = new File( System.getProperty( "maven.home" ) );
        this.localRepository = new File( System.getProperty( "localRepositoryPath" ) );
        this.mavenUserSettings = InvokerTestUtils.getTestResource( "/it/settings-with-passphrase.xml" );
        this.gpgHome = new File( System.getProperty( "gpg.homedir" ) );
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "/it/sign-release-with-artifact/pom.xml", "/target/gpg/tarballs/", new String[]{ "sign-release-with-artifact-1.0-jar-with-dependencies.jar.asc" } },
                { "/it/sign-release-with-artifact-and-output-directory/pom.xml", "/target/signed-files/tarballs/", new String[]{ "sign-release-with-artifact-and-output-directory-1.0-jar-with-dependencies.jar.asc" } },
                { "/it/sign-release-with-artifact-and-output-directory-root/pom.xml", "/signed-files/tarballs/", new String[]{ "sign-release-with-artifact-and-output-directory-root-1.0-jar-with-dependencies.jar.asc" } },
                { "/it/sign-release-with-artifact-same-directory/pom.xml", "/target/tarballs/", new String[]{ "sign-release-with-artifact-same-directory-1.0-jar-with-dependencies.jar", "sign-release-with-artifact-same-directory-1.0-jar-with-dependencies.jar.asc" } },
        });
    }

    @Parameter
    public String pomPath;
    @Parameter(1)
    public String expectedFileLocation;
    @Parameter(2)
    public String[] expectedFiles;

    @Test
    public void testFolderStructureWithArtifactAndDefaultOutputDirectory() throws Exception
    {
        // given
        final File pomFile = InvokerTestUtils.getTestResource( pomPath );
        final InvocationRequest request = InvokerTestUtils.createRequest( pomFile, mavenUserSettings, gpgHome );
        final File integrationTestRootDirectory = new File( pomFile.getParent());
        final File expectedOutputDirectory =  new File (integrationTestRootDirectory + expectedFileLocation );

        // when
        InvokerTestUtils.executeRequest( request, mavenHome, localRepository );

        // then
        assertThat( expectedOutputDirectory.exists(), equalTo( true ) );
        assertThat( expectedOutputDirectory.list(), equalTo( expectedFiles ) );
    }

}
