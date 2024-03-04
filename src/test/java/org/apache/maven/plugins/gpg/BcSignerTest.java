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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.DefaultLocalPathComposer;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BcSigner}.
 */
class BcSignerTest {

    /**
     * Test for BC agent use. Disabled, as this test cannot run on CI, only on computer that have gpg-agent.
     * The goal of this test is to prepare BC signer, but to be able to prepare, it needs passphrase for the
     * passphrase protected signing key (provided in src/test/resources/signing-key.asc). Passphrase is "TEST"
     * (without quotes, all caps). If you want to execute this test, remove disabled annotation and run it from
     * IDE (or whatever is your preferred way). On first run, Agent will pop a dialogue asking for password,
     * and it will cache your response, so subsequent invocation will NOT ask for password.
     * <p>
     * IF you enter correct password ("TEST"), the test will pass (prepare will execute without any issue).
     * IF you enter incorrect password, the test will fail with some message like:
     * {@code org.apache.maven.plugin.MojoFailureException: org.bouncycastle.openpgp.PGPException: checksum mismatch at in checksum of 20 bytes}
     * and this would cause plugin failure as well.
     * <p>
     * On Un*x, to make agent "forget" what you entered, use {@code gpg-connect-agent RELOADAGENT} command. To exit use
     * Ctrl+D (EOF).
     */
    @Disabled
    @Test
    void testAgent() throws Exception {
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        session.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory(new DefaultLocalPathComposer())
                .newInstance(session, new LocalRepository("target/local-repo")));
        BcSigner signer = new BcSigner(
                session,
                "unimportant",
                "unimportant",
                ".gnupg/S.gpg-agent",
                new File("src/test/resources/signing-key.asc").getAbsolutePath(),
                null);
        signer.prepare();
    }
}
