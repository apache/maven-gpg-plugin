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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * A wrapper class for attached artifacts which have a GPG signature. Needed as attached artifacts in general do not
 * have metadata.
 */
public class AttachedSignedArtifact
    implements Artifact
{
    private final Artifact delegate;

    private final AscArtifactMetadata signature;

    public AttachedSignedArtifact( Artifact delegate, AscArtifactMetadata signature )
    {
        this.delegate = delegate;
        this.signature = signature;
    }

    @Override
    public void setArtifactId( String artifactId )
    {
        delegate.setArtifactId( artifactId );
    }

    @Override
    public List<ArtifactVersion> getAvailableVersions()
    {
        return delegate.getAvailableVersions();
    }

    @Override
    public void setAvailableVersions( List<ArtifactVersion> availableVersions )
    {
        delegate.setAvailableVersions( availableVersions );
    }

    @Override
    public String getBaseVersion()
    {
        return delegate.getBaseVersion();
    }

    @Override
    public void setBaseVersion( String baseVersion )
    {
        delegate.setBaseVersion( baseVersion );
    }

    @Override
    public String getDownloadUrl()
    {
        return delegate.getDownloadUrl();
    }

    @Override
    public void setDownloadUrl( String downloadUrl )
    {
        delegate.setDownloadUrl( downloadUrl );
    }

    @Override
    public void setGroupId( String groupId )
    {
        delegate.setGroupId( groupId );
    }

    @Override
    public ArtifactRepository getRepository()
    {
        return delegate.getRepository();
    }

    @Override
    public void setRepository( ArtifactRepository repository )
    {
        delegate.setRepository( repository );
    }

    @Override
    public String getScope()
    {
        return delegate.getScope();
    }

    @Override
    public void setScope( String scope )
    {
        delegate.setScope( scope );
    }

    @Override
    public String getVersion()
    {
        return delegate.getVersion();
    }

    @Override
    public void setVersion( String version )
    {
        delegate.setVersion( version );
    }

    @Override
    public VersionRange getVersionRange()
    {
        return delegate.getVersionRange();
    }

    @Override
    public void setVersionRange( VersionRange range )
    {
        delegate.setVersionRange( range );
    }

    @Override
    public boolean isRelease()
    {
        return delegate.isRelease();
    }

    @Override
    public void setRelease( boolean release )
    {
        delegate.setRelease( release );
    }

    @Override
    public boolean isSnapshot()
    {
        return delegate.isSnapshot();
    }

    @Override
    public void addMetadata( ArtifactMetadata metadata )
    {
        delegate.addMetadata( metadata );
    }

    @Override
    public String getClassifier()
    {
        return delegate.getClassifier();
    }

    @Override
    public boolean hasClassifier()
    {
        return delegate.hasClassifier();
    }

    @Override
    public String getGroupId()
    {
        return delegate.getGroupId();
    }

    @Override
    public String getArtifactId()
    {
        return delegate.getArtifactId();
    }

    @Override
    public String getType()
    {
        return delegate.getType();
    }

    @Override
    public void setFile( File file )
    {
        delegate.setFile( file );
    }

    @Override
    public File getFile()
    {
        return delegate.getFile();
    }

    @Override
    public String getId()
    {
        return delegate.getId();
    }

    @Override
    public String getDependencyConflictId()
    {
        return delegate.getDependencyConflictId();
    }

    @Override
    public String toString()
    {
        return delegate.toString();
    }

    @Override
    public int hashCode()
    {
        return delegate.hashCode();
    }

    @Override
    public boolean equals( Object o )
    {
        return delegate.equals( o );
    }

    @Override
    public void updateVersion( String version, ArtifactRepository localRepository )
    {
        delegate.updateVersion( version, localRepository );
    }

    @Override
    public ArtifactFilter getDependencyFilter()
    {
        return delegate.getDependencyFilter();
    }

    @Override
    public void setDependencyFilter( ArtifactFilter artifactFilter )
    {
        delegate.setDependencyFilter( artifactFilter );
    }

    @Override
    public ArtifactHandler getArtifactHandler()
    {
        return delegate.getArtifactHandler();
    }

    @Override
    public List<String> getDependencyTrail()
    {
        return delegate.getDependencyTrail();
    }

    @Override
    public void setDependencyTrail( List<String> dependencyTrail )
    {
        delegate.setDependencyTrail( dependencyTrail );
    }

    @Override
    public void selectVersion( String version )
    {
        delegate.selectVersion( version );
    }

    @Override
    public void setResolved( boolean resolved )
    {
        delegate.setResolved( resolved );
    }

    @Override
    public boolean isResolved()
    {
        return delegate.isResolved();
    }

    @Override
    public void setResolvedVersion( String version )
    {
        delegate.setResolvedVersion( version );
    }

    @Override
    public void setArtifactHandler( ArtifactHandler artifactHandler )
    {
        delegate.setArtifactHandler( artifactHandler );
    }

    @Override
    public boolean isOptional()
    {
        return delegate.isOptional();
    }

    @Override
    public ArtifactVersion getSelectedVersion()
        throws OverConstrainedVersionException
    {
        return delegate.getSelectedVersion();
    }

    @Override
    public boolean isSelectedVersionKnown()
        throws OverConstrainedVersionException
    {
        return delegate.isSelectedVersionKnown();
    }

    @Override
    public void setOptional( boolean optional )
    {
        delegate.setOptional( optional );
    }

    @Override
    public Collection<ArtifactMetadata> getMetadataList()
    {
        List<ArtifactMetadata> result = new ArrayList<>( delegate.getMetadataList() );

        for ( ArtifactMetadata metadata : result )
        {
            if ( signature.getKey().equals( metadata.getKey() ) )
            {
                // already signed
                return result;
            }
        }

        result.add( signature );

        return result;
    }

    @Override
    public int compareTo( Artifact o )
    {
        return delegate.compareTo( o );
    }

    @Override
    public ArtifactMetadata getMetadata( Class<?> metadataClass )
    {
        // TODO Auto-generated method stub
        return delegate.getMetadata( metadataClass );
    }
}
