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
var artifactDir = new File(localRepositoryPath, "org/apache/maven/its/gpg/srwe/test/1.0")

var expectedFiles = [
    "_remote.repositories",
    "test-1.0.pom",
    "test-1.0.pom.asc",
    "test-1.0.jar",
    "test-1.0.jar.asc",
    "test-1.0-sources.jar",
    "test-1.0-sources.jar.asc"
]

for (File file : artifactDir.listFiles()) {
    var fileName = file.getName()

    //maven4: skip consumer POM
    if (fileName.endsWith("consumer.pom") || fileName.endsWith("consumer.pom.asc")) {
        continue
    }

    println "Checking if file is expected: $file"

    var expected = false;
    for (String expectedFile : expectedFiles) {
        if (expectedFile == fileName) {
            expected = true
            break
        }
    }

    if (!expected) {
        throw new Exception("Unexpected file $file")
    }
}
