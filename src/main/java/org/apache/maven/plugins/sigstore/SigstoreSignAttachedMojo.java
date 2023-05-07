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
package org.apache.maven.plugins.sigstore;

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

import java.io.File;
import java.util.List;

import dev.sigstore.KeylessSignature;
import dev.sigstore.KeylessSigner;
import dev.sigstore.bundle.BundleFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.gpg.FilesCollector;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;

/**
 * Sign project artifact, the POM, and attached artifacts with sigstore for deployment.
 *
 * @since 3.1.1
 */
@Mojo(name = "sigstore", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SigstoreSignAttachedMojo extends AbstractMojo {

    /**
     * Skip doing the gpg signing.
     */
    @Parameter(property = "sigstore.skip", defaultValue = "false")
    private boolean skip;

    /**
     * A list of files to exclude from being signed. Can contain Ant-style wildcards and double wildcards. The default
     * excludes are <code>**&#47;*.md5 **&#47;*.sha1 **&#47;*.sha256 **&#47;*.sha512 **&#47;*.asc **&#47;*.sigstore</code>.
     */
    @Parameter
    private String[] excludes;

    /**
     * Use public staging {@code sigstage.dev} instead of public default {@code sigstore.dev}.
     */
    @Parameter(defaultValue = "false", property = "public-staging")
    private boolean publicStaging;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

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
        // Sign the filesToSign and attach all the signatures
        // ----------------------------------------------------------------------------

        getLog().info("Signing " + items.size() + " file" + ((items.size() > 1) ? "s" : "") + ".");

        try {
            KeylessSigner signer;

            if (publicStaging) {
                signer = KeylessSigner.builder().sigstoreStagingDefaults().build();
            } else {
                signer = KeylessSigner.builder().sigstorePublicDefaults().build();
            }

            for (FilesCollector.Item item : items) {
                File fileToSign = item.getFile();

                getLog().info("Signing " + fileToSign);
                long start = System.currentTimeMillis();
                KeylessSignature signature = signer.signFile(fileToSign.toPath());

                // sigstore signature in bundle format (json string)
                String sigstoreBundle = BundleFactory.createBundle(signature);

                File signatureFile = new File(fileToSign + ".sigstore");
                FileUtils.fileWrite(signatureFile, "UTF-8", sigstoreBundle);

                long duration = System.currentTimeMillis() - start;
                getLog().info("        > " + signatureFile.getName() + " in " + duration + " ms");

                projectHelper.attachArtifact(
                        project, item.getExtension() + ".sigstore", item.getClassifier(), signatureFile);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Error while signing with sigstore", e);
        }
    }
}
