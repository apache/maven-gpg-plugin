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
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;

import org.apache.commons.io.input.NullInputStream;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.PrintStreamLogger;

public class InvokerTestUtils {

    public static InvocationRequest createRequest(
            final File pomFile, final File mavenUserSettings, final File gpgHome) {
        final InvocationRequest request = new DefaultInvocationRequest();
        request.setUserSettingsFile(mavenUserSettings);
        request.setShowVersion(true);
        request.setDebug(true);
        request.setShowErrors(true);
        request.setTimeoutInSeconds(60); // safeguard against GPG freezes
        request.setGoals(Arrays.asList("clean", "install"));
        request.setPomFile(pomFile);

        final Properties properties = new Properties();
        request.setProperties(properties);

        // Required for JRE 7 to connect to Maven Central with TLSv1.2
        final String httpsProtocols = System.getProperty("https.protocols");
        if (httpsProtocols != null && !httpsProtocols.isEmpty()) {
            properties.setProperty("https.protocols", httpsProtocols);
        }

        properties.setProperty("gpg.homedir", gpgHome.getAbsolutePath());

        return request;
    }

    public static BuildResult executeRequest(
            final InvocationRequest request, final File mavenHome, final File localRepository)
            throws FileNotFoundException, MavenInvocationException {
        final InvocationResult result;

        final File buildLog =
                new File(request.getBaseDirectory(request.getPomFile().getParentFile()), "build.log");
        try (final PrintStream buildLogStream = new PrintStream(buildLog)) {
            final InvocationOutputHandler buildLogOutputHandler = new PrintStreamHandler(buildLogStream, false);
            final InvokerLogger logger = new PrintStreamLogger(buildLogStream, InvokerLogger.DEBUG);

            final Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(mavenHome);
            invoker.setLocalRepositoryDirectory(localRepository);
            invoker.setInputStream(new NullInputStream(0));
            invoker.setOutputHandler(buildLogOutputHandler);
            invoker.setErrorHandler(buildLogOutputHandler);
            invoker.setLogger(logger);

            result = invoker.execute(request);
        }

        return new BuildResult(buildLog, result);
    }

    public static File getTestResource(final String path) throws URISyntaxException, FileNotFoundException {
        final URL resourceUrl = InvokerTestUtils.class.getResource(path);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Cannot find file " + path);
        }

        return new File(resourceUrl.toURI());
    }
}
