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


var buildLog = new File(basedir, "build.log")
var logContent = buildLog.getText('UTF-8')

// assert that the Maven build properly failed and did not time out
assert logContent.contains("Total time: ") && logContent.contains("Finished at: ") : "Maven build did not fail, but timed out"

// gpg: assert that the Maven build failed, because pinentry is not allowed in non-interactive mode
// bc: assert that the Maven build failed, because key to sign is encrypted by no passphrase provided
assert logContent.contains("[GNUPG:] FAILURE sign 67108949") || logContent.contains("Secret key is encrypted but no passphrase provided") : "Maven build did not fail in consequence of pinentry not being available to GPG"
