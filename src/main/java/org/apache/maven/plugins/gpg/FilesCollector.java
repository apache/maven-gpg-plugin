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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * Collects project artifact, the POM, and attached artifacts to be signed.
 *
 * @since 3.1.0
 */
public class FilesCollector {
    private final MavenProject project;

    private static final String DEFAULT_EXCLUDES[] =
            new String[] {"**/*.md5", "**/*.sha1", "**/*.sha256", "**/*.sha512", "**/*.asc", "**/*.sigstore"};

    private final String[] excludes;

    private final Log log;

    public FilesCollector(MavenProject project, String[] excludes, Log log) {
        this.project = project;
        this.log = log;
        if (excludes == null || excludes.length == 0) {
            this.excludes = DEFAULT_EXCLUDES;
            return;
        }
        String newExcludes[] = new String[excludes.length];
        for (int i = 0; i < excludes.length; i++) {
            String pattern;
            pattern = excludes[i].trim().replace('/', File.separatorChar).replace('\\', File.separatorChar);
            if (pattern.endsWith(File.separator)) {
                pattern += "**";
            }
            newExcludes[i] = pattern;
        }
        this.excludes = newExcludes;
    }

    public List<Item> collect() throws MojoExecutionException, MojoFailureException {
        List<Item> items = new ArrayList<>();

        if (!"pom".equals(project.getPackaging())) {
            // ----------------------------------------------------------------------------
            // Project artifact
            // ----------------------------------------------------------------------------

            Artifact artifact = project.getArtifact();

            File file = artifact.getFile();

            if (file != null && file.isFile()) {
                items.add(new Item(file, artifact.getArtifactHandler().getExtension()));
            } else if (project.getAttachedArtifacts().isEmpty()) {
                throw new MojoFailureException("The project artifact has not been assembled yet. "
                        + "Please do not invoke this goal before the lifecycle phase \"package\".");
            } else {
                log.debug("Main artifact not assembled, skipping signature generation");
            }
        }

        // ----------------------------------------------------------------------------
        // POM
        // ----------------------------------------------------------------------------

        File pomToSign =
                new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".pom");

        try {
            FileUtils.copyFile(project.getFile(), pomToSign);
        } catch (IOException e) {
            throw new MojoExecutionException("Error copying POM for signing.", e);
        }

        items.add(new Item(pomToSign, "pom"));

        // ----------------------------------------------------------------------------
        // Attached artifacts
        // ----------------------------------------------------------------------------

        for (Artifact artifact : project.getAttachedArtifacts()) {
            File file = artifact.getFile();

            if (isExcluded(artifact)) {
                log.debug("Skipping generation of signature for excluded " + file);
                continue;
            }

            items.add(new Item(
                    file,
                    artifact.getClassifier(),
                    artifact.getArtifactHandler().getExtension()));
        }

        return items;
    }

    /**
     * Tests whether or not a name matches against at least one exclude pattern.
     *
     * @param artifact The artifact to match. Must not be <code>null</code>.
     * @return <code>true</code> when the name matches against at least one exclude pattern, or <code>false</code>
     *         otherwise.
     */
    protected boolean isExcluded(Artifact artifact) {
        final Path projectBasePath = project.getBasedir().toPath();
        final Path artifactPath = artifact.getFile().toPath();
        final String relativeArtifactPath =
                projectBasePath.relativize(artifactPath).toString();

        for (String exclude : excludes) {
            if (SelectorUtils.matchPath(exclude, relativeArtifactPath)) {
                return true;
            }
        }

        return false;
    }

    public static class Item {
        private final File file;

        private final String classifier;

        private final String extension;

        public Item(File file, String classifier, String extension) {
            this.file = file;
            this.classifier = classifier;
            this.extension = extension;
        }

        public Item(File file, String extension) {
            this(file, null, extension);
        }

        public File getFile() {
            return file;
        }

        public String getClassifier() {
            return classifier;
        }

        public String getExtension() {
            return extension;
        }
    }
}
