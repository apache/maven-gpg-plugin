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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.util.encoders.Hex;
import org.codehaus.plexus.util.io.CachingOutputStream;
import org.eclipse.aether.RepositorySystemSession;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A signer implementation that uses pure Java Bouncy Castle implementation to sign.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class BcSigner extends AbstractGpgSigner {
    public static final String NAME = "bc";

    public interface Loader {
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
        default char[] loadPassword(RepositorySystemSession session, byte[] fingerprint) throws IOException {
            return null;
        }
    }

    public final class GpgEnvLoader implements Loader {
        @Override
        public byte[] loadKeyRingMaterial(RepositorySystemSession session) {
            String keyMaterial = (String) session.getConfigProperties().get("env." + keyEnvName);
            if (keyMaterial != null) {
                return keyMaterial.getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }

        @Override
        public byte[] loadKeyFingerprint(RepositorySystemSession session) {
            String keyFingerprint = (String) session.getConfigProperties().get("env." + keyFingerprintEnvName);
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

    public final class GpgConfLoader implements Loader {
        /**
         * Maximum file size allowed to load (as we load it into heap).
         * <p>
         * This barrier exists to prevent us to load big/huge files, if this code is pointed at one
         * (by mistake or by malicious intent).
         *
         * @see <a href="https://wiki.gnupg.org/LargeKeys">Large Keys</a>
         */
        private static final long MAX_SIZE = 64 * 1024 + 1L;

        @Override
        public byte[] loadKeyRingMaterial(RepositorySystemSession session) throws IOException {
            Path keyPath = Paths.get(keyFilePath);
            if (!keyPath.isAbsolute()) {
                keyPath = Paths.get(System.getProperty("user.home"))
                        .resolve(keyPath)
                        .toAbsolutePath();
            }
            if (Files.isRegularFile(keyPath)) {
                if (Files.size(keyPath) < MAX_SIZE) {
                    return Files.readAllBytes(keyPath);
                } else {
                    throw new IOException("Refusing to load key " + keyPath + "; is larger than 16KB");
                }
            }
            return null;
        }

        @Override
        public byte[] loadKeyFingerprint(RepositorySystemSession session) {
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

    public final class GpgAgentPasswordLoader implements Loader {
        @Override
        public char[] loadPassword(RepositorySystemSession session, byte[] fingerprint) throws IOException {
            if (!useAgent) {
                return null;
            }
            List<String> socketLocations = Arrays.stream(agentSocketLocations.split(","))
                    .filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.toList());
            for (String socketLocation : socketLocations) {
                try {
                    Path socketLocationPath = Paths.get(socketLocation);
                    if (!socketLocationPath.isAbsolute()) {
                        socketLocationPath = Paths.get(System.getProperty("user.home"))
                                .resolve(socketLocationPath)
                                .toAbsolutePath();
                    }
                    String pw = load(fingerprint, socketLocationPath);
                    if (pw != null) {
                        return pw.toCharArray();
                    }
                } catch (SocketException e) {
                    // try next location
                }
            }
            return null;
        }

        private String load(byte[] fingerprint, Path socketPath) throws IOException {
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
                    String hexKeyFingerprint = Hex.toHexString(fingerprint);
                    String displayFingerprint = hexKeyFingerprint.toUpperCase(Locale.ROOT);
                    // https://unix.stackexchange.com/questions/71135/how-can-i-find-out-what-keys-gpg-agent-has-cached-like-how-ssh-add-l-shows-yo
                    String instruction = "GET_PASSPHRASE "
                            + (!isInteractive ? "--no-ask " : "")
                            + hexKeyFingerprint
                            + " "
                            + "X "
                            + "GnuPG+Passphrase "
                            + "Please+enter+the+passphrase+to+unlock+the+OpenPGP+secret+key+with+fingerprint:+"
                            + displayFingerprint
                            + "+to+use+it+for+signing+Maven+Artifacts\n";
                    os.write((instruction).getBytes());
                    os.flush();
                    String pw = mayExpectOK(in);
                    if (pw != null) {
                        return new String(Hex.decode(pw.trim()));
                    }
                    return null;
                }
            }
        }

        private void expectOK(BufferedReader in) throws IOException {
            String response = in.readLine();
            if (!response.startsWith("OK")) {
                throw new IOException("Expected OK but got this instead: " + response);
            }
        }

        private String mayExpectOK(BufferedReader in) throws IOException {
            String response = in.readLine();
            if (response.startsWith("ERR")) {
                return null;
            } else if (!response.startsWith("OK")) {
                throw new IOException("Expected OK/ERR but got this instead: " + response);
            }
            return response.substring(Math.min(response.length(), 3));
        }
    }

    private final RepositorySystemSession session;
    private final String keyEnvName;
    private final String keyFingerprintEnvName;
    private final String agentSocketLocations;
    private final String keyFilePath;
    private final String keyFingerprint;
    private PGPSecretKey secretKey;
    private PGPPrivateKey privateKey;
    private PGPSignatureSubpacketVector hashSubPackets;

    public BcSigner(
            RepositorySystemSession session,
            String keyEnvName,
            String keyFingerprintEnvName,
            String agentSocketLocations,
            String keyFilePath,
            String keyFingerprint) {
        this.session = session;
        this.keyEnvName = keyEnvName;
        this.keyFingerprintEnvName = keyFingerprintEnvName;
        this.agentSocketLocations = agentSocketLocations;
        this.keyFilePath = keyFilePath;
        this.keyFingerprint = keyFingerprint;
    }

    @Override
    public String signerName() {
        return NAME;
    }

    @Override
    public void prepare() throws MojoFailureException {
        try {
            List<Loader> loaders = Stream.of(new GpgEnvLoader(), new GpgConfLoader(), new GpgAgentPasswordLoader())
                    .collect(Collectors.toList());

            byte[] keyRingMaterial = null;
            for (Loader loader : loaders) {
                keyRingMaterial = loader.loadKeyRingMaterial(session);
                if (keyRingMaterial != null) {
                    break;
                }
            }
            if (keyRingMaterial == null) {
                throw new MojoFailureException("Key ring material not found");
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
                throw new MojoFailureException("Secret key not found");
            }
            if (secretKey.isPrivateKeyEmpty()) {
                throw new MojoFailureException("Private key not found in Secret key");
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
                    throw new MojoFailureException("Secret key expired at: " + expireDateTime);
                }
            }

            char[] keyPassword = passphrase != null ? passphrase.toCharArray() : null;
            final boolean keyPassNeeded = secretKey.getKeyEncryptionAlgorithm() != SymmetricKeyAlgorithmTags.NULL;
            if (keyPassNeeded && keyPassword == null) {
                for (Loader loader : loaders) {
                    keyPassword = loader.loadPassword(session, secretKey.getFingerprint());
                    if (keyPassword != null) {
                        break;
                    }
                }
                if (keyPassword == null) {
                    throw new MojoFailureException("Secret key is encrypted but no passphrase provided");
                }
            }

            this.secretKey = secretKey;
            this.privateKey = secretKey.extractPrivateKey(
                    new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(keyPassword));
            PGPSignatureSubpacketGenerator subPacketGenerator = new PGPSignatureSubpacketGenerator();
            subPacketGenerator.setIssuerFingerprint(false, secretKey);
            this.hashSubPackets = subPacketGenerator.generate();
        } catch (PGPException | IOException e) {
            throw new MojoFailureException(e);
        }
    }

    @Override
    public String getKeyInfo() {
        Iterator<String> userIds = secretKey.getPublicKey().getUserIDs();
        if (userIds.hasNext()) {
            return userIds.next();
        }
        return Hex.toHexString(secretKey.getPublicKey().getFingerprint());
    }

    @Override
    protected void generateSignatureForFile(File file, File signature) throws MojoExecutionException {
        try (InputStream in = Files.newInputStream(file.toPath());
                OutputStream out = new CachingOutputStream(signature.toPath())) {
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
