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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Signs artifacts and installs the artifact in the remote repository.
 *
 * @author Daniel Kulp
 * @since 1.0-beta-4
 */
@Mojo(name = "sign-and-deploy-file", requiresProject = false, threadSafe = true)
public class SignAndDeployFileMojo extends AbstractGpgMojo {

    /**
     * The directory where to store signature files.
     */
    @Parameter(property = "gpg.ascDirectory")
    private File ascDirectory;

    /**
     * Flag whether Maven is currently in online/offline mode.
     */
    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    private boolean offline;

    /**
     * GroupId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "groupId")
    private String groupId;

    /**
     * ArtifactId of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "artifactId")
    private String artifactId;

    /**
     * Version of the artifact to be deployed. Retrieved from POM file if specified.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * Type of the artifact to be deployed. Retrieved from POM file if specified.
     * Defaults to file extension if not specified via command line or POM.
     */
    @Parameter(property = "packaging")
    private String packaging;

    /**
     * Add classifier to the artifact
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * Description passed to a generated POM file (in case of generatePom=true).
     */
    @Parameter(property = "generatePom.description")
    private String description;

    /**
     * File to be deployed.
     */
    @Parameter(property = "file", required = true)
    private File file;

    /**
     * Location of an existing POM file to be deployed alongside the main artifact, given by the ${file} parameter.
     */
    @Parameter(property = "pomFile")
    private File pomFile;

    /**
     * Upload a POM for this artifact. Will generate a default POM if none is supplied with the pomFile argument.
     */
    @Parameter(property = "generatePom", defaultValue = "true")
    private boolean generatePom;

    /**
     * URL where the artifact will be deployed. <br/>
     * ie ( file:///C:/m2-repo or scp://host.com/path/to/repo )
     */
    @Parameter(property = "url", required = true)
    private String url;

    /**
     * Server Id to map on the &lt;id&gt; under &lt;server&gt; section of <code>settings.xml</code>. In most cases, this
     * parameter will be required for authentication.
     */
    @Parameter(property = "repositoryId", defaultValue = "remote-repository", required = true)
    private String repositoryId;

    /**
     * The type of remote repository layout to deploy to. Try <i>legacy</i> for a Maven 1.x-style repository layout.
     */
    @Parameter(property = "repositoryLayout", defaultValue = "default")
    private String repositoryLayout;

    /**
     */
    @Component
    private RepositorySystem repositorySystem;

    /**
     * The component used to validate the user-supplied artifact coordinates.
     */
    @Component
    private ModelValidator modelValidator;

    /**
     * The default Maven project created when building the plugin
     *
     * @since 1.3
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The bundled API docs for the artifact.
     *
     * @since 1.3
     */
    @Parameter(property = "javadoc")
    private File javadoc;

    /**
     * The bundled sources for the artifact.
     *
     * @since 1.3
     */
    @Parameter(property = "sources")
    private File sources;

    /**
     * Parameter used to control how many times a failed deployment will be retried before giving up and failing.
     * If a value outside the range 1-10 is specified it will be pulled to the nearest value within the range 1-10.
     *
     * @since 1.3
     */
    @Parameter(property = "retryFailedDeploymentCount", defaultValue = "1")
    private int retryFailedDeploymentCount;

    /**
     * A comma separated list of types for each of the extra side artifacts to deploy. If there is a mis-match in
     * the number of entries in {@link #files} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter(property = "types")
    private String types;

    /**
     * A comma separated list of classifiers for each of the extra side artifacts to deploy. If there is a mis-match in
     * the number of entries in {@link #files} or {@link #types}, then an error will be raised.
     */
    @Parameter(property = "classifiers")
    private String classifiers;

    /**
     * A comma separated list of files for each of the extra side artifacts to deploy. If there is a mis-match in
     * the number of entries in {@link #types} or {@link #classifiers}, then an error will be raised.
     */
    @Parameter(property = "files")
    private String files;

    /**
     * @since 3.0.0
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    private void initProperties() throws MojoExecutionException {
        // Process the supplied POM (if there is one)
        if (pomFile != null) {
            generatePom = false;

            Model model = readModel(pomFile);

            processModel(model);
        }

        if (packaging == null && file != null) {
            packaging = FileUtils.getExtension(file.getName());
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (offline) {
            throw new MojoFailureException("Cannot deploy artifacts when Maven is in offline mode");
        }

        initProperties();

        validateArtifactInformation();

        if (!file.exists()) {
            throw new MojoFailureException(file.getPath() + " not found.");
        }

        RemoteRepository deploymentRepository = new RemoteRepository.Builder(repositoryId, "default", url).build();

        // create artifacts
        List<Artifact> artifacts = new ArrayList<>();

        // main artifact
        ArtifactHandler handler = artifactHandlerManager.getArtifactHandler(packaging);
        Artifact main = new DefaultArtifact(
                        groupId,
                        artifactId,
                        classifier == null || classifier.trim().isEmpty() ? handler.getClassifier() : classifier,
                        handler.getExtension(),
                        version)
                .setFile(file);

        File localRepoFile = new File(
                session.getRepositorySession().getLocalRepository().getBasedir(),
                session.getRepositorySession().getLocalRepositoryManager().getPathForLocalArtifact(main));
        if (file.equals(localRepoFile)) {
            throw new MojoFailureException("Cannot deploy artifact from the local repository: " + file);
        }
        artifacts.add(main);

        if (!"pom".equals(packaging)) {
            if (pomFile == null && generatePom) {
                pomFile = generatePomFile();
            }
            if (pomFile != null) {
                artifacts.add(
                        new DefaultArtifact(main.getGroupId(), main.getArtifactId(), null, "pom", main.getVersion())
                                .setFile(pomFile));
            }
        }

        if (sources != null) {
            artifacts.add(
                    new DefaultArtifact(main.getGroupId(), main.getArtifactId(), "sources", "jar", main.getVersion())
                            .setFile(sources));
        }

        if (javadoc != null) {
            artifacts.add(
                    new DefaultArtifact(main.getGroupId(), main.getArtifactId(), "javadoc", "jar", main.getVersion())
                            .setFile(javadoc));
        }

        if (files != null) {
            if (types == null) {
                throw new MojoExecutionException("You must specify 'types' if you specify 'files'");
            }
            if (classifiers == null) {
                throw new MojoExecutionException("You must specify 'classifiers' if you specify 'files'");
            }
            String[] files = this.files.split(",", -1);
            String[] types = this.types.split(",", -1);
            String[] classifiers = this.classifiers.split(",", -1);
            if (types.length != files.length) {
                throw new MojoExecutionException("You must specify the same number of entries in 'files' and "
                        + "'types' (respectively " + files.length + " and " + types.length + " entries )");
            }
            if (classifiers.length != files.length) {
                throw new MojoExecutionException("You must specify the same number of entries in 'files' and "
                        + "'classifiers' (respectively " + files.length + " and " + classifiers.length + " entries )");
            }
            for (int i = 0; i < files.length; i++) {
                File file = new File(files[i]);
                if (!file.isFile()) {
                    // try relative to the project basedir just in case
                    file = new File(project.getBasedir(), files[i]);
                }
                if (file.isFile()) {
                    Artifact artifact;
                    String ext =
                            artifactHandlerManager.getArtifactHandler(types[i]).getExtension();
                    if (StringUtils.isWhitespace(classifiers[i])) {
                        artifact = new DefaultArtifact(
                                main.getGroupId(), main.getArtifactId(), null, ext, main.getVersion());
                    } else {
                        artifact = new DefaultArtifact(
                                main.getGroupId(), main.getArtifactId(), classifiers[i], ext, main.getVersion());
                    }
                    artifacts.add(artifact.setFile(file));
                } else {
                    throw new MojoExecutionException("Specified side artifact " + file + " does not exist");
                }
            }
        } else {
            if (types != null) {
                throw new MojoExecutionException("You must specify 'files' if you specify 'types'");
            }
            if (classifiers != null) {
                throw new MojoExecutionException("You must specify 'files' if you specify 'classifiers'");
            }
        }

        // sign all
        AbstractGpgSigner signer = newSigner(null);
        signer.setOutputDirectory(ascDirectory);
        signer.setBaseDirectory(new File("").getAbsoluteFile());

        ArrayList<Artifact> signatures = new ArrayList<>();
        for (Artifact a : artifacts) {
            signatures.add(new DefaultArtifact(
                            a.getGroupId(),
                            a.getArtifactId(),
                            a.getClassifier(),
                            a.getExtension() + AbstractGpgSigner.SIGNATURE_EXTENSION,
                            a.getVersion())
                    .setFile(signer.generateSignatureForArtifact(a.getFile())));
        }
        artifacts.addAll(signatures);

        // deploy all
        try {
            deploy(deploymentRepository, artifacts);
        } catch (DeploymentException e) {
            throw new MojoExecutionException(
                    "Error deploying attached artifacts " + artifacts + ": " + e.getMessage(), e);
        }
    }

    /**
     * Process the supplied pomFile to get groupId, artifactId, version, and packaging
     *
     * @param model The POM to extract missing artifact coordinates from, must not be <code>null</code>.
     */
    private void processModel(Model model) {
        Parent parent = model.getParent();

        if (this.groupId == null) {
            this.groupId = model.getGroupId();
            if (this.groupId == null && parent != null) {
                this.groupId = parent.getGroupId();
            }
        }
        if (this.artifactId == null) {
            this.artifactId = model.getArtifactId();
        }
        if (this.version == null) {
            this.version = model.getVersion();
            if (this.version == null && parent != null) {
                this.version = parent.getVersion();
            }
        }
        if (this.packaging == null) {
            this.packaging = model.getPackaging();
        }
    }

    /**
     * Extract the model from the specified POM file.
     *
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoExecutionException If the file doesn't exist of cannot be read.
     */
    private Model readModel(File pomFile) throws MojoExecutionException {
        try (Reader reader = ReaderFactory.newXmlReader(pomFile)) {
            return new MavenXpp3Reader().read(reader);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("POM not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading POM " + pomFile, e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Error parsing POM " + pomFile, e);
        }
    }

    /**
     * Generates a minimal POM from the user-supplied artifact information.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoExecutionException If the generation failed.
     */
    private File generatePomFile() throws MojoExecutionException {
        Model model = generateModel();

        try {
            File tempFile = Files.createTempFile("mvndeploy", ".pom").toFile();
            tempFile.deleteOnExit();

            try (Writer fw = WriterFactory.newXmlWriter(tempFile)) {
                new MavenXpp3Writer().write(fw, model);
            }

            return tempFile;
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing temporary pom file: " + e.getMessage(), e);
        }
    }

    /**
     * Validates the user-supplied artifact information.
     *
     * @throws MojoFailureException If any artifact coordinate is invalid.
     */
    private void validateArtifactInformation() throws MojoFailureException {
        Model model = generateModel();

        ModelBuildingRequest request =
                new DefaultModelBuildingRequest().setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MAVEN_2_0);

        List<String> result = new ArrayList<>();

        SimpleModelProblemCollector problemCollector = new SimpleModelProblemCollector(result);

        modelValidator.validateEffectiveModel(model, request, problemCollector);

        if (!result.isEmpty()) {
            StringBuilder msg = new StringBuilder("The artifact information is incomplete or not valid:\n");
            for (String e : result) {
                msg.append(" - " + e + '\n');
            }
            throw new MojoFailureException(msg.toString());
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     *
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel() {
        Model model = new Model();

        model.setModelVersion("4.0.0");

        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setPackaging(packaging);

        model.setDescription(description);

        return model;
    }

    /**
     * Deploy an artifact from a particular file.
     *
     * @param deploymentRepository the repository to deploy to
     * @param artifacts the artifacts definition
     * @throws DeploymentException if an error occurred deploying the artifact
     */
    protected void deploy(RemoteRepository deploymentRepository, List<Artifact> artifacts) throws DeploymentException {
        int retryFailedDeploymentCount = Math.max(1, Math.min(10, this.retryFailedDeploymentCount));
        DeploymentException exception = null;
        for (int count = 0; count < retryFailedDeploymentCount; count++) {
            try {
                if (count > 0) {
                    // CHECKSTYLE_OFF: LineLength
                    getLog().info("Retrying deployment attempt " + (count + 1) + " of " + retryFailedDeploymentCount);
                    // CHECKSTYLE_ON: LineLength
                }
                DeployRequest deployRequest = new DeployRequest();
                deployRequest.setRepository(deploymentRepository);
                deployRequest.setArtifacts(artifacts);

                repositorySystem.deploy(session.getRepositorySession(), deployRequest);
                exception = null;
                break;
            } catch (DeploymentException e) {
                if (count + 1 < retryFailedDeploymentCount) {
                    getLog().warn("Encountered issue during deployment: " + e.getLocalizedMessage());
                    getLog().debug(e);
                }
                if (exception == null) {
                    exception = e;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    private static class SimpleModelProblemCollector implements ModelProblemCollector {

        private final List<String> result;

        SimpleModelProblemCollector(List<String> result) {
            this.result = result;
        }

        public void add(ModelProblemCollectorRequest req) {
            if (!ModelProblem.Severity.WARNING.equals(req.getSeverity())) {
                result.add(req.getMessage());
            }
        }
    }
}
