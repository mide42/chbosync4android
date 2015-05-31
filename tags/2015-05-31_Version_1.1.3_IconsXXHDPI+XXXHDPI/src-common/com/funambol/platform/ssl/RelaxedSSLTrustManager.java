package com.funambol.platform.ssl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import com.funambol.util.Log;


/**
 * This class was added for the PTBV SyncML Client.
 * 
 */
public class RelaxedSSLTrustManager implements X509TrustManager {

	private String certKey = null;
	private static final X509Certificate[] _AcceptedIssuers = new X509Certificate[] {};
	private static final String LOG_NAME = "TrustManager";

	RelaxedSSLTrustManager(String certKey) {
		super();
		this.certKey = certKey;
	}

	RelaxedSSLTrustManager() {
		super();
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType)
			throws CertificateException {
		if (this.certKey == null) {
			// This is the Accept All certificates case.
			return;
		}

		// Otherwise, we have a certKey defined. We should now examine the one
		// we got from the server.
		// They match? All is good. They don't, throw an exception.
		String our_key = this.certKey.replaceAll("\\s+", "");
		try {
			// Assume self-signed root is okay?
			X509Certificate ss_cert = chain[0];
			String thumbprint = RelaxedSSLTrustManager.getThumbPrint(ss_cert);
			if (Log.isLoggable(Log.DEBUG)) {
				Log.debug(LOG_NAME, "Certificate Thumbprint: " + thumbprint);
			}
			if (our_key.equalsIgnoreCase(thumbprint)) {
				return;
			} else {
				throw new CertificateException("Certificate key [" + thumbprint + "] doesn't match expected value.");						
			}
		} catch (NoSuchAlgorithmException e) {
			throw new CertificateException("Unable to check self-signed cert, unknown algorithm. " + e.toString());
		}

	}

	public boolean isClientTrusted(X509Certificate[] chain) {
		return true;
	}

	public boolean isServerTrusted(X509Certificate[] chain) {
		return true;
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return _AcceptedIssuers;
	}

	// Thank you:
	// http://stackoverflow.com/questions/1270703/how-to-retrieve-compute-an-x509-certificates-thumbprint-in-java
	private static String getThumbPrint(X509Certificate cert)
			throws NoSuchAlgorithmException, CertificateEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] der = cert.getEncoded();
		md.update(der);
		byte[] digest = md.digest();
		return hexify(digest);
	}

	private static String hexify(byte bytes[]) {
		char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				             'a', 'b', 'c', 'd', 'e', 'f' };

		StringBuffer buf = new StringBuffer(bytes.length * 2);

		for (int i = 0; i < bytes.length; ++i) {
			buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
			buf.append(hexDigits[bytes[i] & 0x0f]);
		}

		return buf.toString();
	}
}