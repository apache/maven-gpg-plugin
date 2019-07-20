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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 *
 * @author Robert Scholte
 * @since 3.0.0
 */
public class GpgVersionParser
{
    private final GpgVersionConsumer consumer;

    private GpgVersionParser( GpgVersionConsumer consumer )
    {
        this.consumer = consumer;

    }

    public static GpgVersionParser parse( String executable )
    {
        Commandline cmd = new Commandline();

        if ( StringUtils.isNotEmpty( executable ) )
        {
            cmd.setExecutable( executable );
        }
        else
        {
            cmd.setExecutable( "gpg" + ( Os.isFamily( Os.FAMILY_WINDOWS ) ? ".exe" : "" ) );
        }


        cmd.createArg().setValue( "--version" );

        GpgVersionConsumer out = new GpgVersionConsumer();

        try
        {
           CommandLineUtils.executeCommandLine( cmd, null, out, null );
        }
        catch ( CommandLineException e )
        {
            // TODO probably a dedicated exception
        }

        return new GpgVersionParser( out );
    }

    public GpgVersion getGpgVersion()
    {
        return consumer.gpgVersion;

    }

    /**
     * Consumes the output of {@code gpg --version}
     *
     * @author Robert Scholte
     * @since 3.0.0
     */
    static class GpgVersionConsumer
        implements StreamConsumer
    {
        private final Pattern gpgVersionPattern = Pattern.compile( "gpg \\(GnuPG.*\\) .+" );

        private GpgVersion gpgVersion;

        @Override
        public void consumeLine( String line )
            throws IOException
        {
            Matcher m = gpgVersionPattern.matcher( line );
            if ( m.matches() )
            {
                gpgVersion = GpgVersion.parse( m.group() );
            }
        }
    }

}
