---
title: Frequently Asked Questions
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<a id="top"></a>

# Frequently Asked Questions

1. [What is GnuPG?](#question1)
2. [Why is the site descriptor not signed?](#site-descriptor)
3. [Why am I getting "gpg: signing failed: No pinentry" while releasing?](#no-pinentry)

<a id="question1"></a>

### What is GnuPG?

You can read more about GnuPG at [their web site](http://www.gnupg.org/).

<a id="site-descriptor"></a>

### Why is the site descriptor not signed?

The `site.xml` that can be deployed alongside parent POMs was originally attached to the project in such a way
that the GPG Plugin could not get hold of it. To enable signing of the site descriptor, you need to update to
Maven Site Plugin 2.1.1+ which contains the required fix (see also
[MSITE-478](https://issues.apache.org/jira/browse/MSITE-478)).

<a id="no-pinentry"></a>

### Why am I getting "gpg: signing failed: No pinentry" while releasing?

When plugin used in combination with
[Maven Release Plugin](https://maven.apache.org/maven-release/maven-release-plugin/) the GPG signing will
happen in "batch mode". This implies that you must either use GPG passphrase passed in via environment
variable (preferred on systems like CI systems are), or, if on Workstation, using primed gpg-agent is needed.
Read more here about [GPG Agent priming](passphrase.html#Retrieve_passphrase_via_gpg-agent).
