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

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Parse the output of <code>gpg --version</code> and exposes these as dedicated objects.
 *
 * Supported:
 * <ul>
 *   <li>gpg version, i.e. gpg (GnuPG) 2.2.15</li>
 * </ul>
 * Unsupported:
 * <ul>
 *   <li>libgcrypt version</li>
 *   <li>Home</li>
 *   <li>Supported algorithms (Pubkey, Cipher, Hash, Compression)</li>
 * </ul>
 *
 * @author Robert Scholte
 * @since 3.0.0
 */
public class GpgVersionParser {
    private final GpgVersionConsumer consumer;

    private GpgVersionParser(GpgVersionConsumer consumer) {
        this.consumer = consumer;
    }

    public static GpgVersionParser parse(String executable) throws MojoExecutionException {
        Commandline cmd = new Commandline();

        if (executable != null && !executable.isEmpty()) {
            cmd.setExecutable(executable);
        } else {
            cmd.setExecutable("gpg" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : ""));
        }

        cmd.createArg().setValue("--version");

        GpgVersionConsumer out = new GpgVersionConsumer();

        try {
            CommandLineUtils.executeCommandLine(cmd, null, out, null);
        } catch (CommandLineException e) {
            throw new MojoExecutionException("failed to execute gpg", e);
        }

        return new GpgVersionParser(out);
    }

    public GpgVersion getGpgVersion() {
        return consumer.getGpgVersion();
    }

    /**
     * Consumes the output of {@code gpg --version}
     *
     * @author Robert Scholte
     * @since 3.0.0
     */
    static class GpgVersionConsumer implements StreamConsumer {
        private final Pattern gpgVersionPattern = Pattern.compile("gpg \\([^)]+\\) .+");

        private GpgVersion gpgVersion;

        @Override
        public void consumeLine(String line) throws IOException {
            Matcher m = gpgVersionPattern.matcher(line);
            if (m.matches()) {
                gpgVersion = GpgVersion.parse(m.group());
            }
        }

        public GpgVersion getGpgVersion() {
            return gpgVersion;
        }
    }
}
