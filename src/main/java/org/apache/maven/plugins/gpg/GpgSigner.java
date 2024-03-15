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

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.DefaultConsumer;

/**
 * A signer implementation that uses the GnuPG command line executable.
 */
public class GpgSigner extends AbstractGpgSigner {
    public static final String NAME = "gpg";
    private String executable;

    public GpgSigner(String executable) {
        this.executable = executable;
    }

    @Override
    public String signerName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void generateSignatureForFile(File file, File signature) throws MojoExecutionException {
        // ----------------------------------------------------------------------------
        // Set up the command line
        // ----------------------------------------------------------------------------

        Commandline cmd = new Commandline();

        if (executable != null && !executable.isEmpty()) {
            cmd.setExecutable(executable);
        } else {
            cmd.setExecutable("gpg" + (Os.isFamily(Os.FAMILY_WINDOWS) ? ".exe" : ""));
        }

        GpgVersionParser versionParser = GpgVersionParser.parse(executable);

        GpgVersion gpgVersion = versionParser.getGpgVersion();
        if (gpgVersion == null) {
            throw new MojoExecutionException("Could not determine gpg version");
        }

        getLog().debug(gpgVersion.toString());

        if (args != null) {
            for (String arg : args) {
                cmd.createArg().setValue(arg);
            }
        }

        if (homeDir != null) {
            cmd.createArg().setValue("--homedir");
            cmd.createArg().setFile(homeDir);
        }

        if (gpgVersion.isBefore(GpgVersion.parse("2.1"))) {
            if (useAgent) {
                cmd.createArg().setValue("--use-agent");
            } else {
                cmd.createArg().setValue("--no-use-agent");
            }
        }

        InputStream in = null;
        if (null != passphrase) {
            if (gpgVersion.isAtLeast(GpgVersion.parse("2.0"))) {
                // required for option --passphrase-fd since GPG 2.0
                cmd.createArg().setValue("--batch");
            }

            if (gpgVersion.isAtLeast(GpgVersion.parse("2.1"))) {
                // required for option --passphrase-fd since GPG 2.1
                cmd.createArg().setValue("--pinentry-mode");
                cmd.createArg().setValue("loopback");
            }

            // make --passphrase-fd effective in gpg2
            cmd.createArg().setValue("--passphrase-fd");
            cmd.createArg().setValue("0");

            // Prepare the input stream which will be used to pass the passphrase to the executable
            if (!passphrase.endsWith(System.lineSeparator())) {
                in = new ByteArrayInputStream((passphrase + System.lineSeparator()).getBytes());
            } else {
                in = new ByteArrayInputStream(passphrase.getBytes());
            }
        }

        if (null != keyname) {
            cmd.createArg().setValue("--local-user");

            cmd.createArg().setValue(keyname);
        }

        cmd.createArg().setValue("--armor");

        cmd.createArg().setValue("--detach-sign");

        if (getLog().isDebugEnabled()) {
            // instruct GPG to write status information to stdout
            cmd.createArg().setValue("--status-fd");
            cmd.createArg().setValue("1");
        }

        if (!isInteractive) {
            cmd.createArg().setValue("--batch");
            cmd.createArg().setValue("--no-tty");

            if (null == passphrase && gpgVersion.isAtLeast(GpgVersion.parse("2.1"))) {
                // prevent GPG from spawning input prompts in Maven non-interactive mode
                cmd.createArg().setValue("--pinentry-mode");
                cmd.createArg().setValue("error");
            }
        }

        if (!defaultKeyring) {
            cmd.createArg().setValue("--no-default-keyring");
        }

        if (secretKeyring != null && !secretKeyring.isEmpty()) {
            if (gpgVersion.isBefore(GpgVersion.parse("2.1"))) {
                cmd.createArg().setValue("--secret-keyring");
                cmd.createArg().setValue(secretKeyring);
            } else {
                getLog().warn("'secretKeyring' is an obsolete option and ignored. All secret keys "
                        + "are stored in the ‘private-keys-v1.d’ directory below the GnuPG home directory.");
            }
        }

        if (publicKeyring != null && !publicKeyring.isEmpty()) {
            cmd.createArg().setValue("--keyring");
            cmd.createArg().setValue(publicKeyring);
        }

        if ("once".equalsIgnoreCase(lockMode)) {
            cmd.createArg().setValue("--lock-once");
        } else if ("multiple".equalsIgnoreCase(lockMode)) {
            cmd.createArg().setValue("--lock-multiple");
        } else if ("never".equalsIgnoreCase(lockMode)) {
            cmd.createArg().setValue("--lock-never");
        }

        cmd.createArg().setValue("--output");
        cmd.createArg().setFile(signature);

        cmd.createArg().setFile(file);

        // ----------------------------------------------------------------------------
        // Execute the command line
        // ----------------------------------------------------------------------------

        getLog().debug("CMD: " + cmd);

        try {
            int exitCode = CommandLineUtils.executeCommandLine(cmd, in, new DefaultConsumer(), new DefaultConsumer());

            if (exitCode != 0) {
                throw new MojoExecutionException("Exit code: " + exitCode);
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Unable to execute gpg command", e);
        }
    }
}
