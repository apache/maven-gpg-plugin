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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

/**
 * A base class for all classes that implements signing of files.
 *
 * @author Dennis Lundberg
 * @since 1.5
 */
public abstract class AbstractGpgSigner {
    public static final String SIGNATURE_EXTENSION = ".asc";

    protected boolean useAgent;

    protected boolean isInteractive = true;

    protected boolean defaultKeyring = true;

    protected String keyname;

    private Log log;

    protected String passphrase;

    private File outputDir;

    private File buildDir;

    private File baseDir;

    protected File homeDir;

    protected String secretKeyring;

    protected String publicKeyring;

    protected String lockMode;

    protected List<String> args;

    public Log getLog() {
        return log;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public void setInteractive(boolean b) {
        isInteractive = b;
    }

    public void setLockMode(String lockMode) {
        this.lockMode = lockMode;
    }

    public void setUseAgent(boolean b) {
        useAgent = b;
    }

    public void setDefaultKeyring(boolean enabled) {
        defaultKeyring = enabled;
    }

    public void setKeyName(String s) {
        keyname = s;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    public void setPassPhrase(String s) {
        passphrase = s;
    }

    public void setOutputDirectory(File out) {
        outputDir = out;
    }

    public void setBuildDirectory(File out) {
        buildDir = out;
    }

    public void setBaseDirectory(File out) {
        baseDir = out;
    }

    public void setHomeDirectory(File homeDirectory) {
        homeDir = homeDirectory;
    }

    public void setSecretKeyring(String path) {
        secretKeyring = path;
    }

    public void setPublicKeyring(String path) {
        publicKeyring = path;
    }

    public abstract String signerName();

    public void prepare() throws MojoFailureException {}

    /**
     * Create a detached signature file for the provided file.
     *
     * @param file The file to sign
     * @return A reference to the generated signature file
     * @throws MojoExecutionException if signature generation fails
     */
    public File generateSignatureForArtifact(File file) throws MojoExecutionException {
        // ----------------------------------------------------------------------------
        // Set up the file and directory for the signature file
        // ----------------------------------------------------------------------------

        File signature = new File(file + SIGNATURE_EXTENSION);

        boolean isInBuildDir = false;
        if (buildDir != null) {
            File parent = signature.getParentFile();
            if (buildDir.equals(parent)) {
                isInBuildDir = true;
            }
        }
        if (!isInBuildDir && outputDir != null) {
            String fileDirectory = "";
            File signatureDirectory = signature;

            while ((signatureDirectory = signatureDirectory.getParentFile()) != null) {
                if (isPossibleRootOfArtifact(signatureDirectory)) {
                    break;
                }
                fileDirectory = signatureDirectory.getName() + File.separatorChar + fileDirectory;
            }
            signatureDirectory = new File(outputDir, fileDirectory);
            if (!signatureDirectory.exists()) {
                signatureDirectory.mkdirs();
            }
            signature = new File(signatureDirectory, file.getName() + SIGNATURE_EXTENSION);
        }

        if (signature.exists()) {
            signature.delete();
        }

        // ----------------------------------------------------------------------------
        // Generate the signature file
        // ----------------------------------------------------------------------------

        generateSignatureForFile(file, signature);

        return signature;
    }

    /**
     * Generate the detached signature file for the provided file.
     *
     * @param file The file to sign
     * @param signature The file in which the generate signature will be put
     * @throws MojoExecutionException if signature generation fails
     */
    protected abstract void generateSignatureForFile(File file, File signature) throws MojoExecutionException;

    private boolean isPossibleRootOfArtifact(File signatureDirectory) {
        return signatureDirectory.equals(outputDir)
                || signatureDirectory.equals(buildDir)
                || signatureDirectory.equals(baseDir);
    }
}
