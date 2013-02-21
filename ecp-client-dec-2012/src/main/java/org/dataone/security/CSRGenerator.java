package org.dataone.security;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.openssl.PEMWriter;

/**
 * This class generates PKCS10 certificate signing request 
 * @author Pankaj@JournalDev.com  
 * @version 1.0
 * @see http://www.journaldev.com/223/generating-a-certificate-signing-request-using-java-api  
 */
public class CSRGenerator {
	
	private PublicKey publicKey = null;
	private PrivateKey privateKey = null;
	private KeyPairGenerator keyGen = null;
	
	public CSRGenerator() {
		// use Bouncy Castle
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

		try {
			keyGen = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		keyGen.initialize(2048, new SecureRandom());
		KeyPair keypair = keyGen.generateKeyPair();
		publicKey = keypair.getPublic();
		privateKey = keypair.getPrivate();
	}

	public String generateCSR(String cn) throws Exception {
		byte[] csr = generatePKCS10(cn, "NCEAS", "UCSB", "Santa Barbara",
				"California", "USA");
		return new String(csr, "UTF-8");
	}

	
	/**
	 * 
	 * @param CN Common Name, is X.509 speak for the name that
	 * distinguishes the Certificate best, and ties it to your
	 * Organization
	 * @param OU Organizational unit
	 * @param O Organization NAME
	 * @param L Location  
	 * @param S  State 
	 * @param C Country 
	 * @return byte array of the CSR
	 * @throws Exception
	 */
	private byte[] generatePKCS10(String CN, String OU, String O,
			String L, String S, String C) throws Exception {
		// generate PKCS10 certificate request
		String sigAlg = "MD5WithRSA";
		
		Signature signature = Signature.getInstance(sigAlg);
		signature.initSign(privateKey);
		// common, orgUnit, org, locality, state, country
		X500Principal principal = new X500Principal("CN=" + CN +", OU=" + OU + ", O=" + O + ", L=" + L + ", S=" + S + ", C=" + C);  
        
		DERSet asn1Set = new DERSet();
		
        PKCS10CertificationRequest kpGen = new PKCS10CertificationRequest(sigAlg, principal, publicKey, asn1Set, privateKey);
        StringWriter sw = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(sw);
        pemWriter.writeObject(kpGen);
        pemWriter.close();
        byte[] c = sw.toString().getBytes("UTF-8");
		return c;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public static void main(String[] args) throws Exception {
		CSRGenerator csrg = new CSRGenerator();
		System.out.println("Public Key:\n" + csrg.getPublicKey().toString());
		System.out.println("Private Key:\n" + csrg.getPrivateKey().toString());
		String csr = csrg.generateCSR("ignoreMe");
		System.out.println("CSR Request Generated!!");
		System.out.println(csr);
	}
}
