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


import org.codehaus.plexus.util.FileUtils

var buildLog = new File(basedir, "build.log")
var logContent = FileUtils.fileRead(buildLog)

// assert that the Maven build properly failed and did not time out
if (!logContent.contains("Total time: ") || !logContent.contains("Finished at: ")) {
    throw new Exception("Maven build did not fail, but timed out")
}

// assert that the Maven build failed, because pinentry is not allowed in non-interactive mode
if (!logContent.contains("[GNUPG:] FAILURE sign 67108949")) {
    throw new Exception("Maven build did not fail in consequence of pinentry not being available to GPG")
}

