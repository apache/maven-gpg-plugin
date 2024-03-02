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
import java.io.IOException;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * @author Benjamin Bentmann
 */
public abstract class AbstractGpgMojo extends AbstractMojo {
    public static final String DEFAULT_ENV_MAVEN_GPG_PASSPHRASE = "MAVEN_GPG_PASSPHRASE";

    /**
     * The env variable name where the GnuPG passphrase is set. The default value is {@code MAVEN_GPG_PASSPHRASE"}.
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.passphraseEnvName", defaultValue = DEFAULT_ENV_MAVEN_GPG_PASSPHRASE)
    private String passphraseEnvName;

    /**
     * The directory from which gpg will load keyrings. If not specified, gpg will use the value configured for its
     * installation, e.g. <code>~/.gnupg</code> or <code>%APPDATA%/gnupg</code>.
     *
     * @since 1.0
     */
    @Parameter(property = "gpg.homedir")
    private File homedir;

    /**
     * The passphrase to use when signing. If not given, look up the value under Maven
     * settings using server id at 'passphraseServerKey' configuration.
     *
     * @deprecated Do NOT use this!
     */
    @Deprecated
    @Parameter(property = "gpg.passphrase")
    private String passphrase;

    /**
     * Server id to lookup the passphrase under Maven settings.
     * @since 1.6
     * @deprecated Do NOT use this!
     */
    @Deprecated
    @Parameter(property = "gpg.passphraseServerId")
    private String passphraseServerId;

    /**
     * The "name" of the key to sign with. Passed to gpg as <code>--local-user</code>.
     */
    @Parameter(property = "gpg.keyname")
    private String keyname;

    /**
     * Passes <code>--use-agent</code> or <code>--no-use-agent</code> to gpg. If using an agent, the passphrase is
     * optional as the agent will provide it. For gpg2, specify true as --no-use-agent was removed in gpg2 and doesn't
     * ask for a passphrase anymore.
     */
    @Parameter(property = "gpg.useagent", defaultValue = "true")
    private boolean useAgent;

    /**
     */
    @Parameter(defaultValue = "${settings.interactiveMode}", readonly = true)
    private boolean interactive;

    /**
     * The path to the GnuPG executable to use for artifact signing. Defaults to either "gpg" or "gpg.exe" depending on
     * the operating system.
     *
     * @since 1.1
     */
    @Parameter(property = "gpg.executable")
    private String executable;

    /**
     * Whether to add the default keyrings from gpg's home directory to the list of used keyrings.
     *
     * @since 1.2
     */
    @Parameter(property = "gpg.defaultKeyring", defaultValue = "true")
    private boolean defaultKeyring;

    /**
     * <p>The path to a secret keyring to add to the list of keyrings. By default, only the {@code secring.gpg} from
     * gpg's home directory is considered. Use this option (in combination with {@link #publicKeyring} and
     * {@link #defaultKeyring} if required) to use a different secret key. <em>Note:</em> Relative paths are resolved
     * against gpg's home directory, not the project base directory.</p>
     * <strong>NOTE: </strong>As of gpg 2.1 this is an obsolete option and ignored. All secret keys are stored in the
     * ‘private-keys-v1.d’ directory below the GnuPG home directory.
     *
     * @since 1.2
     */
    @Parameter(property = "gpg.secretKeyring")
    private String secretKeyring;

    /**
     * The path to a public keyring to add to the list of keyrings. By default, only the {@code pubring.gpg} from gpg's
     * home directory is considered. Use this option (and {@link #defaultKeyring} if required) to use a different public
     * key. <em>Note:</em> Relative paths are resolved against gpg's home directory, not the project base directory.
     *
     * @since 1.2
     */
    @Parameter(property = "gpg.publicKeyring")
    private String publicKeyring;

    /**
     * The lock mode to use when invoking gpg. By default no lock mode will be specified. Valid values are {@code once},
     * {@code multiple} and {@code never}. The lock mode gets translated into the corresponding {@code --lock-___}
     * command line argument. Improper usage of this option may lead to data and key corruption.
     *
     * @see <a href="http://www.gnupg.org/documentation/manuals/gnupg/GPG-Configuration-Options.html">the
     *      --lock-options</a>
     * @since 1.5
     */
    @Parameter(property = "gpg.lockMode")
    private String lockMode;

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
     * Skip doing the gpg signing.
     */
    @Parameter(property = "gpg.skip", defaultValue = "false")
    private boolean skip;

    /**
     * @since 3.0.0
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * The signer to use. Values supported: {@code gpg} (to use GnuPG command line executable), or {@code bc} (to use
     * pure Java BouncyCastle backed signer).
     *
     * @since 3.2.0
     */
    @Parameter(property = "gpg.signer", defaultValue = "gpg")
    private String signer;

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            // We're skipping the signing stuff
            return;
        }
        if ((passphrase != null && !passphrase.trim().isEmpty())
                || (passphraseServerId != null && !passphraseServerId.trim().isEmpty())) {
            // Stop propagating worst practices: passphrase MUST NOT be in any file on disk
            // (and sec dispatcher does not help either, is a joke)
            throw new MojoFailureException(
                    "Do not store passphrase in any file (disk or repository), rely on GnuPG agent or provide passphrase in "
                            + DEFAULT_ENV_MAVEN_GPG_PASSPHRASE + " environment variable.");
        }

        doExecute();
    }

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    AbstractGpgSigner newSigner(MavenProject project) throws MojoExecutionException, MojoFailureException {
        AbstractGpgSigner signer;
        if (this.signer.equals("gpg")) {
            signer = new GpgSigner(executable);
        } else if (this.signer.equals("bc")) {
            signer = new BcSigner(session.getRepositorySession(), interactive);
        } else {
            throw new MojoExecutionException("Unknown signer: " + this.signer);
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

        signer.setPassPhrase(passphrase);
        if (null == passphrase && !useAgent) {
            if (!interactive) {
                throw new MojoFailureException("Cannot obtain passphrase in batch mode");
            }
            try {
                signer.setPassPhrase(signer.getPassphrase(project));
            } catch (IOException e) {
                throw new MojoExecutionException("Exception reading passphrase", e);
            }
        }

        signer.setUp();

        return signer;
    }
}
