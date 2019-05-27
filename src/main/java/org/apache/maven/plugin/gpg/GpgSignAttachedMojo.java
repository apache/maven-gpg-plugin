package org.apache.maven.plugin.gpg;

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
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
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

/**
 * Sign project artifact, the POM, and attached artifacts with GnuPG for deployment.
 *
 * @author Jason van Zyl
 * @author Jason Dillon
 * @author Daniel Kulp
 */
@Mojo( name = "sign", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true )
public class GpgSignAttachedMojo
    extends AbstractGpgMojo
{

    private static final String DEFAULT_EXCLUDES[] = new String[] { "**/*.md5", "**/*.sha1", "**/*.asc" };

    /**
     * Skip doing the gpg signing.
     */
    @Parameter( property = "gpg.skip", defaultValue = "false" )
    private boolean skip;

    /**
     * A list of files to exclude from being signed. Can contain Ant-style wildcards and double wildcards. The default
     * excludes are <code>**&#47;*.md5   **&#47;*.sha1    **&#47;*.asc</code>.
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
    @Parameter( defaultValue = "${project.build.directory}/gpg", alias = "outputDirectory" )
    private File ascDirectory;

    /**
     * The maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

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

        AbstractGpgSigner signer = newSigner( project );

        // ----------------------------------------------------------------------------
        // What we need to generateSignatureForArtifact here
        // ----------------------------------------------------------------------------

        signer.setOutputDirectory( ascDirectory );
        signer.setBuildDirectory( new File( project.getBuild().getDirectory() ) );
        signer.setBaseDirectory( project.getBasedir() );

        List<SigningBundle> signingBundles = new ArrayList<>();

        if ( !"pom".equals( project.getPackaging() ) )
        {
            // ----------------------------------------------------------------------------
            // Project artifact
            // ----------------------------------------------------------------------------

            Artifact artifact = project.getArtifact();

            File file = artifact.getFile();

            if ( file != null && file.isFile() )
            {
                getLog().debug( "Generating signature for " + file );

                File projectArtifactSignature = signer.generateSignatureForArtifact( file );

                if ( projectArtifactSignature != null )
                {
                    signingBundles.add( new SigningBundle( artifact.getArtifactHandler().getExtension(),
                                                           artifact.getClassifier(),
                                                           projectArtifactSignature ) );
                }
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

        getLog().debug( "Generating signature for " + pomToSign );

        File pomSignature = signer.generateSignatureForArtifact( pomToSign );

        if ( pomSignature != null )
        {
            signingBundles.add( new SigningBundle( "pom", pomSignature ) );
        }

        // ----------------------------------------------------------------------------
        // Attached artifacts
        // ----------------------------------------------------------------------------

        for ( Object o : project.getAttachedArtifacts() )
        {
            Artifact artifact = (Artifact) o;

            File file = artifact.getFile();

            getLog().debug( "Generating signature for " + file );

            File signature = signer.generateSignatureForArtifact( file );

            if ( signature != null )
            {
                signingBundles.add( new SigningBundle( artifact.getArtifactHandler().getExtension(),
                                                       artifact.getClassifier(), signature ) );
            }
        }

        // ----------------------------------------------------------------------------
        // Attach all the signatures
        // ----------------------------------------------------------------------------

        for ( SigningBundle bundle : signingBundles )
        {
            projectHelper.attachArtifact( project, bundle.getExtension() + AbstractGpgSigner.SIGNATURE_EXTENSION,
                                          bundle.getClassifier(), bundle.getSignature() );
        }
    }

    /**
     * Tests whether or not a name matches against at least one exclude pattern.
     *
     * @param name The name to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against at least one exclude pattern, or <code>false</code>
     *         otherwise.
     */
    protected boolean isExcluded( String name )
    {
        for ( String exclude : excludes )
        {
            if ( SelectorUtils.matchPath( exclude, name ) )
            {
                return true;
            }
        }
        return false;
    }

}
