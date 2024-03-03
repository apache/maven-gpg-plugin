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
package org.apache.maven.plugins.gpg.it;

import java.io.File;

import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GpgSignAttachedMojoIT {

    private final File mavenHome;
    private final File localRepository;
    private final File mavenUserSettings;
    private final File gpgHome;

    public GpgSignAttachedMojoIT() throws Exception {
        this.mavenHome = new File(System.getProperty("maven.home"));
        this.localRepository = new File(System.getProperty("localRepositoryPath"));
        this.mavenUserSettings = InvokerTestUtils.getTestResource(System.getProperty("settingsFile"));
        this.gpgHome = new File(System.getProperty("gpg.homedir"));
    }

    @Test
    void testInteractiveWithoutPassphrase() throws Exception {
        // given
        final File pomFile =
                InvokerTestUtils.getTestResource("/it/sign-release-without-passphrase-interactive/pom.xml");
        final InvocationRequest request = InvokerTestUtils.createRequest(pomFile, mavenUserSettings, gpgHome, false);

        // require Maven interactive mode
        request.setBatchMode(false);

        // when
        final BuildResult result = InvokerTestUtils.executeRequest(request, mavenHome, localRepository);

        final InvocationResult invocationResult = result.getInvocationResult();
        final String buildLogContent = FileUtils.fileRead(result.getBuildLog());

        // then
        assertNotEquals(0, invocationResult.getExitCode(), "Maven execution must fail");
        assertTrue(
                buildLogContent.contains("[GNUPG:] FAILURE sign 67108949"),
                "Maven execution failed because no pinentry program is available");
    }
}
