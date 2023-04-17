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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.SelectorUtils;

import dev.sigstore.KeylessSignature;
import dev.sigstore.KeylessSigner;
import dev.sigstore.bundle.BundleFactory;

/**
 * Sign project artifact, the POM, and attached artifacts with sigstore for deployment.
 *
 * @since 3.1.0
 */
@Mojo( name = "sigstore", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true )
public class SigstoreSignAttachedMojo
    extends AbstractMojo
{

    private static final String DEFAULT_EXCLUDES[] =
        new String[] { "**/*.md5", "**/*.sha1", "**/*.sha256", "**/*.sha512", "**/*.asc", "**/*.sigstore" };

    /**
     * Skip doing the gpg signing.
     */
    @Parameter( property = "sigstore.skip", defaultValue = "false" )
    private boolean skip;

    /**
     * A list of files to exclude from being signed. Can contain Ant-style wildcards and double wildcards. The default
     * excludes are <code>**&#47;*.md5   **&#47;*.sha1    **&#47;*.sha256    **&#47;*.sha512
     *     **&#47;*.asc    **&#47;*.sigstore</code>.
     */
    @Parameter
    private String[] excludes;

    /**
     * The Maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    /**
     * PoC: wait time before each file signature (in seconds)
     */
    @Parameter( property = "sigstore.wait", defaultValue = "0" )
    private long wait;

    /**
     * Maven ProjectHelper
     */
    @Component
    private MavenProjectHelper projectHelper;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            // We're skipping the signing stuff
            return;
        }

        if ( excludes == null || excludes.length == 0 )
        {
            excludes = DEFAULT_EXCLUDES;
        }
        String newExcludes[] = new String[excludes.length];
        for ( int i = 0; i < excludes.length; i++ )
        {
            String pattern;
            pattern = excludes[i].trim().replace( '/', File.separatorChar ).replace( '\\', File.separatorChar );
            if ( pattern.endsWith( File.separator ) )
            {
                pattern += "**";
            }
            newExcludes[i] = pattern;
        }
        excludes = newExcludes;

        List<SigningBundle> filesToSign = new ArrayList<>();

        if ( !"pom".equals( project.getPackaging() ) )
        {
            // ----------------------------------------------------------------------------
            // Project artifact
            // ----------------------------------------------------------------------------

            Artifact artifact = project.getArtifact();

            File file = artifact.getFile();

            if ( file != null && file.isFile() )
            {
                filesToSign.add( new SigningBundle( artifact.getArtifactHandler().getExtension(), file ) );
            }
            else if ( project.getAttachedArtifacts().isEmpty() )
            {
                throw new MojoFailureException( "The project artifact has not been assembled yet. "
                    + "Please do not invoke this goal before the lifecycle phase \"package\"." );
            }
            else
            {
                getLog().debug( "Main artifact not assembled, skipping signature generation" );
            }
        }

        // ----------------------------------------------------------------------------
        // POM
        // ----------------------------------------------------------------------------

        File pomToSign = new File( project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".pom" );

        try
        {
            FileUtils.copyFile( project.getFile(), pomToSign );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error copying POM for signing.", e );
        }

        filesToSign.add( new SigningBundle( "pom", pomToSign ) );

        // ----------------------------------------------------------------------------
        // Attached artifacts
        // ----------------------------------------------------------------------------

        for ( Object o : project.getAttachedArtifacts() )
        {
            Artifact artifact = (Artifact) o;

            File file = artifact.getFile();

            if ( isExcluded( artifact ) )
            {
                getLog().debug( "Skipping generation of signature for excluded " + file );
                continue;
            }

            filesToSign.add( new SigningBundle( artifact.getArtifactHandler().getExtension(),
                                                artifact.getClassifier(), file ) );
        }

        // ----------------------------------------------------------------------------
        // Sign the filesToSign and attach all the signatures
        // ----------------------------------------------------------------------------

        try
        {
            KeylessSigner signer = KeylessSigner.builder().sigstoreStagingDefaults().build();
            for ( SigningBundle bundleToSign : filesToSign )
            {
                if ( wait > 0 )
                {
                    getLog().info( "waiting for " + wait + " seconds before signing" );
                    Thread.sleep( wait * 1000 );
                }

                File fileToSign = bundleToSign.getSignature(); // reusing original GPG implementation where it's the signature: TODO change

                KeylessSignature signature = signer.signFile( fileToSign.toPath() );

                // sigstore signature in bundle format (json string)
                String sigstoreBundle = BundleFactory.createBundle( signature );

                File signatureFile = new File( fileToSign + ".sigstore" );
                FileUtils.fileWrite( signatureFile, "UTF-8", sigstoreBundle );

                projectHelper.attachArtifact( project, bundleToSign.getExtension() + ".sigstore",
                                            bundleToSign.getClassifier(), signatureFile );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error while signing with sigstore", e );
        }
    }

    /**
     * Tests whether or not a name matches against at least one exclude pattern.
     *
     * @param artifact The artifact to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against at least one exclude pattern, or <code>false</code>
     *         otherwise.
     */
    protected boolean isExcluded( Artifact artifact )
    {
        final Path projectBasePath = project.getBasedir().toPath();
        final Path artifactPath = artifact.getFile().toPath();
        final String relativeArtifactPath = projectBasePath.relativize( artifactPath ).toString();

        for ( String exclude : excludes )
        {
            if ( SelectorUtils.matchPath( exclude, relativeArtifactPath ) )
            {
                return true;
            }
        }

        return false;
    }

}
