---
title: Passphrases
author: 
  - Konrad Windszus
---

<!-- Licensed to the Apache Software Foundation (ASF) under one-->
<!-- or more contributor license agreements.  See the NOTICE file-->
<!-- distributed with this work for additional information-->
<!-- regarding copyright ownership.  The ASF licenses this file-->
<!-- to you under the Apache License, Version 2.0 (the-->
<!-- "License"); you may not use this file except in compliance-->
<!-- with the License.  You may obtain a copy of the License at-->
<!---->
<!--   http://www.apache.org/licenses/LICENSE-2.0-->
<!---->
<!-- Unless required by applicable law or agreed to in writing,-->
<!-- software distributed under the License is distributed on an-->
<!-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY-->
<!-- KIND, either express or implied.  See the License for the-->
<!-- specific language governing permissions and limitations-->
<!-- under the License.-->


# Passphrases

In most cases secret GPG keys are protected with a passphrase which must be passed to this Maven plugin in order to sign.

There are different ways how a passphrase can be retrieved. They are outlined in order of precedence (from safest to least safe)

<!-- MACRO{toc|fromDepth=2} -->

General remark regarding environment variables: Examples below are NOT instructions how to invoke Maven, as if you&apos;d follow these examples literally, it would defy the goal of not leaking cleartext passphrases, as these would end up in terminal history\! You should set these environment variables on your own discretion in some secure manner.

In the future, plugin will operate in `bestPractices` mode enabled, and will fail the build if credentials are given in a unsafe manner. The goal of this change was to protect plugin users from possible &quot;leaks&quot; of sensitive information \(like passphrase is\). Sensitive information like passphrases should never be stored on disks \(plaintext or quasi-encrypted\), nor should be used in a way they may &quot;leak&quot; into other files \(for example bash terminal history\).

## Retrieve passphrase via gpg-agent

Ideally, if invoked on workstation, you should rely on [gpg-agent](https://www.gnupg.org/documentation/manuals/gnupg/Invoking-GPG_002dAGENT.html) to collect passphrase from, as in that way no secrets will enter terminal history nor any file on disk. In agent-less \(batch\) sessions, typically on CI, you should provide passphrases via environment variable \(see goals\).

**Note:** When using the GPG Plugin in combination with the Maven Release Plugin, on a developer Workstation, you should rely on gpg-agent, but have it &quot;primed&quot;, as Release plugin invokes build in batch mode, that will prevent agent to present the &quot;pinentry pop up&quot;. If fully unattended release is being done, for example on a CI system, then with `useAgent` set to `false` one can pass the passphrase via environment variable.

**To prime gpg-agent caches**, one can perform simple &quot;sign&quot; operation on workstation like this `echo "test" | gpg --clearsign` or can use gpg command [gpg-preset-passphrase](https://www.gnupg.org/documentation/manuals/gnupg/Invoking-gpg_002dpreset_002dpassphrase.html).

## Retrieve passphrase via environment variable

In &quot;agent-less&quot; \(CI like usage\) mode one can supply passphrase via environment variable only.
General remark regarding environment variables: Examples below are NOT instructions how to invoke Maven, as if you&apos;d follow these examples literally, it would defy the goal of not leaking cleartext passphrases, as these would end up in terminal history\! You should set these environment variables on your own discretion in some secure manner.

```
MAVEN_GPG_PASSPHRASE=thephrase mvn release:perform
```

One &quot;real life&quot; example, on Un*x systems could be this:

```
read -s -p "Enter your GnuPG key passphrase: " MAVEN_GPG_PASSPHRASE; mvn release:perform
```


## Configure passphrase in settings.xml

**NOTE:** These techniques below are highly discouraged. Ideally sensitive information should enter via gpg-agent or via environment variables.

Instead of specifying the passphrase on the command line, you can place it in your local `settings.xml` either in clear or [encrypted](/guides/mini/guide-encryption.html) text.

```unknown
<settings>
  [...]
  <servers>
    [...]
    <server>
      <id>gpg.passphrase</id>
      <passphrase>clear or encrypted text</passphrase>
    </server>
  </servers>
</settings>
```

## Configure passphrase in settings.xml with a keyname

To allow discovery of keyname and passphrase at build time, configure this plugin to map both _keyname_ and _passphraseServerId_ to a fixed property.

```unknown
<project>
  ...
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-gpg-plugin</artifactId>
        <version>${project.version}</version>
        <executions>
          <execution>
            <id>sign-artifacts</id>
            <phase>verify</phase>
            <goals>
              <goal>sign</goal>
            </goals>
            <configuration>
              <keyname>${gpg.keyname}</keyname>
              <passphraseServerId>${gpg.keyname}</passphraseServerId>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
  ...
</project>
```

and use local `settings.xml` to discover the passphrase via the keyname

```unknown
<settings>
  [...]
  <servers>
    [...]
    <server>
      <id>your.keyname</id>
      <passphrase>clear or encrypted text</passphrase>
    </server>
  </servers>

  [...]
  <profiles>
    <profile>
      <id>my_profile_id</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.keyname>your.keyname</gpg.keyname>
      </properties>
    </profile>
  <profiles>
</settings>
```

## Configure passphrase via CLI argument

Finally, the passphrase can be given on the command line as well, but this is not recommended, and plugin will emit warnings. This mode of invocation is highly discouraged, as passphrase in cleartext is recorded into Terminal history.

```
mvn verify -Dgpg.passphrase=thephrase
```

*Never configure this with a literal value in the pom.xml!*


