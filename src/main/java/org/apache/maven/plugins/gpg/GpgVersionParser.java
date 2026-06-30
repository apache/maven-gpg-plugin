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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.Os;

/**
 * Parse the output of <code>gpg --version</code> and exposes these as dedicated objects.
 *
 * Uses ProcessBuilder for execution to avoid Windows cmd.exe nested quoting issues,
 * matching GpgSigner implementation. Direct ProcessBuilder usage without external
 * command-line parsing libraries.
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
        String gpgExecutable = (executable != null && !executable.isEmpty())
                ? executable
                : "gpg" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : "");

        List<String> command = new ArrayList<>();
        command.add(gpgExecutable);
        command.add("--version");

        Map<String, String> environment = new HashMap<>();

        GpgVersionConsumer out = new GpgVersionConsumer();

        try {
            ProcessExecutor executor = new ProcessExecutor(new Log() {
                @Override
                public boolean isDebugEnabled() {
                    return false;
                }

                @Override
                public void debug(CharSequence content) {}

                @Override
                public void debug(CharSequence content, Throwable error) {}

                @Override
                public void debug(Throwable error) {}

                @Override
                public boolean isInfoEnabled() {
                    return false;
                }

                @Override
                public void info(CharSequence content) {}

                @Override
                public void info(CharSequence content, Throwable error) {}

                @Override
                public void info(Throwable error) {}

                @Override
                public boolean isWarnEnabled() {
                    return false;
                }

                @Override
                public void warn(CharSequence content) {}

                @Override
                public void warn(CharSequence content, Throwable error) {}

                @Override
                public void warn(Throwable error) {}

                @Override
                public boolean isErrorEnabled() {
                    return false;
                }

                @Override
                public void error(CharSequence content) {}

                @Override
                public void error(CharSequence content, Throwable error) {}

                @Override
                public void error(Throwable error) {}
            });

            List<String> output = executor.executeAndCollectOutput(command, environment);

            for (String line : output) {
                out.consumeLine(line);
            }
        } catch (ProcessExecutor.ProcessExecutionException e) {
            throw new MojoExecutionException("gpg --version exited with exit code: " + e.getExitCode(), e);
        } catch (IOException | InterruptedException e) {
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
    static class GpgVersionConsumer {
        private final Pattern gpgVersionPattern = Pattern.compile("gpg \\([^)]+\\) .+");

        private GpgVersion gpgVersion;

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
