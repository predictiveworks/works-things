package de.kp.works.things.server.ssl

/**
 * Copyright (c) 2019 - 2021 Dr. Krusche & Partner PartG. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * @author Stefan Krusche, Dr. Krusche & Partner PartG
 *
 */

import com.google.common.base.Strings
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.openssl.{PEMEncryptedKeyPair, PEMKeyPair, PEMParser}
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder

import java.io.{ByteArrayInputStream, InputStreamReader}
import java.nio.file.{Files, Paths}
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, KeyStore, PrivateKey, Security}
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, SSLSocketFactory, TrustManager, TrustManagerFactory}

object SslUtil {

  val TLS_VERSION = "TLS"

  val CA_CERT_ALIAS = "caCert-cert"
  val CERT_ALIAS    = "cert"

  val PRIVATE_KEY_ALIAS = "private-key"

  /**
   * Build a SSLSocketFactory without Key & Trust Managers; the TLS version is set
   * to the default version
   */
  def getPlainSslSocketFactory: SSLSocketFactory =
    getPlainSslSocketFactory(TLS_VERSION)

  def getPlainSslSocketFactory(tlsVersion: String): SSLSocketFactory = {

    val sslContext = SSLContext.getInstance(tlsVersion)

    sslContext.init(null, null, null)
    sslContext.getSocketFactory

  }

  /** * KEY STORE ONLY ** */

  def getStoreSslSocketFactory(
    keystoreFile: String,
    keystoreType: String,
    keystorePassword: String,
    keystoreAlgorithm: String): SSLSocketFactory =
    getStoreSslSocketFactory(keystoreFile, keystoreType, keystorePassword, keystoreAlgorithm, TLS_VERSION)

  def getStoreSslSocketFactory(
    keystoreFile: String,
    keystoreType: String,
    keystorePassword: String,
    keystoreAlgorithm: String,
    tlsVersion: String): SSLSocketFactory = {

    val sslContext = SSLContext.getInstance(tlsVersion)

    /* Build Key Managers */
    var keyManagers:Array[KeyManager] = null

    keyManagers = getStoreKeyManagerFactory(keystoreFile, keystoreType, keystorePassword, keystoreAlgorithm).getKeyManagers

    sslContext.init(keyManagers, null, null)
    sslContext.getSocketFactory

  }

  /** * KEY & TRUST STORE ** */

  def getStoreSslSocketFactory(keystoreFile: String, keystoreType: String, keystorePassword: String, keystoreAlgorithm: String, truststoreFile: String, truststoreType: String, truststorePassword: String, truststoreAlgorithm: String, tlsVersion: String): SSLSocketFactory = {

    val sslContext = SSLContext.getInstance(tlsVersion)

    /* Build Key Managers */
    var keyManagers:Array[KeyManager] = null

    val keyManagerFactory = getStoreKeyManagerFactory(keystoreFile, keystoreType, keystorePassword, keystoreAlgorithm)
    keyManagers = keyManagerFactory.getKeyManagers

    /* Build Trust Managers */
    var trustManagers:Array[TrustManager] = null

    val trustManagerFactory = getStoreTrustManagerFactory(truststoreFile, truststoreType, truststorePassword, truststoreAlgorithm)
    if (trustManagerFactory != null) trustManagers = trustManagerFactory.getTrustManagers

    sslContext.init(keyManagers, trustManagers, null)
    sslContext.getSocketFactory

  }

  /** * CERTIFICATE FILES * */

  def getCertFileSslSocketFactory(caCrtFile: String, crtFile: String, keyFile: String, password: String): SSLSocketFactory =
    getCertFileSslSocketFactory(caCrtFile, crtFile, keyFile, password, TLS_VERSION)

  def getCertFileSslSocketFactory(caCrtFile: String, crtFile: String, keyFile: String, password: String, tlsVersion: String): SSLSocketFactory = {

    Security.addProvider(new BouncyCastleProvider)

    val trustManagerFactory = getCertFileTrustManagerFactory(caCrtFile)
    val keyManagerFactory = getCertFileKeyManagerFactory(crtFile, keyFile, password)

    val sslContext = SSLContext.getInstance(tlsVersion)
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)

    /*
     * Set connect options to use the TLS
     * enabled socket factory
     */
    sslContext.getSocketFactory

  }

  /** * CERTIFICATES ** */

  def getCertSslSocketFactory(caCert: X509Certificate, cert: X509Certificate, privateKey: PrivateKey, password: String): SSLSocketFactory =
    getCertSslSocketFactory(caCert, cert, privateKey, password, TLS_VERSION)

  def getCertSslSocketFactory(caCert: X509Certificate, cert: X509Certificate, privateKey: PrivateKey, password: String, tlsVersion: String): SSLSocketFactory = {

    Security.addProvider(new BouncyCastleProvider)

    val trustManagerFactory = getCertTrustManagerFactory(caCert)
    val keyManagerFactory = getCertKeyManagerFactory(cert, privateKey, password)

    val sslContext = SSLContext.getInstance(tlsVersion)
    sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)

    /*
     * Set connect options to use the TLS
     * enabled socket factory
     */
    sslContext.getSocketFactory

  }

  /** *** KEY MANAGER FACTORY **** */

  def getStoreKeyManagerFactory(keystoreFile: String, keystoreType: String, keystorePassword: String, keystoreAlgorithm: String): KeyManagerFactory = {

    var keystore = loadKeystore(keystoreFile, keystoreType, keystorePassword)
    println("keystore loaded")
    /*
     * We have to manually fall back to default keystore. SSLContext won't provide
     * such a functionality.
     */
    if (keystore == null) {
      val ksFile = System.getProperty("javax.net.ssl.keyStore")
      val ksType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType)
      val ksPass = System.getProperty("javax.net.ssl.keyStorePassword", "")

      keystore = loadKeystore(ksFile, ksType, ksPass)

    }
    val ksAlgo =
      if (Strings.isNullOrEmpty(keystoreAlgorithm)) KeyManagerFactory.getDefaultAlgorithm
      else keystoreAlgorithm

    val keyManagerFactory = KeyManagerFactory.getInstance(ksAlgo)
    val passwordArr =
      if (keystorePassword == null) null
      else keystorePassword.toCharArray

    keyManagerFactory.init(keystore, passwordArr)
    keyManagerFactory

  }

  def getCertFileKeyManagerFactory(crtFile: String, keyFile: String, password: String): KeyManagerFactory = {

    val cert = getX509CertFromPEM(crtFile)
    val privateKey = getPrivateKeyFromPEM(keyFile, password)

    getCertKeyManagerFactory(cert, privateKey, password)

  }

  def getCertKeyManagerFactory(cert: X509Certificate, privateKey: PrivateKey, password: String): KeyManagerFactory = {

    val ks = createKeystore
    /*
     * Add client certificate to key store, the client certificate alias is
     * 'certificate' (see IBM Watson IoT platform)
     */
    ks.setCertificateEntry(CERT_ALIAS, cert)
    /*
     * Add private key to keystore and distinguish between use case with and without
     * password
     */
    val passwordArray =
      if (password != null) {
        password.toCharArray
      }
      else {
        new Array[Char](0)
      }

    ks.setKeyEntry(PRIVATE_KEY_ALIAS, privateKey, passwordArray, Array(cert))
    /*
     * Initialize key manager from the key store; note, the default algorithm also
     * supported by IBM Watson IoT platform is PKIX
     */
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)

    keyManagerFactory.init(ks, passwordArray)
    keyManagerFactory
  }

  /** *** TRUST MANAGER FACTORY **** */

  def getAllTrustManagers: Array[TrustManager] = {
    Array[TrustManager](new AllTrustManager())
  }

  def getStoreTrustManagerFactory(truststoreFile: String, truststoreType: String, truststorePassword: String, truststoreAlgorithm: String): TrustManagerFactory = {

    var factory:TrustManagerFactory = null
    val trustStore = loadKeystore(truststoreFile, truststoreType, truststorePassword)

    if (trustStore != null) {
      val trustStoreAlgorithm =
        if (Strings.isNullOrEmpty(truststoreAlgorithm)) TrustManagerFactory.getDefaultAlgorithm
        else truststoreAlgorithm

      factory = TrustManagerFactory.getInstance(trustStoreAlgorithm)
      factory.init(trustStore)

    }

    factory

  }

  def getCertFileTrustManagerFactory(caCrtFile: String): TrustManagerFactory = {

    val caCert = getX509CertFromPEM(caCrtFile)
    getCertTrustManagerFactory(caCert)

  }

  def getCertTrustManagerFactory(caCert: X509Certificate): TrustManagerFactory = {

    val ks = createKeystore
    /*
     * Add CA certificate to keystore; note, the CA certificate alias is set to
     * 'ca-certificate' (see IBM Watson IoT platform)
     */
    ks.setCertificateEntry(CA_CERT_ALIAS, caCert)
    /*
     * Establish certificate trust chain; note, the default algorithm also supported
     * by IBM Watson IoT platform is PKIX
     */
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)

    trustManagerFactory.init(ks)
    trustManagerFactory

  }

  /** *** X509 CERTIFICATE **** */

  def getX509CertFromPEM(crtFile: String): X509Certificate = {

    Security.addProvider(new BouncyCastleProvider)
    /*
     * Since Java cannot read PEM formatted certificates, this method is using
     * bouncy castle (http://www.bouncycastle.org/) to load the necessary files.
     *
     * IMPORTANT: Bouncycastle Provider must be added before this method can be
     * called
     *
     */
    val bytes = Files.readAllBytes(Paths.get(crtFile))
    val bais = new ByteArrayInputStream(bytes)

    val reader = new PEMParser(new InputStreamReader(bais))
    val certObj = reader.readObject

    reader.close()

    certObj match {
      case cert:X509Certificate =>
        cert

      case certHolder:X509CertificateHolder =>
        new JcaX509CertificateConverter()
          .setProvider( "BC" )
          .getCertificate( certHolder )

      case _ =>
        throw new Exception(s"Unknown certificate object $certObj detected.")
    }

  }

  /** *** PRIVATE KEY **** */

  def getPrivateKeyFromPEM(keyFile: String, password: String): PrivateKey = {

    Security.addProvider(new BouncyCastleProvider)

    val bytes = Files.readAllBytes(Paths.get(keyFile))
    val bais = new ByteArrayInputStream(bytes)

    val reader = new PEMParser(new InputStreamReader(bais))
    val keyObj = reader.readObject

    reader.close()

    val keyBytes = keyObj match {
        /*
         * Encrypted support either for (private, public)
         * key pairs or a single private key info
         */
      case encKeyPair: PEMEncryptedKeyPair =>
        /*
         * This path extracts the private key from
         * a PEM (private, public) key file
         */
        if (password == null)
          throw new Exception("[ERROR] Reading private key from file without password is not supported.")

        val provider = new JcePEMDecryptorProviderBuilder().build(password.toCharArray)
        val keyPair = encKeyPair.decryptKeyPair(provider)

        keyPair.getPrivateKeyInfo.getEncoded

      case encPrivateKeyInfo:PKCS8EncryptedPrivateKeyInfo =>
        /*
         * This path extracts the private key from
         * a PEM private key file
         */
        if (password == null)
          throw new Exception("[ERROR] Reading private key from file without password is not supported.")

        val builder = new JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC")
        val provider = builder.build(password.toCharArray)

        val privateKeyInfo = encPrivateKeyInfo.decryptPrivateKeyInfo(provider)
        privateKeyInfo.getEncoded

      case keyPair:PEMKeyPair =>
        keyPair.getPrivateKeyInfo.getEncoded

      case privateKeyInfo:PrivateKeyInfo =>
        privateKeyInfo.getEncoded

      case _ =>
        throw new Exception(s"Unknown key object $keyObj detected.")
    }

    val keySpec = new PKCS8EncodedKeySpec(keyBytes)

    val factory = KeyFactory.getInstance("RSA", "BC")
    factory.generatePrivate(keySpec)

  }

  /**
   * Load a Java KeyStore located at keystoreFile of keystoreType and
   * keystorePassword
   */
  def loadKeystore(keystoreFile: String, keystoreType: String, keystorePassword: String): KeyStore = {

    var keystore:KeyStore = null
    if (keystoreFile != null) {

      keystore = KeyStore.getInstance(keystoreType)
      val passwordArr = if (keystorePassword == null) null
      else keystorePassword.toCharArray

      val is = Files.newInputStream(Paths.get(keystoreFile))

      try keystore.load(is, passwordArr)
      finally if (is != null) is.close()

    }

    keystore

  }

  def createKeystore:KeyStore = {
    /*
     * Create a default (JKS) keystore without any password.
     * Method load(null, null) indicates to create a new one
     */
    val keystore = KeyStore.getInstance(KeyStore.getDefaultType)
    keystore.load(null, null)
    keystore

  }

}

