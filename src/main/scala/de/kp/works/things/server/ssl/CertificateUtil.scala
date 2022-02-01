package de.kp.works.things.server.ssl

import org.bouncycastle.asn1.{ASN1Encodable, DERSequence}
import org.bouncycastle.asn1.x500.{X500Name, X500NameBuilder}
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509._
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509ExtensionUtils, JcaX509v3CertificateBuilder}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PKCS8Generator
import org.bouncycastle.openssl.jcajce.{JcaPEMWriter, JcaPKCS8Generator, JceOpenSSLPKCS8EncryptorBuilder}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder

import java.io.{ByteArrayInputStream, FileOutputStream, FileWriter}
import java.math.BigInteger
import java.nio.file.{Files, Paths}
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.spec.PKCS8EncodedKeySpec
import java.security._
import java.time.{LocalDate, ZoneOffset}
import java.util.{Calendar, Date}

object CertificateUtil {

  private val KEY_ALGO          = "RSA"
  private val KEY_SIZE          = 2048
  private val KEYSTORE_TYPE     = "PKCS12"
  private val SECURITY_PROVIDER = "BC"
  private val SIGNATURE_ALGO    = "SHA256withRSA"

  private val folder = "/Users/krusche/IdeaProjects/works-things/security"

  Security.addProvider(new BouncyCastleProvider())

  def main(args:Array[String]):Unit = {

    //createCertChain()

  }

  /**
   * A helper method to read a non encrypted
   * private key from file
   */
  def readPrivateKey():PrivateKey = {

    val path = s"$folder/private.key"

    val bytes = Files.readAllBytes(Paths.get(path))
    val spec = new PKCS8EncodedKeySpec(bytes)

    val factory = KeyFactory.getInstance("RSA")
    factory.generatePrivate(spec)

  }
  /**
   * A helper method to write a non encrypted
   * private key to file
   */
  def writePrivateKey(privateKey:PrivateKey):Unit = {

    val fos = new FileOutputStream(s"$folder/private.key")
    fos.write(privateKey.getEncoded)

    fos.close()

  }

  def privateKeyFromPEM(privatePass:String):PrivateKey = {

    val path = s"$folder/private.pem"
    SslUtil.getPrivateKeyFromPEM(path, privatePass)

  }
  /**
   * A helper method to encrypt and write a private key
   * to a PEM file
   */
  def privateKeyToPEM(privateKey:PrivateKey, privatePass:String):Unit = {

    val pemWriter = new JcaPEMWriter( new FileWriter(s"$folder/private.pem"))
    val pemObj = {

      val encryptorBuilder = new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.PBE_SHA1_3DES)
      encryptorBuilder.setRandom(new SecureRandom())

      encryptorBuilder.setPasssword(privatePass.toCharArray)
      val outputEncryptor = encryptorBuilder.build()

      val generator = new JcaPKCS8Generator(privateKey, outputEncryptor)
      generator.generate()

    }

    pemWriter.writeObject(pemObj)
    pemWriter.close()

  }
  /**
   * A helper method to write a X509 certificate
   * to a PEM file
   */
  def certificateToPEM(certificate:X509Certificate):Unit = {

    val pemWriter = new JcaPEMWriter( new FileWriter(s"$folder/certificate.pem"))
    val pemObj = certificate

    pemWriter.writeObject(pemObj)
    pemWriter.close()

  }

  def certificateFromPEM():X509Certificate = {

    val path = s"$folder/certificate.pem"
    SslUtil.getX509CertFromPEM(path)

  }

  def createCertChain():Unit = {
    /*
     * STEP #1: Initialize the KeyPair generator
     */
    val random = new SecureRandom()

    val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGO, SECURITY_PROVIDER)
    keyPairGenerator.initialize(KEY_SIZE, random)
    /*
     * Step #2: Setup start date to yesterday and the
     * end date for 2 years validity
     */
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DATE, -1)

    val startDate = calendar.getTime

    calendar.add(Calendar.YEAR, 2)
    val endDate = calendar.getTime
    /* --------------------------------------------------
     *
     *              CREATE ROOT CERTIFICATE
     */

    /*
     * STEP #3: Create a root key pair and respective
     * root certificate
     */
    val rootKeyPair = keyPairGenerator.generateKeyPair()

    val rootRND = random.nextLong().toString
    val rootSerialNum = new BigInteger(rootRND)
    /*
     * The issued by and issued to are the same for
     * root certificates
     */
    val rootCN = "dr-kruscheundpartner.de"

    val rootCertIssuer = new X500Name(s"CN=$rootCN")
    val rootCertSubject = rootCertIssuer

    val rootCertContentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGO)
      .setProvider(SECURITY_PROVIDER).build(rootKeyPair.getPrivate)

    val rootCertBuilder = new JcaX509v3CertificateBuilder(
      rootCertIssuer,
      rootSerialNum,
      startDate,
      endDate,
      rootCertSubject,
      rootKeyPair.getPublic)

    /*
     * Add Extensions: A BasicConstraint to mark root
     * certificate as CA certificate
     */
    val rootCertExtUtils = new JcaX509ExtensionUtils()
    val rootSubjectKeyIdentifier = rootCertExtUtils.createSubjectKeyIdentifier(rootKeyPair.getPublic)

    rootCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
    rootCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, rootSubjectKeyIdentifier)
    /*
     * Create a certificate holder and export to X509Certificate
     */
    val rootCertHolder = rootCertBuilder.build(rootCertContentSigner)
    val rootCert = new JcaX509CertificateConverter()
      .setProvider(SECURITY_PROVIDER).getCertificate(rootCertHolder)

    val rootPath = s"$folder/root-cert.pfx"
    val rootPass = "qwertzu"

    writeToKeystore(
      "rootCert", rootCert, rootKeyPair.getPrivate, rootPass, rootPath)

    /* --------------------------------------------------
     *
     *              CREATE CLIENT CERTIFICATE
     */

    /*
     * STEP #4: Create client key pair and respective
     * client certificate
     */
    val issuedCertKeyPair = keyPairGenerator.generateKeyPair()

    val issuedCertRND = random.nextLong().toString
    val issuedCertSerialNum = new BigInteger(issuedCertRND)

    val issuedCertCN = "hutundstiel.at"
    val issuedCertSubject = new X500Name(s"CN=$issuedCertCN")
    /*
     * Generate a new key pair and sign it using the
     * root certificate private key
     */
    val pkcs10Builder = new JcaPKCS10CertificationRequestBuilder(
      issuedCertSubject, issuedCertKeyPair.getPublic)

    val csrBuilder = new JcaContentSignerBuilder(SIGNATURE_ALGO)
      .setProvider(SECURITY_PROVIDER)
    /*
     * Sign the new key pair with the request subject
     * private key
     */
    val csrContentSigner = csrBuilder.build(issuedCertKeyPair.getPrivate)
    val csr = pkcs10Builder.build(csrContentSigner)

    val issuedCertBuilder = new X509v3CertificateBuilder(
      rootCertIssuer,
      issuedCertSerialNum,
      startDate,
      endDate,
      csr.getSubject,
      csr.getSubjectPublicKeyInfo)

    val issuedCertExtUtils = new JcaX509ExtensionUtils()
    /*
     * Add Extensions: Use BasicConstraints to indicate
     * that this certificate is not a CA
     */
    issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
    /*
     * Add issuer certificate identifier
     * as extension
     */
    issuedCertBuilder.addExtension(
      Extension.authorityKeyIdentifier, false,
      issuedCertExtUtils.createAuthorityKeyIdentifier(rootCert))

    issuedCertBuilder.addExtension(
      Extension.subjectKeyIdentifier, false,
      issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo))

    /*
     * Add intended key usage extension if needed
     */
    issuedCertBuilder.addExtension(
      Extension.keyUsage, false, new KeyUsage(KeyUsage.keyEncipherment))
    /*
     * Add DNS name if this certificate is used
     * for SSL
     */
    val asn1Encodables = Array[ASN1Encodable](
      new GeneralName(GeneralName.dNSName, "things.local"),
      new GeneralName(GeneralName.iPAddress, "127.0.0.1")
    )

    issuedCertBuilder.addExtension(
      Extension.subjectAlternativeName, false, new DERSequence(asn1Encodables))

    val issuedCertHolder = issuedCertBuilder.build(csrContentSigner)
    val issuedCert  = new JcaX509CertificateConverter()
      .setProvider(SECURITY_PROVIDER).getCertificate(issuedCertHolder)

    val issuedPath = s"$folder/issued-cert.pfx"
    val issuedPass = "qwertzu"

    writeToKeystore(
      "issuedCert", issuedCert, issuedCertKeyPair.getPrivate, issuedPass, issuedPath)

  }

  def writeToKeystore(
    certAlias:String, cert:X509Certificate, privateKey:PrivateKey,
    keystorePass:String, fileName:String):Unit = {

    val sslKeyStore = KeyStore.getInstance(KEYSTORE_TYPE, SECURITY_PROVIDER)
    sslKeyStore.load(null, null)

    sslKeyStore.setKeyEntry(certAlias, privateKey, null, Array(cert))

    val fos = new FileOutputStream(fileName)
    sslKeyStore.store(fos, keystorePass.toCharArray)

  }

  def createX509Cert(privateKey:PrivateKey, publicKey:PublicKey, random:SecureRandom):X509Certificate = {
    /*
     * STEP #1: Fill in X509 certificate fields
     */
    val subject = new X500NameBuilder(BCStyle.INSTANCE)
      .addRDN(BCStyle.CN, "de.kp.works.things")
      .build

    val id = new Array[Byte](20)
    random.nextBytes(id)

    val serial = new BigInteger(160, random)
    /*
     * STEP #2: Create X509 certificate
     */
    val startDate = LocalDate.of(2022,1,1).atStartOfDay(ZoneOffset.UTC).toInstant
    val endDate = LocalDate.of(2050,1,1).atStartOfDay(ZoneOffset.UTC).toInstant

    val certificate = new JcaX509v3CertificateBuilder(
      subject,
      serial,
      Date.from(startDate),
      Date.from(endDate),
      subject,
      publicKey)

    certificate.addExtension(Extension.subjectKeyIdentifier, false, id)
    certificate.addExtension(Extension.authorityKeyIdentifier, false, id)

    val constraints = new BasicConstraints(true)
    certificate.addExtension(
      Extension.basicConstraints, true, constraints.getEncoded)

    val keyUsage = new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature)
    certificate.addExtension(Extension.keyUsage, false, keyUsage.getEncoded())

    val keyPurposeIds = Array[KeyPurposeId](
      KeyPurposeId.id_kp_serverAuth,
      KeyPurposeId.id_kp_clientAuth
    )

    val keyUsageExt = new ExtendedKeyUsage(keyPurposeIds)
    certificate.addExtension(
      Extension.extendedKeyUsage, false, keyUsageExt.getEncoded())

    val signer = new JcaContentSignerBuilder(SIGNATURE_ALGO)
      .build(privateKey)

    val holder = certificate.build(signer)
    /*
     * STEP #4: Convert to JRE certificate
     */
    val converter = new JcaX509CertificateConverter()
    converter.setProvider(SECURITY_PROVIDER)

    val x509Certificate = converter.getCertificate(holder)
    /*
     * STEP #5: Validate certificate
     */
    x509Certificate.checkValidity(new Date())

    x509Certificate.verify(publicKey)
    x509Certificate.verify(x509Certificate.getPublicKey)

    val bais = new ByteArrayInputStream(x509Certificate.getEncoded)
    val factory = CertificateFactory.getInstance("X.509", SECURITY_PROVIDER)

    val output = factory.generateCertificate(bais)
    output.asInstanceOf[X509Certificate]

  }

}
