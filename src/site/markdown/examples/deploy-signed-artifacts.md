---
title: Deploy Signed Artifacts
author: 
  - Dennis Lundberg
date: 2011-03-19
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
# Deploy Signed Artifacts

Without this plugin you deploy your project like this:

```unknown
mvn deploy
```

If you have configured this plugin according to the instructions in the [usage page](../usage.html), nothing changes for interactive sessions:

```unknown
mvn deploy
```

And the gpg-agent will prompt you for passphrase.

General remark regarding environment variables: Examples below are NOT instructions how to invoke Maven, as if you&apos;d follow these examples literally, it would defy the goal of not leaking cleartext passphrases, as these would end up in terminal history\! You should set these environment variables on your own discretion in some secure manner.

If you use &quot;batch&quot; build \(or build is invoked by Maven Release Plugin\), then gpg-agent will be unable to ask interactively for password. In such cases you want to &quot;prime&quot; the agent with passwords first. See [usage page](../usage.html) for details how to &quot;prime&quot; gpg-agent.

In &quot;agent-less&quot; \(CI like usage\) mode one can supply passphrase via environment variable only.

```unknown
MAVEN_GPG_PASSPHRASE=thephrase mvn --batch-mode deploy
```

## Sign using BC Signer

By default the plugin uses the &quot;gpg&quot; Signer \(that relies on GnuPG tool installed on host OS\). The &quot;bc&quot; Signer on the other hand implements signing in pure Java using Bouncy Castle libraries.

The &quot;bc&quot; signer, unlike &quot;gpg&quot;, does not and cannot make use of `~/.gnupg` directory in user home, and have to have configured both, the key used to sign and the passphrase \(if key is passphrase protected\). The key is expected to be in TSK format \(see [&quot;Transferable Secret Keys&quot;](https://openpgp.dev/book/private_keys.html#transferable-secret-key-format) format\).

```unknown
mvn deploy -Dgpg.signer=bc -Dgpg.keyFilePath=path/to/key
```

In interactive sessions, similarly as with &quot;gpg&quot; Signer, gpg-agent will be used to ask for password. In batch sessions, you can use environment variables to achieve similar thing:

```unknown
MAVEN_GPG_PASSPHRASE=thephrase mvn deploy -Dgpg.signer=bc -Dgpg.keyFilePath=path/to/key
```

Ultimately, you can place both, they key and passphrase into environment variables:

```unknown
MAVEN_GPG_KEY=thekeymaterial MAVEN_GPG_PASSPHRASE=thephrase mvn deploy -Dgpg.signer=bc
```

## Install/Deploy without configuring the plugin in the POM

Currently this is not easily accomplished. gpg signs the artifacts attached to the build at the point that gpg runs. However, we want to &quot;inject&quot; the gpg into the phases. What MIGHT work is:

```unknown
mvn verify gpg:sign install:install deploy:deploy
```

However, if there are other plugins configured for phases after the `verify` phase, they will not be run.

