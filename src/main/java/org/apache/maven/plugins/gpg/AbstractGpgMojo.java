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

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @author Benjamin Bentmann
 */
public abstract class AbstractGpgMojo extends AbstractMojo {
    public static final String DEFAULT_ENV_MAVEN_GPG_KEY = "MAVEN_GPG_KEY";
    public static final String DEFAULT_ENV_MAVEN_GPG_FINGERPRINT = "MAVEN_GPG_KEY_FINGERPRINT";
    public static final String DEFAULT_ENV_MAVEN_GPG_PASSPHRASE = "MAVEN_GPG_PASSPHRASE";

    /**
     * BC Signer only: The comma separate list of Unix Domain Socket paths, to use to communicate with GnuPG agent.
     * If relative, they are resolved against user home directory.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.agentSocketLocations", defaultValue = ".gnupg/S.gpg-agent")
    private String agentSocketLocations;

    /**
     * BC Signer only: The path of the exported key in TSK format, and probably passphrase protected. If relative,
     * the file is resolved against Maven local repository root.
     * <p>
     * <em>Note: it is not recommended to have sensitive files on disk or SCM repository, this mode is more to be used
     * in local environment (workstations) or for testing purposes.</em>
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyFilePath", defaultValue = "maven-signing-key.key")
    private String keyFilePath;

    /**
     * BC Signer only: The fingerprint of the key to use for signing. If not given, first key in keyring will be used.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyFingerprint")
    private String keyFingerprint;

    /**
     * BC Signer only: The env variable name where the GnuPG key is set. The default value is {@code MAVEN_GPG_KEY}.
     * To use BC Signer you must provide GnuPG key, as it does not use GnuPG home directory to extract/find the
     * key (while it does use GnuPG Agent to ask for password in interactive mode).
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_KEY)
    private String keyEnvName;

    /**
     * BC Signer only: The env variable name where the GnuPG key fingerprint is set, if the provided keyring contains
     * multiple keys. The default value is {@code MAVEN_GPG_KEY_FINGERPRINT}.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyFingerprintEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_FINGERPRINT)
    private String keyFingerprintEnvName;

    /**
     * The env variable name where the GnuPG passphrase is set. The default value is {@code MAVEN_GPG_PASSPHRASE}.
     * This is the recommended way to pass passphrase for signing in batch mode execution of Maven.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.passphraseEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_PASSPHRASE)
    private String passphraseEnvName;

    /**
     * GPG Signer only: The directory from which gpg will load keyrings. If not specified, gpg will use the value configured for its
     * installation, e.g. <code>~/.gnupg</code> or <code>%APPDATA%/gnupg</code>.
     *
     * @since 1.0
     */
    @Parameter(property = "gpg.homedir")
    private File homedir;

    /**
     * The passphrase to use when signing. If not given, look up the value under Maven
     * settings using server id at 'passphraseServerKey' configuration. <em>Do not use this parameter, if set, the
     * plugin will fail. Passphrase should be provided only via gpg-agent (interactive) or via env variable
     * (non-interactive).</em>
     *
     * @deprecated Do not use this configuration, plugin will fail if set.
     **/
    @Deprecated
    @Parameter(property = "gpg.passphrase")
    private String passphrase;

    /**
     * Server id to lookup the passphrase under Maven settings. <em>Do not use this parameter, if set, the
     * plugin will fail. Passphrase should be provided only via gpg-agent (interactive) or via env variable
     * (non-interactive).</em>
     *
     * @since 1.6
     * @deprecated Do not use this configuration, plugin will fail if set.
     **/
    @Deprecated
    @Parameter(property = "gpg.passphraseServerId")
    private String passphraseServerId;

    /**
     * GPG Signer only: The "name" of the key to sign with. Passed to gpg as <code>--local-user</code>.
     */
    @Parameter(property = "gpg.keyname")
    private String keyname;

    /**
     * GPG Signer only: Passes <code>--use-agent</code> or <code>--no-use-agent</code> to gpg. If using an agent, the
     * passphrase is optional as the agent will provide it. For gpg2, specify true as --no-use-agent was removed in
     * gpg2 and doesn't ask for a passphrase anymore. Deprecated, and better to rely on session "interactive" setting
     * (if interactive, agent will be used, otherwise not).
     *
     * @deprecated
     */
    @Deprecated
    @Parameter(property = "gpg.useagent", defaultValue = "true")
    private boolean useAgent;

    /**
     * Detect is session interactive or not.
     */
    @Parameter(defaultValue = "${settings.interactiveMode}", readonly = true)
    private boolean interactive;

    /**
     * GPG Signer only: The path to the GnuPG executable to use for artifact signing. Defaults to either "gpg" or
     * "gpg.exe" depending on the operating system.
     *
     * @since 1.1
     */
    @Parameter(property = "gpg.executable")
    private String executable;

    /**
     * GPG Signer only: Whether to add the default keyrings from gpg's home directory to the list of used keyrings.
     *
     * @since 1.2
     */
    @Parameter(property = "gpg.defaultKeyring", defaultValue = "true")
    private boolean defaultKeyring;

    /**
     * GPG Signer only: The path to a secret keyring to add to the list of keyrings. By default, only the
     * {@code secring.gpg} from gpg's home directory is considered. Use this option (in combination with
     * {@link #publicKeyring} and {@link #defaultKeyring} if required) to use a different secret key.
     * <em>Note:</em> Relative paths are resolved against gpg's home directory, not the project base directory.
     * <p>
     * <strong>NOTE: </strong>As of gpg 2.1 this is an obsolete option and ignored. All secret keys are stored in the
     * ‘private-keys-v1.d’ directory below the GnuPG home directory.
     *
     * @since 1.2
     * @deprecated
     */
    @Deprecated
    @Parameter(property = "gpg.secretKeyring")
    private String secretKeyring;

    /**
     * GPG Signer only: The path to a public keyring to add to the list of keyrings. By default, only the
     * {@code pubring.gpg} from gpg's home directory is considered. Use this option (and {@link #defaultKeyring}
     * if required) to use a different public key. <em>Note:</em> Relative paths are resolved against gpg's home
     * directory, not the project base directory.
     * <p>
     * <strong>NOTE: </strong>As of gpg 2.1 this is an obsolete option and ignored. All public keys are stored in the
     * ‘pubring.kbx’ file below the GnuPG home directory.
     *
     * @since 1.2
     * @deprecated
     */
    @Deprecated
    @Parameter(property = "gpg.publicKeyring")
    private String publicKeyring;

    /**
     * GPG Signer only: The lock mode to use when invoking gpg. By default no lock mode will be specified. Valid
     * values are {@code once}, {@code multiple} and {@code never}. The lock mode gets translated into the
     * corresponding {@code --lock-___} command line argument. Improper usage of this option may lead to data and
     * key corruption.
     *
     * @see <a href="http://www.gnupg.org/documentation/manuals/gnupg/GPG-Configuration-Options.html">the
     *      --lock-options</a>
     * @since 1.5
     */
    @Parameter(property = "gpg.lockMode")
    private String lockMode;

    /**
     * Skip doing the gpg signing.
     */
    @Parameter(property = "gpg.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Sets the arguments to be passed to gpg. Example:
     *
     * <pre>
     * &lt;gpgArguments&gt;
     *   &lt;arg&gt;--no-random-seed-file&lt;/arg&gt;
     *   &lt;arg&gt;--no-permission-warning&lt;/arg&gt;
     * &lt;/gpgArguments&gt;
     * </pre>
     *
     * @since 1.5
     */
    @Parameter
    private List<String> gpgArguments;

    /**
     * The name of the Signer implementation to use. Accepted values are {@code "gpg"} (the default, uses GnuPG
     * executable) and {@code "bc"} (uses Bouncy Castle pure Java signer).
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.signer", defaultValue = GpgSigner.NAME)
    private String signer;

    /**
     * @since 3.0.0
     */
    @Component
    protected MavenSession session;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            // We're skipping the signing stuff
            return;
        }
        if ((passphrase != null && !passphrase.trim().isEmpty())
                || (passphraseServerId != null && !passphraseServerId.trim().isEmpty())) {
            // Stop propagating worst practices: passphrase MUST NOT be in any file on disk
            throw new MojoFailureException(
                    "Do not store passphrase in any file (disk or SCM repository), rely on GnuPG agent or provide passphrase in "
                            + passphraseEnvName + " environment variable.");
        }

        doExecute();
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected AbstractGpgSigner newSigner() throws MojoFailureException {
        AbstractGpgSigner signer;
        if (GpgSigner.NAME.equals(this.signer)) {
            signer = new GpgSigner(executable);
        } else if (BcSigner.NAME.equals(this.signer)) {
            signer = new BcSigner(
                    session.getRepositorySession(),
                    keyEnvName,
                    keyFingerprintEnvName,
                    agentSocketLocations,
                    keyFilePath,
                    keyFingerprint);
        } else {
            throw new MojoFailureException("Unknown signer: " + this.signer);
        }

        signer.setLog(getLog());
        signer.setInteractive(interactive);
        signer.setKeyName(keyname);
        signer.setUseAgent(useAgent);
        signer.setHomeDirectory(homedir);
        signer.setDefaultKeyring(defaultKeyring);
        signer.setSecretKeyring(secretKeyring);
        signer.setPublicKeyring(publicKeyring);
        signer.setLockMode(lockMode);
        signer.setArgs(gpgArguments);

        String passphrase =
                (String) session.getRepositorySession().getConfigProperties().get("env." + passphraseEnvName);
        if (passphrase != null) {
            signer.setPassPhrase(passphrase);
        }

        if (null == passphrase && !useAgent) {
            if (!interactive) {
                throw new MojoFailureException("Cannot obtain passphrase in batch mode");
            }
        }

        signer.prepare();

        return signer;
    }
}
