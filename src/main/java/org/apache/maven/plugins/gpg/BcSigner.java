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

import java.io.*;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A signer implementation that uses pure Java Bouncy Castle implementation to sign.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class BcSigner extends AbstractGpgSigner {

    public interface Loader {
        /**
         * Returns {@code true} if this loader requires user interactivity.
         */
        boolean isInteractive();

        /**
         * Returns the key ring material, or {@code null}.
         */
        default byte[] loadKeyRingMaterial(RepositorySystemSession session) throws IOException {
            return null;
        }

        /**
         * Returns the key fingerprint, or {@code null}.
         */
        default byte[] loadKeyFingerprint(RepositorySystemSession session) throws IOException {
            return null;
        }

        /**
         * Returns the key password, or {@code null}.
         */
        default char[] loadPassword(RepositorySystemSession session, long keyId) throws IOException {
            return null;
        }
    }

    public static final class GpgEnvLoader implements Loader {
        @Override
        public boolean isInteractive() {
            return false;
        }

        @Override
        public byte[] loadKeyRingMaterial(RepositorySystemSession session) {
            String keyMaterial = ConfigUtils.getString(session, null, "env.MAVEN_GPG_KEY");
            if (keyMaterial != null) {
                return keyMaterial.getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }

        @Override
        public byte[] loadKeyFingerprint(RepositorySystemSession session) {
            String keyFingerprint = ConfigUtils.getString(session, null, "env.MAVEN_GPG_KEY_FINGERPRINT");
            if (keyFingerprint != null) {
                if (keyFingerprint.trim().length() == 40) {
                    return Hex.decode(keyFingerprint);
                } else {
                    throw new IllegalArgumentException(
                            "Key fingerprint configuration is wrong (hex encoded, 40 characters)");
                }
            }
            return null;
        }

        @Override
        public char[] loadPassword(RepositorySystemSession session, long keyId) {
            String keyPassword = ConfigUtils.getString(session, null, "env.MAVEN_GPG_KEY_PASS");
            if (keyPassword != null) {
                return keyPassword.toCharArray();
            }
            return null;
        }
    }

    public static final class GpgConfLoader implements Loader {
        /**
         * Maximum key size, see <a href="https://wiki.gnupg.org/LargeKeys">Large Keys</a>.
         */
        private static final long MAX_SIZE = 5 * 1024 + 1L;

        @Override
        public boolean isInteractive() {
            return false;
        }

        @Override
        public byte[] loadKeyRingMaterial(RepositorySystemSession session) throws IOException {
            Path keyPath = Paths.get(ConfigUtils.getString(
                    session,
                    new File(session.getLocalRepository().getBasedir(), "maven-signing-key.key").toString(),
                    "gpg.keyFilePath"));
            if (!keyPath.isAbsolute()) {
                keyPath = session.getLocalRepository().getBasedir().toPath().resolve(keyPath);
            }
            if (Files.isRegularFile(keyPath)) {
                if (Files.size(keyPath) < MAX_SIZE) {
                    return Files.readAllBytes(keyPath);
                } else {
                    throw new IOException("Refusing to load key " + keyPath + "; is larger than 5KB");
                }
            }
            return null;
        }

        @Override
        public byte[] loadKeyFingerprint(RepositorySystemSession session) {
            String keyFingerprint = ConfigUtils.getString(session, null, "gpg.keyFingerprint");
            if (keyFingerprint != null) {
                if (keyFingerprint.trim().length() == 40) {
                    return Hex.decode(keyFingerprint);
                } else {
                    throw new IllegalArgumentException(
                            "Key fingerprint configuration is wrong (hex encoded, 40 characters)");
                }
            }
            return null;
        }
    }

    public static final class GpgAgentPasswordLoader implements Loader {
        @Override
        public boolean isInteractive() {
            return true;
        }

        @Override
        public char[] loadPassword(RepositorySystemSession session, long keyId) throws IOException {
            String socketLocationsStr =
                    ConfigUtils.getString(session, ".gnupg/S.gpg-agent", "gpg.agentSocketLocations");
            List<String> socketLocations = Arrays.stream(socketLocationsStr.split(","))
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.toList());
            for (String socketLocation : socketLocations) {
                try {
                    return load(keyId, Paths.get(System.getProperty("user.home"), socketLocation))
                            .toCharArray();
                } catch (SocketException e) {
                    // try next location
                }
            }
            return null;
        }

        private String load(long keyId, Path socketPath) throws IOException {
            try (AFUNIXSocket sock = AFUNIXSocket.newInstance()) {
                sock.connect(AFUNIXSocketAddress.of(socketPath));
                try (BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                        OutputStream os = sock.getOutputStream()) {

                    expectOK(in);
                    String display = System.getenv("DISPLAY");
                    if (display != null) {
                        os.write(("OPTION display=" + display + "\n").getBytes());
                        os.flush();
                        expectOK(in);
                    }
                    String term = System.getenv("TERM");
                    if (term != null) {
                        os.write(("OPTION ttytype=" + term + "\n").getBytes());
                        os.flush();
                        expectOK(in);
                    }
                    String hexKeyId = Long.toHexString(keyId & 0xFFFFFFFFL);
                    // https://unix.stackexchange.com/questions/71135/how-can-i-find-out-what-keys-gpg-agent-has-cached-like-how-ssh-add-l-shows-yo
                    String instruction = "GET_PASSPHRASE " + hexKeyId + " " + "Passphrase+incorrect"
                            + " GnuPG+Key+Passphrase Enter+passphrase+for+encrypted+GnuPG+key+" + hexKeyId
                            + "+to+use+it+for+signing+Maven+Artifacts\n";
                    os.write((instruction).getBytes());
                    os.flush();
                    return new String(Hex.decode(expectOK(in).trim()));
                }
            }
        }

        private String expectOK(BufferedReader in) throws IOException {
            String response = in.readLine();
            if (!response.startsWith("OK")) {
                throw new IOException("Expected OK but got this instead: " + response);
            }
            return response.substring(Math.min(response.length(), 3));
        }
    }

    private final RepositorySystemSession session;
    private final boolean interactive;
    private PGPSecretKey secretKey;
    private PGPPrivateKey privateKey;
    private PGPSignatureSubpacketVector hashSubPackets;

    public BcSigner(RepositorySystemSession session, boolean interactive) {
        this.session = session;
        this.interactive = interactive;
    }

    @Override
    public void setUp() throws MojoExecutionException {
        try {
            List<Loader> loaders = Stream.of(new GpgEnvLoader(), new GpgConfLoader(), new GpgAgentPasswordLoader())
                    .filter(l -> interactive || !l.isInteractive())
                    .collect(Collectors.toList());

            byte[] keyRingMaterial = null;
            for (Loader loader : loaders) {
                keyRingMaterial = loader.loadKeyRingMaterial(session);
                if (keyRingMaterial != null) {
                    break;
                }
            }
            if (keyRingMaterial == null) {
                throw new MojoExecutionException("Key ring material not found");
            }

            byte[] fingerprint = null;
            for (Loader loader : loaders) {
                fingerprint = loader.loadKeyFingerprint(session);
                if (fingerprint != null) {
                    break;
                }
            }

            PGPSecretKeyRingCollection pgpSecretKeyRingCollection = new PGPSecretKeyRingCollection(
                    PGPUtil.getDecoderStream(new ByteArrayInputStream(keyRingMaterial)),
                    new BcKeyFingerprintCalculator());

            PGPSecretKey secretKey = null;
            for (PGPSecretKeyRing ring : pgpSecretKeyRingCollection) {
                for (PGPSecretKey key : ring) {
                    if (!key.isPrivateKeyEmpty()) {
                        if (fingerprint == null || Arrays.equals(fingerprint, key.getFingerprint())) {
                            secretKey = key;
                            break;
                        }
                    }
                }
            }
            if (secretKey == null) {
                throw new MojoExecutionException("Secret key not found");
            }
            if (secretKey.isPrivateKeyEmpty()) {
                throw new MojoExecutionException("Private key not found in Secret key");
            }

            long validSeconds = secretKey.getPublicKey().getValidSeconds();
            if (validSeconds > 0) {
                LocalDateTime expireDateTime = secretKey
                        .getPublicKey()
                        .getCreationTime()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .plusSeconds(validSeconds);
                if (LocalDateTime.now().isAfter(expireDateTime)) {
                    throw new MojoExecutionException("Secret key expired at: " + expireDateTime);
                }
            }

            char[] keyPassword = passphrase != null ? passphrase.toCharArray() : null;
            final boolean keyPassNeeded = secretKey.getKeyEncryptionAlgorithm() != SymmetricKeyAlgorithmTags.NULL;
            if (keyPassNeeded && keyPassword == null) {
                for (Loader loader : loaders) {
                    keyPassword = loader.loadPassword(session, secretKey.getKeyID());
                    if (keyPassword != null) {
                        break;
                    }
                }
                if (keyPassword == null) {
                    throw new MojoExecutionException("Secret key is encrypted but no key password provided");
                }
            }

            this.secretKey = secretKey;
            this.privateKey = secretKey.extractPrivateKey(
                    new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(keyPassword));
            PGPSignatureSubpacketGenerator subPacketGenerator = new PGPSignatureSubpacketGenerator();
            subPacketGenerator.setIssuerFingerprint(false, secretKey);
            this.hashSubPackets = subPacketGenerator.generate();
        } catch (PGPException | IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    @Override
    protected void generateSignatureForFile(File file, File signature) throws MojoExecutionException {
        try (InputStream in = Files.newInputStream(file.toPath());
                OutputStream out = Files.newOutputStream(signature.toPath())) {
            PGPSignatureGenerator sGen = new PGPSignatureGenerator(
                    new BcPGPContentSignerBuilder(secretKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA512));
            sGen.init(PGPSignature.BINARY_DOCUMENT, privateKey);
            sGen.setHashedSubpackets(hashSubPackets);
            int len;
            byte[] buffer = new byte[8 * 1024];
            while ((len = in.read(buffer)) >= 0) {
                sGen.update(buffer, 0, len);
            }
            try (BCPGOutputStream bcpgOutputStream = new BCPGOutputStream(new ArmoredOutputStream(out))) {
                sGen.generate().encode(bcpgOutputStream);
            }
        } catch (PGPException | IOException e) {
            throw new MojoExecutionException(e);
        }
    }
}
