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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the semver value of GPG.
 * This is in most cases the first line of <code>gpg --version</code>
 * 
 *
 * @author Robert Scholte
 * @since 3.0.0
 */
public class GpgVersion implements Comparable<GpgVersion>
{
    private final String rawVersion;

    private GpgVersion( String rawVersion )
    {
        this.rawVersion = rawVersion;
    }

    public static GpgVersion parse( String rawVersion )
    {
        return new GpgVersion( rawVersion );
    }

    @Override
    public int compareTo( GpgVersion other )
    {
        Pattern p = Pattern.compile( "([.\\d]+)$" );

        String[] thisSegments;
        Matcher m = p.matcher( rawVersion );
        if ( m.find() )
        {
            thisSegments  = m.group( 1 ).split( "\\." );
        }
        else
        {
          throw new IllegalArgumentException( "Can't parse version of " + this.rawVersion );
        }

        String[] otherSegments;
        m = p.matcher( other.rawVersion );
        if ( m.find() )
        {
            otherSegments  = m.group( 1 ).split( "\\." );
        }
        else
        {
          throw new IllegalArgumentException( "Can't parse version of " + other.rawVersion );
        }

        int minSegments = Math.min( thisSegments.length, otherSegments.length );

        for ( int index = 0; index < minSegments; index++ )
        {
            int thisValue = Integer.parseInt( thisSegments[index] );

            int otherValue = Integer.parseInt( otherSegments[index] );

            int compareValue = Integer.compare( thisValue, otherValue );

            if ( compareValue != 0 )
            {
                return compareValue;
            }
        }

        return ( thisSegments.length - otherSegments.length );
    }

    /**
     * Verify if this version is before some other version
     *
     * @param other the version to compare with
     * @return {@code true} is this is less than {@code other}, otherwise {@code false}
     */
    public boolean isBefore( GpgVersion other )
    {
        return this.compareTo( other ) < 0;
    }

    /**
     * Verify if this version is before some other version
     *
     * @param other the version to compare with
     * @return {@code true}  is this is less than {@code other}, otherwise {@code false}
     */
    public boolean isBefore( String other )
    {
        return this.compareTo( parse( other ) ) < 0;
    }

    /**
     * Verify if this version is at least some other version
     *
     * @param other the version to compare with
     * @return  {@code true}  is this is greater than or equal to {@code other}, otherwise {@code false}
     */
    public boolean isAtLeast( GpgVersion other )
    {
        return this.compareTo( other ) >= 0;
    }

    /**
     * Verify if this version is at least some other version
     *
     * @param other the version to compare with
     * @return  {@code true} is this is greater than or equal to {@code other}, otherwise {@code false}
     */
    public boolean isAtLeast( String other )
    {
        return this.compareTo( parse( other ) ) >= 0;
    }

    @Override
    public String toString()
    {
        return rawVersion;
    }

}
