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
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

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
     * BC Signer only: The path of the exported key in
     * <a href="https://openpgp.dev/book/private_keys.html#transferable-secret-key-format">TSK format</a>,
     * and may be passphrase protected. If relative, the file is resolved against user home directory.
     * <p>
     * <em>Note: it is not recommended to have sensitive files checked into SCM repository. Key file should reside on
     * developer workstation, outside of SCM tracked repository. For CI-like use cases you should set the
     * key material as env variable instead.</em>
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
     * BC Signer only: The env variable name where the GnuPG key is set.
     * To use BC Signer you must provide GnuPG key, as it does not use GnuPG home directory to extract/find the
     * key (while it does use GnuPG Agent to ask for password in interactive mode). The key should be in
     * <a href="https://openpgp.dev/book/private_keys.html#transferable-secret-key-format">TSK format</a> and may
     * be passphrase protected.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_KEY)
    private String keyEnvName;

    /**
     * BC Signer only: The env variable name where the GnuPG key fingerprint is set, if the provided keyring contains
     * multiple keys.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.keyFingerprintEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_FINGERPRINT)
    private String keyFingerprintEnvName;

    /**
     * The env variable name where the GnuPG passphrase is set. This is the recommended way to pass passphrase
     * for signing in batch mode execution of Maven.
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
     * settings using server id at 'passphraseServerKey' configuration. <em>Do not use this parameter, it leaks
     * sensitive data. Passphrase should be provided only via gpg-agent or via env variable.
     * If parameter {@link #bestPractices} set to {@code true}, plugin fails when this parameter is configured.</em>
     *
     * @deprecated Do not use this configuration, it may leak sensitive information. Rely on gpg-agent or env
     * variables instead.
     **/
    @Deprecated
    @Parameter(property = GPG_PASSPHRASE)
    private String passphrase;

    /**
     * Server id to lookup the passphrase under Maven settings. <em>Do not use this parameter, it leaks
     * sensitive data. Passphrase should be provided only via gpg-agent or via env variable.
     * If parameter {@link #bestPractices} set to {@code true}, plugin fails when this parameter is configured.</em>
     *
     * @since 1.6
     * @deprecated Do not use this configuration, it may leak sensitive information. Rely on gpg-agent or env
     * variables instead.
     **/
    @Deprecated
    @Parameter(property = "gpg.passphraseServerId", defaultValue = GPG_PASSPHRASE)
    private String passphraseServerId;

    /**
     * GPG Signer only: The "name" of the key to sign with. Passed to gpg as <code>--local-user</code>.
     */
    @Parameter(property = "gpg.keyname")
    private String keyname;

    /**
     * All signers: whether gpg-agent is allowed to be used or not. If enabled, passphrase is optional, as agent may
     * provide it. Have to be noted, that in "batch" mode, gpg-agent will be prevented to pop up pinentry
     * dialogue, hence best is to "prime" the agent caches beforehand.
     * <p>
     * GPG Signer: Passes <code>--use-agent</code> or <code>--no-use-agent</code> option to gpg if it is version 2.1
     * or older. Otherwise, will use an agent. In non-interactive mode gpg options are appended with
     * <code>--pinentry-mode error</code>, preventing gpg agent to pop up pinentry dialogue. Agent will be able to
     * hand over only cached passwords.
     * <p>
     * BC Signer: Allows signer to communicate with gpg agent. In non-interactive mode it uses
     * <code>--no-ask</code> option with the <code>GET_PASSPHRASE</code> function. Agent will be able to hand over
     * only cached passwords.
     */
    @Parameter(property = "gpg.useagent", defaultValue = "true")
    private boolean useAgent;

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
     * @deprecated Obsolete option since GnuPG 2.1 version.
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
     * @deprecated Obsolete option since GnuPG 2.1 version.
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
     * GPG Signer only: Sets the arguments to be passed to gpg. Example:
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

    /**
     * Switch to improve plugin enforcement of "best practices". If set to {@code false}, plugin retains all the
     * backward compatibility regarding getting secrets (but will warn). If set to {@code true}, plugin will fail
     * if any "bad practices" regarding sensitive data handling are detected. By default, plugin remains backward
     * compatible (this flag is {@code false}). Somewhere in the future, when this parameter enabling transitioning
     * from older plugin versions is removed, the logic using this flag will be modified like it is set to {@code true}.
     * It is warmly advised to configure this parameter to {@code true} and migrate project and user environment
     * regarding how sensitive information is stored.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.bestPractices", defaultValue = "false")
    private boolean bestPractices;

    /**
     * Current user system settings for use in Maven.
     *
     * @since 1.6
     */
    @Parameter(defaultValue = "${settings}", readonly = true, required = true)
    protected Settings settings;

    /**
     * Maven Security Dispatcher.
     *
     * @since 1.6
     * @deprecated Provides quasi-encryption, should be avoided.
     */
    @Deprecated
    private final SecDispatcher secDispatcher =
            new DefaultSecDispatcher(new DefaultPlexusCipher(), Collections.emptyMap(), "~/.m2/settings-security.xml");

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            // We're skipping the signing stuff
            return;
        }
        if (bestPractices && (isNotBlank(passphrase) || isNotBlank(passphraseServerId))) {
            // Stop propagating worst practices: passphrase MUST NOT be in any file on disk
            throw new MojoFailureException(
                    "Do not store passphrase in any file (disk or SCM repository), rely on GnuPG agent or provide passphrase in "
                            + passphraseEnvName + " environment variable.");
        }

        doExecute();
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    private void logBestPracticeWarning(String source) {
        getLog().warn("");
        getLog().warn("W A R N I N G");
        getLog().warn("");
        getLog().warn("Do not store passphrase in any file (disk or SCM repository),");
        getLog().warn("instead rely on GnuPG agent or provide passphrase in ");
        getLog().warn(passphraseEnvName + " environment variable for batch mode.");
        getLog().warn("");
        getLog().warn("Sensitive content loaded from " + source);
        getLog().warn("");
    }

    protected AbstractGpgSigner newSigner(MavenProject mavenProject) throws MojoFailureException {
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
        signer.setInteractive(settings.isInteractiveMode());
        signer.setKeyName(keyname);
        signer.setUseAgent(useAgent);
        signer.setHomeDirectory(homedir);
        signer.setDefaultKeyring(defaultKeyring);
        signer.setSecretKeyring(secretKeyring);
        signer.setPublicKeyring(publicKeyring);
        signer.setLockMode(lockMode);
        signer.setArgs(gpgArguments);

        // "new way": env prevails
        String passphrase =
                (String) session.getRepositorySession().getConfigProperties().get("env." + passphraseEnvName);
        if (isNotBlank(passphrase)) {
            signer.setPassPhrase(passphrase);
        } else if (!bestPractices) {
            // "old way": mojo config
            passphrase = this.passphrase;
            if (isNotBlank(passphrase)) {
                logBestPracticeWarning("Mojo configuration");
                signer.setPassPhrase(passphrase);
            } else {
                // "old way": serverId + settings
                passphrase = loadGpgPassphrase();
                if (isNotBlank(passphrase)) {
                    logBestPracticeWarning("settings.xml");
                    signer.setPassPhrase(passphrase);
                } else {
                    // "old way": project properties
                    passphrase = getPassphrase(mavenProject);
                    if (isNotBlank(passphrase)) {
                        logBestPracticeWarning("Project properties");
                        signer.setPassPhrase(passphrase);
                    }
                }
            }
        }
        signer.prepare();

        return signer;
    }

    private boolean isNotBlank(String string) {
        return string != null && !string.trim().isEmpty();
    }

    // Below is attic, to be thrown out

    @Deprecated
    private static final String GPG_PASSPHRASE = "gpg.passphrase";

    @Deprecated
    private String loadGpgPassphrase() throws MojoFailureException {
        if (isNotBlank(passphraseServerId)) {
            Server server = settings.getServer(passphraseServerId);
            if (server != null) {
                if (isNotBlank(server.getPassphrase())) {
                    try {
                        return secDispatcher.decrypt(server.getPassphrase());
                    } catch (SecDispatcherException e) {
                        throw new MojoFailureException("Unable to decrypt gpg passphrase", e);
                    }
                }
            }
        }
        return null;
    }

    @Deprecated
    public String getPassphrase(MavenProject project) {
        String pass = null;
        if (project != null) {
            pass = project.getProperties().getProperty(GPG_PASSPHRASE);
            if (pass == null) {
                MavenProject prj2 = findReactorProject(project);
                pass = prj2.getProperties().getProperty(GPG_PASSPHRASE);
            }
        }
        if (project != null && pass != null) {
            findReactorProject(project).getProperties().setProperty(GPG_PASSPHRASE, pass);
        }
        return pass;
    }

    @Deprecated
    private MavenProject findReactorProject(MavenProject prj) {
        if (prj.getParent() != null
                && prj.getParent().getBasedir() != null
                && prj.getParent().getBasedir().exists()) {
            return findReactorProject(prj.getParent());
        }
        return prj;
    }
}
