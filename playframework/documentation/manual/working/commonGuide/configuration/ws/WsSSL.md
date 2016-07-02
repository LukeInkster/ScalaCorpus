<!--- Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com> -->
# Configuring WS SSL

[[Play WS|ScalaWS]] allows you to set up HTTPS completely from a configuration file, without the need to write code.  It does this by layering the Java Secure Socket Extension (JSSE) with a configuration layer and with reasonable defaults.

JDK 1.8 contains an implementation of JSSE which is [significantly more advanced](https://docs.oracle.com/javase/8/docs/technotes/guides/security/enhancements-8.html) than previous versions, and should be used if security is a priority.

> **NOTE**: It is highly recommended (if not required) to use WS SSL with the
unlimited strength java cryptography extension.  You can download the policy files from Oracle's website at [Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 8 Download](http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html).

## Table of Contents

- [[Quick Start to WS SSL|WSQuickStart]]
- [[Generating X.509 Certificates|CertificateGeneration]]
- [[Configuring Trust Stores and Key Stores|KeyStores]]
- [[Configuring Protocols|Protocols]]
- [[Configuring Cipher Suites|CipherSuites]]
- [[Configuring Certificate Validation|CertificateValidation]]
- [[Configuring Certificate Revocation|CertificateRevocation]]
- [[Configuring Hostname Verification|HostnameVerification]]
- [[Example Configurations|ExampleSSLConfig]]
- [[Using the Default SSLContext|DefaultContext]]
- [[Debugging SSL Connections|DebuggingSSL]]
- [[Loose Options|LooseSSL]]
- [[Testing SSL|TestingSSL]]

## Further Reading

JSSE is a complex product.  For convenience, the JSSE materials are provided here:

JDK 1.8:

* [JSSE Reference Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html)
* [JSSE Crypto Spec](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html#SSLTLS)
* [SunJSSE Providers](https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider)
* [PKI Programmer's Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/security/certpath/CertPathProgGuide.html)
* [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
