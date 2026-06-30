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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Os;

/**
 * A signer implementation that uses the GnuPG command line executable.
 *
 * Uses ProcessBuilder for execution to avoid Windows cmd.exe nested quoting issues.
 * No external command-line parsing libraries needed - builds commands directly.
 * ProcessBuilder provides:
 * - Better path handling on all platforms
 * - No cmd.exe wrapper causing path concatenation
 * - Direct environment variable control
 * - Proper stdin/stdout/stderr streaming
 */
public class GpgSigner extends AbstractGpgSigner {
    public static final String NAME = "gpg";
    private final String executable;

    public GpgSigner(String executable) {
        this.executable = executable;
    }

    @Override
    public String signerName() {
        return NAME;
    }

    @Override
    public String getKeyInfo() {
        return keyname != null ? keyname : "default";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void generateSignatureForFile(File file, File signature) throws MojoExecutionException {
        String gpgExecutable = (executable != null && !executable.isEmpty())
                ? executable
                : "gpg" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : "");

        GpgVersionParser versionParser = GpgVersionParser.parse(executable);
        GpgVersion gpgVersion = versionParser.getGpgVersion();
        if (gpgVersion == null) {
            throw new MojoExecutionException("Could not determine gpg version");
        }

        getLog().debug("GPG Version: " + gpgVersion);

        List<String> command = new ArrayList<>();
        command.add(gpgExecutable);

        Map<String, String> environment = new HashMap<>();
        environment.put("MSYS_NO_PATHCONV", "1");

        if (args != null) {
            command.addAll(args);
        }

        if (homeDir != null) {
            command.add("--homedir");
            command.add(homeDir.getAbsolutePath());
        }

        if (gpgVersion.isBefore(GpgVersion.parse("2.1"))) {
            if (useAgent) {
                command.add("--use-agent");
            } else {
                command.add("--no-use-agent");
            }
        }

        InputStream in = null;
        if (null != passphrase) {
            if (gpgVersion.isAtLeast(GpgVersion.parse("2.0"))) {
                // required for option --passphrase-fd since GPG 2.0
                command.add("--batch");
            }

            if (gpgVersion.isAtLeast(GpgVersion.parse("2.1"))) {
                // required for option --passphrase-fd since GPG 2.1
                command.add("--pinentry-mode");
                command.add("loopback");
            }

            // make --passphrase-fd effective in gpg2
            command.add("--passphrase-fd");
            command.add("0");

            // Prepare the STDIN stream which will be used to pass the passphrase to the executable
            // but obey terminatePassphrase: append LF if asked for
            if (terminatePassphrase && !passphrase.endsWith("\n")) {
                in = new ByteArrayInputStream((passphrase + "\n").getBytes());
            } else {
                in = new ByteArrayInputStream(passphrase.getBytes());
            }
        }

        if (null != keyname) {
            command.add("--local-user");
            command.add(keyname);
        }

        command.add("--armor");
        command.add("--detach-sign");

        if (getLog().isDebugEnabled()) {
            // instruct GPG to write status information to stdout
            command.add("--status-fd");
            command.add("1");
        }

        if (!isInteractive) {
            command.add("--batch");
            command.add("--no-tty");

            if (null == passphrase && gpgVersion.isAtLeast(GpgVersion.parse("2.1"))) {
                // prevent GPG from spawning input prompts in Maven non-interactive mode
                command.add("--pinentry-mode");
                command.add("error");
            }
        }

        if (!defaultKeyring) {
            command.add("--no-default-keyring");
        }

        if (secretKeyring != null && !secretKeyring.isEmpty()) {
            if (gpgVersion.isBefore(GpgVersion.parse("2.1"))) {
                command.add("--secret-keyring");
                command.add(secretKeyring);
            } else {
                getLog().warn("'secretKeyring' is an obsolete option and ignored. All secret keys "
                        + "are stored in the 'private-keys-v1.d' directory below the GnuPG home directory.");
            }
        }

        if (publicKeyring != null && !publicKeyring.isEmpty()) {
            command.add("--keyring");
            command.add(publicKeyring);
        }

        if ("once".equalsIgnoreCase(lockMode)) {
            command.add("--lock-once");
        } else if ("multiple".equalsIgnoreCase(lockMode)) {
            command.add("--lock-multiple");
        } else if ("never".equalsIgnoreCase(lockMode)) {
            command.add("--lock-never");
        }

        command.add("--output");
        command.add(signature.getAbsolutePath());

        command.add(file.getAbsolutePath());

        getLog().debug("CMD: " + String.join(" ", command));

        try {
            ProcessExecutor executor = new ProcessExecutor(getLog());
            executor.execute(
                    command,
                    environment,
                    in,
                    line -> {
                        if (getLog().isDebugEnabled()) {
                            getLog().debug("[GPG stdout] " + line);
                        }
                    },
                    line -> {
                        if (getLog().isWarnEnabled()) {
                            getLog().warn("[GPG stderr] " + line);
                        }
                    });
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to execute gpg command", e);
        }
    }
}
