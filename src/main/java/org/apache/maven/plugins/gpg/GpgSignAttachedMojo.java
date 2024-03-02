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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.layout.RepositoryLayout;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.transfer.NoRepositoryLayoutException;

/**
 * Sign project artifact, the POM, and attached artifacts with GnuPG for deployment.
 *
 * @author Jason van Zyl
 * @author Jason Dillon
 * @author Daniel Kulp
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class GpgSignAttachedMojo extends AbstractGpgMojo {

    /**
     * A list of files to exclude from being signed. Can contain Ant-style wildcards and double wildcards. The default
     * excludes are <code>**&#47;*.md5 **&#47;*.sha1 **&#47;*.sha256 **&#47;*.sha512 **&#47;*.asc **&#47;*.sigstore</code>.
     *
     * @since 1.0-alpha-4
     */
    @Parameter
    private String[] excludes;

    /**
     * The directory where to store signature files.
     *
     * @since 1.0-alpha-4
     */
    @Parameter(defaultValue = "${project.build.directory}/gpg", alias = "outputDirectory")
    private File ascDirectory;

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Maven ProjectHelper
     */
    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private RepositoryLayoutProvider repositoryLayoutProvider;

    private final RemoteRepository central =
            new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build();

    @Override
    public void doExecute() throws MojoExecutionException, MojoFailureException {
        // ----------------------------------------------------------------------------
        // Collect files to sign
        // ----------------------------------------------------------------------------

        ArrayList<Artifact> artifacts = new ArrayList<>();
        try {
            RepositoryLayout repositoryLayout = repositoryLayoutProvider.newRepositoryLayout(
                    session.getRepositorySession(),
                    project.getDistributionManagementArtifactRepository() != null
                            ? RepositoryUtils.toRepo(project.getDistributionManagementArtifactRepository())
                            : central);

            Artifact pomArtifact = RepositoryUtils.toArtifact(new ProjectArtifact(project));
            Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());

            if (pomArtifact.getFile().isFile()) {
                File pomToSign = new File(
                        project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".pom");
                try {
                    FileUtils.copyFile(project.getFile(), pomToSign);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error copying POM for signing.", e);
                }
                artifacts.add(pomArtifact.setFile(pomToSign));
            }

            if (projectArtifact.getFile().isFile()) {
                artifacts.add(projectArtifact);
            }

            for (org.apache.maven.artifact.Artifact attached : project.getAttachedArtifacts()) {
                getLog().debug("Attaching for deploy: " + attached.getId());
                artifacts.add(RepositoryUtils.toArtifact(attached));
            }

            // ----------------------------------------------------------------------------
            // Sign collected files and attach all the signatures
            // ----------------------------------------------------------------------------

            AbstractGpgSigner signer = newSigner(project);
            signer.setOutputDirectory(ascDirectory);
            signer.setBuildDirectory(new File(project.getBuild().getDirectory()));
            signer.setBaseDirectory(project.getBasedir());

            getLog().info("Signing " + artifacts.size() + " file" + ((artifacts.size() > 1) ? "s" : "") + " with "
                    + ((signer.keyname == null) ? "default" : signer.keyname) + " secret key.");

            for (Artifact artifact : artifacts) {
                if (!repositoryLayout.hasChecksums(artifact)) {
                    continue;
                }
                getLog().debug("Generating signature for " + artifact.getFile());

                File signature = signer.generateSignatureForArtifact(artifact.getFile());

                projectHelper.attachArtifact(
                        project,
                        artifact.getExtension() + AbstractGpgSigner.SIGNATURE_EXTENSION,
                        artifact.getClassifier(),
                        signature);
            }
        } catch (NoRepositoryLayoutException e) {
            throw new MojoFailureException(e);
        }
    }
}
