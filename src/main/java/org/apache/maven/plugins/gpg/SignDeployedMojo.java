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

import javax.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Resolves given artifacts from a given remote repository, signs them, and deploys the signatures next to signed
 * artifacts, and cleans up afterward. This mojo will use "own" local repository for all the operations to not
 * "pollute" user local repository, and also to be able to fully clean up (delete) after job done.
 *
 * @since 3.2.3
 */
@Mojo(name = "sign-deployed", requiresProject = false, threadSafe = true)
public class SignDeployedMojo extends AbstractGpgMojo {

    /**
     * URL where the artifacts are deployed.
     */
    @Parameter(property = "url", required = true)
    private String url;

    /**
     * Server ID to map on the &lt;id&gt; under &lt;server&gt; section of <code>settings.xml</code>. In most cases, this
     * parameter will be required for authentication.
     */
    @Parameter(property = "repositoryId", required = true)
    private String repositoryId;

    /**
     * Should generate coordinates "javadoc" sub-artifacts?
     */
    @Parameter(property = "javadoc", defaultValue = "true", required = true)
    private boolean javadoc;

    /**
     * Should generate coordinates "sources" sub-artifacts?
     */
    @Parameter(property = "sources", defaultValue = "true", required = true)
    private boolean sources;

    /**
     * If no {@link ArtifactCollectorSPI} is added, this Mojo will fall back to this parameter to collect GAVs that are
     * deployed and needs signatures deployed next to them. This parameter can contain multiple things:
     * <ul>
     *     <li>A path to an existing file, that contains one GAV spec at a line. File may also contain empty lines or
     *     lines starting with {@code #} that will be ignored.</li>
     *     <li>A comma separated list of GAV specs.</li>
     * </ul>
     * <p>
     * Note: format of GAV entries must be {@code <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>}.
     */
    @Parameter(property = "artifacts")
    private String artifacts;

    @Component
    private RepositorySystem repositorySystem;

    @Inject
    private Map<String, ArtifactCollectorSPI> artifactCollectors;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        if (settings.isOffline()) {
            throw new MojoFailureException("Cannot deploy artifacts when Maven is in offline mode");
        }

        Path tempDirectory = null;
        Set<Artifact> artifacts = new HashSet<>();
        try {
            tempDirectory = Files.createTempDirectory("gpg-sign-deployed");
            getLog().debug("Using temp directory " + tempDirectory);

            DefaultRepositorySystemSession signingSession =
                    new DefaultRepositorySystemSession(session.getRepositorySession());
            signingSession.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(
                    signingSession, new LocalRepository(tempDirectory.toFile())));

            // remote repo where deployed artifacts are, and where signatures need to be deployed
            RemoteRepository deploymentRepository = repositorySystem.newDeploymentRepository(
                    signingSession, new RemoteRepository.Builder(repositoryId, "default", url).build());

            // get artifacts list
            getLog().debug("Collecting artifacts for signing...");
            artifacts.addAll(collectArtifacts(signingSession, deploymentRepository));
            getLog().info("Collected " + artifacts.size() + " artifact" + ((artifacts.size() > 1) ? "s" : "")
                    + " for signing");

            // create additional ones if needed
            if (sources || javadoc) {
                getLog().debug("Adding additional artifacts...");
                List<Artifact> additions = new ArrayList<>();
                for (Artifact artifact : artifacts) {
                    if (artifact.getClassifier().isEmpty()) {
                        if (sources) {
                            additions.add(new SubArtifact(artifact, "sources", "jar"));
                        }
                        if (javadoc) {
                            additions.add(new SubArtifact(artifact, "javadoc", "jar"));
                        }
                    }
                }
                artifacts.addAll(additions);
            }

            // resolve them all
            getLog().info("Resolving " + artifacts.size() + " artifact" + ((artifacts.size() > 1) ? "s" : "")
                    + " artifacts for signing...");
            List<ArtifactResult> results = repositorySystem.resolveArtifacts(
                    signingSession,
                    artifacts.stream()
                            .map(a -> new ArtifactRequest(a, Collections.singletonList(deploymentRepository), "gpg"))
                            .collect(Collectors.toList()));
            artifacts = results.stream().map(ArtifactResult::getArtifact).collect(Collectors.toSet());

            // sign all
            AbstractGpgSigner signer = newSigner(null);
            signer.setOutputDirectory(tempDirectory.toFile());
            getLog().info("Signer '" + signer.signerName() + "' is signing " + artifacts.size() + " file"
                    + ((artifacts.size() > 1) ? "s" : "") + " with key " + signer.getKeyInfo());

            HashSet<Artifact> signatures = new HashSet<>();
            for (Artifact a : artifacts) {
                signatures.add(new DefaultArtifact(
                                a.getGroupId(),
                                a.getArtifactId(),
                                a.getClassifier(),
                                a.getExtension() + AbstractGpgSigner.SIGNATURE_EXTENSION,
                                a.getVersion())
                        .setFile(signer.generateSignatureForArtifact(a.getFile())));
            }

            // deploy all signature
            getLog().info("Deploying artifact signatures...");
            repositorySystem.deploy(
                    signingSession,
                    new DeployRequest()
                            .setRepository(deploymentRepository)
                            .setArtifacts(signatures)
                            .setTrace(RequestTrace.newChild(null, this)));
        } catch (IOException e) {
            throw new MojoExecutionException("IO error: " + e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(
                    "Error resolving deployed artifacts " + artifacts + ": " + e.getMessage(), e);
        } catch (DeploymentException e) {
            throw new MojoExecutionException("Error deploying signatures: " + e.getMessage(), e);
        } finally {
            if (tempDirectory != null) {
                getLog().info("Cleaning up...");
                try {
                    FileUtils.deleteDirectory(tempDirectory.toFile());
                } catch (IOException e) {
                    getLog().warn("Could not clean up temp directory " + tempDirectory);
                }
            }
        }
    }

    /**
     * Returns a collection of remotely deployed artifacts that needs to be signed and have signatures deployed
     * next to them.
     */
    protected Collection<Artifact> collectArtifacts(RepositorySystemSession session, RemoteRepository remoteRepository)
            throws IOException {
        Collection<Artifact> result = null;
        for (ArtifactCollectorSPI artifactCollector : artifactCollectors.values()) {
            result = artifactCollector.collectArtifacts(session, remoteRepository);
            if (result != null) {
                break;
            }
        }
        if (result == null) {
            if (artifacts != null) {
                try {
                    Path path = Paths.get(artifacts);
                    if (Files.isRegularFile(path)) {
                        try (Stream<String> lines = Files.lines(path)) {
                            result = lines.filter(l -> !l.isEmpty() && !l.startsWith("#"))
                                    .map(DefaultArtifact::new)
                                    .collect(Collectors.toSet());
                        }
                    }
                } catch (InvalidPathException e) {
                    // ignore
                }
                if (result == null) {
                    result = Arrays.stream(artifacts.split(","))
                            .map(DefaultArtifact::new)
                            .collect(Collectors.toSet());
                }
            }
        }
        if (result == null) {
            throw new IllegalStateException("No source to collect from (set -Dartifacts=g:a:v... or add collector)");
        }
        return result;
    }
}
