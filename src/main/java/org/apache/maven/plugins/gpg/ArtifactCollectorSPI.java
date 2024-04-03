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
import java.util.Collection;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Artifact collector SPI, that collects artifacts in some way from given {@link RemoteRepository}.
 *
 * @since 3.2.3
 */
public interface ArtifactCollectorSPI {
    /**
     * Returns collected artifacts or {@code null} if collection was not possible for any reason.
     * <p>
     * Collector should collect only <em>relevant artifacts</em>, those that are subject to signing.
     */
    Collection<Artifact> collectArtifacts(RepositorySystemSession session, RemoteRepository remoteRepository)
            throws IOException;
}
