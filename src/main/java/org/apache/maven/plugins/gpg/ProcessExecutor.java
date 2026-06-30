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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.maven.plugin.logging.Log;

/**
 * Utility class for executing processes with ProcessBuilder.
 * Provides common functionality for GPG command execution across the plugin.
 */
class ProcessExecutor {
    private final Log log;

    ProcessExecutor(Log log) {
        this.log = log;
    }

    /**
     * Execute a command and collect stdout line by line.
     *
     * @param command the command and arguments
     * @return list of output lines
     * @throws IOException if process execution fails
     * @throws InterruptedException if process is interrupted
     * @throws ProcessExecutionException if process exits with non-zero code
     */
    List<String> executeAndCollectOutput(List<String> command, Map<String, String> environment)
            throws IOException, InterruptedException, ProcessExecutionException {
        return executeAndCollectOutput(command, environment, null);
    }

    /**
     * Execute a command with stdin input and collect stdout line by line.
     *
     * @param command the command and arguments
     * @param environment environment variables
     * @param stdin input stream for process stdin
     * @return list of output lines
     * @throws IOException if process execution fails
     * @throws InterruptedException if process is interrupted
     * @throws ProcessExecutionException if process exits with non-zero code
     */
    List<String> executeAndCollectOutput(List<String> command, Map<String, String> environment, InputStream stdin)
            throws IOException, InterruptedException, ProcessExecutionException {

        List<String> output = new ArrayList<>();
        Consumer<String> outputConsumer = output::add;

        execute(command, environment, stdin, outputConsumer, line -> {
            if (log.isWarnEnabled()) {
                log.warn("[Process stderr] " + line);
            }
        });

        if (log.isDebugEnabled()) {
            log.debug("[Process output collected] " + output.size() + " lines");
        }

        return output;
    }

    /**
     * Execute a command with stdout and stderr line consumers.
     *
     * @param command the command and arguments
     * @param environment environment variables
     * @param stdin input stream for process stdin (can be null)
     * @param stdoutConsumer consumer for stdout lines
     * @param stderrConsumer consumer for stderr lines
     * @throws IOException if process execution fails
     * @throws InterruptedException if process is interrupted
     * @throws ProcessExecutionException if process exits with non-zero code
     */
    void execute(
            List<String> command,
            Map<String, String> environment,
            InputStream stdin,
            Consumer<String> stdoutConsumer,
            Consumer<String> stderrConsumer)
            throws IOException, InterruptedException, ProcessExecutionException {

        ProcessBuilder pb = new ProcessBuilder(command);

        if (environment != null && !environment.isEmpty()) {
            Map<String, String> processEnv = pb.environment();
            processEnv.putAll(environment);
        }

        if (log.isDebugEnabled()) {
            log.debug("ProcessBuilder command: " + command);
            log.debug("ProcessBuilder environment: " + environment);
        }

        Process process = pb.start();

        executeWithStreamHandling(process, stdin, stdoutConsumer, stderrConsumer);
    }

    private void executeWithStreamHandling(
            Process process, InputStream stdin, Consumer<String> stdoutConsumer, Consumer<String> stderrConsumer)
            throws IOException, InterruptedException, ProcessExecutionException {

        StreamGobbler outputGobbler =
                new StreamGobbler(process.getInputStream(), stdoutConsumer != null ? stdoutConsumer : line -> {});
        StreamGobbler errorGobbler =
                new StreamGobbler(process.getErrorStream(), stderrConsumer != null ? stderrConsumer : line -> {});

        outputGobbler.start();
        errorGobbler.start();

        if (stdin != null) {
            writeStdin(process.getOutputStream(), stdin);
        }

        int exitCode = process.waitFor();
        outputGobbler.join();
        errorGobbler.join();

        if (exitCode != 0) {
            throw new ProcessExecutionException("Process exited with exit code: " + exitCode, exitCode);
        }
    }

    private void writeStdin(OutputStream processStdin, InputStream stdin) throws IOException {
        try (OutputStream os = processStdin) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stdin.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        } catch (IOException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error writing to process stdin: " + e.getMessage());
            }
        }
    }

    /**
     * Thread that reads from an InputStream and passes each line to a consumer.
     */
    private static class StreamGobbler extends Thread {
        private final InputStream inputStream;
        private final Consumer<String> outputConsumer;

        StreamGobbler(InputStream inputStream, Consumer<String> outputConsumer) {
            this.inputStream = inputStream;
            this.outputConsumer = outputConsumer;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputConsumer.accept(line);
                }
            } catch (IOException e) {
                // Stream closed is expected when process ends
                // Suppress stack trace for normal termination
                if (!e.getMessage().contains("Stream closed") && !e.getMessage().contains("Bad file descriptor")) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Exception thrown when a process exits with a non-zero exit code.
     */
    static class ProcessExecutionException extends Exception {
        private final int exitCode;

        ProcessExecutionException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        int getExitCode() {
            return exitCode;
        }
    }
}
