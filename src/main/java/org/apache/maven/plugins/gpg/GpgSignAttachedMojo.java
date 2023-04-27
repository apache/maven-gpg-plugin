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
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

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
     * Skip doing the gpg signing.
     */
    @Parameter(property = "gpg.skip", defaultValue = "false")
    private boolean skip;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            // We're skipping the signing stuff
            return;
        }

        // ----------------------------------------------------------------------------
        // Collect files to sign
        // ----------------------------------------------------------------------------

        FilesCollector collector = new FilesCollector(project, excludes, getLog());
        List<FilesCollector.Item> items = collector.collect();

        // ----------------------------------------------------------------------------
        // Sign collected files and attach all the signatures
        // ----------------------------------------------------------------------------

        AbstractGpgSigner signer = newSigner(project);
        signer.setOutputDirectory(ascDirectory);
        signer.setBuildDirectory(new File(project.getBuild().getDirectory()));
        signer.setBaseDirectory(project.getBasedir());

        getLog().info("Signing " + items.size() + " file" + ((items.size() > 1) ? "s" : "") + " with "
                + ((signer.keyname == null) ? "default" : signer.keyname) + " secret key.");

        for (FilesCollector.Item item : items) {
            getLog().debug("Generating signature for " + item.getFile());

            File signature = signer.generateSignatureForArtifact(item.getFile());

            projectHelper.attachArtifact(
                    project,
                    item.getExtension() + AbstractGpgSigner.SIGNATURE_EXTENSION,
                    item.getClassifier(),
                    signature);
        }
    }
}
