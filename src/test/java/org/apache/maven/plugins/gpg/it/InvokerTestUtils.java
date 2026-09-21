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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.forked.ForkedMavenExecutor;

public class InvokerTestUtils {

    /**
     * Builds the request for a {@code clean install} of the IT project, in batch mode unless {@code interactive}.
     */
    public static ExecutorRequest.Builder createRequest(
            File pomFile,
            File mavenUserSettings,
            File gpgHome,
            String signer,
            boolean providePassphraseEnv,
            boolean interactive) {
        final List<String> arguments = new ArrayList<>();
        if (!interactive) {
            arguments.add("-B");
        }
        arguments.add("-V");
        arguments.add("-X");
        arguments.add("-e");
        arguments.add("-s");
        arguments.add(mavenUserSettings.getAbsolutePath());
        arguments.add("-f");
        arguments.add(pomFile.getAbsolutePath());

        // Required for JRE 7 to connect to Maven Central with TLSv1.2
        final String httpsProtocols = System.getProperty("https.protocols");
        if (httpsProtocols != null && !httpsProtocols.isEmpty()) {
            arguments.add("-Dhttps.protocols=" + httpsProtocols);
        }

        if (signer != null) {
            arguments.add("-Dgpg.signer=" + signer);
            arguments.add("-Dgpg.keyFilePath=" + new File("src/test/resources/signing-key.asc").getAbsolutePath());
        }
        arguments.add("-Dgpg.homedir=" + gpgHome.getAbsolutePath());

        arguments.add("clean");
        arguments.add("install");

        final ExecutorRequest.Builder request = ExecutorRequest.mavenBuilder()
                .cwd(pomFile.getParentFile().toPath())
                .arguments(arguments)
                .executionTimeout(Duration.ofSeconds(60)); // safeguard against GPG freezes

        if (providePassphraseEnv) {
            request.environmentVariable("MAVEN_GPG_PASSPHRASE", "TEST");
        }

        return request;
    }

    public static ExecutorRequest.Builder createRequest(
            File pomFile, File mavenUserSettings, File gpgHome, String signer, boolean providePassphraseEnv) {
        return createRequest(pomFile, mavenUserSettings, gpgHome, signer, providePassphraseEnv, false);
    }

    /**
     * Runs the request with the given Maven installation and local repository; Maven's output goes to
     * {@code build.log} next to the IT project's POM.
     */
    public static BuildResult executeRequest(
            final ExecutorRequest.Builder request, final File mavenHome, final File localRepository)
            throws IOException {
        final File buildLog = new File(request.build().cwd().toFile(), "build.log");
        try (OutputStream buildLogStream = Files.newOutputStream(buildLog.toPath());
                ForkedMavenExecutor executor = new ForkedMavenExecutor(mavenHome.toPath())) {
            final ExecutorResult result =
                    executor.execute(request.argument("-Dmaven.repo.local=" + localRepository.getAbsolutePath())
                            .stdOut(buildLogStream)
                            .stdErr(buildLogStream)
                            .build());
            return new BuildResult(buildLog, result);
        }
    }

    public static File getTestResource(final String path) throws URISyntaxException, FileNotFoundException {
        final URL resourceUrl = InvokerTestUtils.class.getResource(path);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Cannot find file " + path);
        }

        return new File(resourceUrl.toURI());
    }
}
