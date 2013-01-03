package org.dataone.security;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.PrivateKey;

import jettyClient.objectProviderRegisterer.ObjectProviderRegisterer;
import jettyClient.paosClient.ExchangeContent;
import jettyClient.paosClient.PaosClient;
import jettyClient.parser.ParseHelper;
import jettyClient.simpleClient.ClientExchange;
import jettyClient.simpleClient.ClientOptions;
import jettyClient.simpleClient.Connections;

import org.bouncycastle.openssl.PEMWriter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.saml.saml2.core.IDPEntry;

public class CertificateFetcher extends PaosClient {
	
	static {
		// Initialize and configure OpenSAML (Builderfactory, Marshaller...)
		try {
			InitializationService.initialize();
		} catch (InitializationException e) {
			e.printStackTrace();
		}

		// Register PAOS request header builder + marshaller.
		ObjectProviderRegisterer.register();
	}
	
	/**
	 * Default constructor uses the default httpclient
	 */
	public CertificateFetcher() {
		super(null);
		this.httpClient = getClient();
	}
	
	public CertificateFetcher(HttpClient httpClient) {
		super(httpClient);
	}
	
	/**
	 * Authenticate with the given identity provider using the username/password
	 * @param idpUrl
	 * @param username
	 * @param password
	 * @return the PEM content for a certificate+private key pair from the service provider
	 */
	public String authenticate(String spUrl, String idpUrl, String username, String password) {

		ClientOptions options = new ClientOptions();
		options.setSpURL(Connections.getURL(spUrl));
		options.setSpEndpoint(Connections.getURL(spUrl));
		options.setIdpUrl(Connections.getURL(idpUrl));
		options.setPrincipal(username);
		options.setCredentials(password);
		// have to set the idp url in the entry
		IDPEntry idpEntry = (IDPEntry) ParseHelper.buildObject(IDPEntry.DEFAULT_ELEMENT_NAME);
		idpEntry.setLoc(idpUrl);
		
		// set up the client
		HttpClient httpClient = getClient();
		try {
			httpClient.start();
		} catch (Exception e) {
			System.out.println("Could not start client.");
			e.printStackTrace();
		}
		System.out.println("Client started");

		// If there is an IdP
		if (idpEntry != null) {

			// authenticate with SP+IdP
			Connections connections = new Connections();
			ExchangeContent assertionResponse = connections.accessResource(options, idpEntry, httpClient);

			// let's get the certificate from SP
			if (assertionResponse != null) {
				return getCertificate(options, httpClient, assertionResponse);
			}
			
		}
		
		return null;
	}

	/**
	 * Create and configure a Jetty Httpclient.
	 * 
	 * @return A HttpClient
	 */
	private HttpClient getClient() {

		HttpClient client = new HttpClient();
		client.setIdleTimeout(1000);
		client.setTimeout(100000); // STATUS_EXPIRED
		client.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);

		return client;
	}
	
	/**
	 * Retrieve the PEM certificate from the SP
	 * @param options
	 * @param httpClient
	 * @param assertionContent
	 * @return
	 */
	private String getCertificate(ClientOptions options, HttpClient httpClient, ExchangeContent assertionContent) {
		// finally get the certificate
		ExchangeContent certContent = new ExchangeContent(null, null);
		certContent.setCookieField(assertionContent.getCookieField());
		
		// generate a certificate signing request
		String csr = null;
		
		// use Java to generate the csr
		CSRGenerator csrGenerator = new CSRGenerator();
		try {
			csr = csrGenerator.generateCSR("ignoreMe");
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			csr = URLEncoder.encode(csr, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// this value can be anything, but must match the CSRF cookie value
		String csrfValue = "fetchMyCertificate";
		
		String queryString = "submit=certreq&certlifetime=12&CSRF=" + csrfValue + "&certreq=" + csr;
		URL spURL = options.getSpURL();
		//URL debugSpURL = Connections.getURL("https://ecp.cilogon.org/secure/env3.php");
		//spURL = debugSpURL;
		CertificateFetcher certFetcher = new CertificateFetcher(httpClient);
		String resultString = certFetcher.sendPOST(spURL, certContent, queryString, csrfValue);
		//System.out.println(resultString);
		
		// save to PEM file with private key
		PrivateKey privateKey = csrGenerator.getPrivateKey();
		StringWriter stringWriter = new StringWriter();
		PEMWriter pemWriter = new PEMWriter(stringWriter);  
		try {
			pemWriter.writeObject(privateKey);
			pemWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String privateKeyString = stringWriter.toString();
		String certificateContent = resultString;
		
		// return the pem
		String clientPEM = privateKeyString + certificateContent;
		System.out.println(clientPEM);
		
		return clientPEM;
			
	}

	/**
	 * Send a a POST to the endpoint using the given parameter string
	 * 
	 * @param endpoint
	 * @param content
	 * @return Returns an ExchangeContent object with a response.
	 */
	private String sendPOST(URL endpoint, ExchangeContent content, String paramString, String csrfValue) {

		String results = null;
		
		// Create a new POST exchange.
		ClientExchange clientExchange = getPOSTExchange(endpoint);
		
		// Fill with params
		// Add content to the Exchange
		clientExchange.setRequestContentType("application/x-www-form-urlencoded;charset=utf-8");
		clientExchange.setRequestContent(new ByteArrayBuffer(paramString.getBytes()));

		System.out.println("\nSent to " + clientExchange.getAddress().getHost()
				+ clientExchange.getRequestURI() + "\n" + paramString);

		// Add the cookie to the Exchange (if there is one)
		if (!content.getCookieField().equals("")) {
			// the shibboleth cookie needs to be added
			String shibCookie = content.getCookieField() + ";";

			// add extra csrf cookie
			String csrCookie = "CSRF=" + csrfValue + "; path=\"/\"; domain=" + endpoint.getHost() +"; port=" + endpoint.getPort() + "; path_spec; secure; version=1;";

			// together in one header
			clientExchange.setRequestHeader(HttpHeaders.COOKIE, shibCookie + " " + csrCookie);

		}

		// Set realmResolver if there is one to set.
		if (content.getRealmResolver() != null)
			httpClient.setRealmResolver(content.getRealmResolver());

		// Send exchange
		clientExchange = exchangeContent(httpClient, clientExchange);

		// exchangeContent() will return null when something fails.
		if (clientExchange != null) {
			
			try {
				results = clientExchange.getResponseContent();
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} else {
			System.out.println("Could not send envelope.");
		}
		return results;
	}
	
	public static void main(String[] args) {
		CertificateFetcher cf = new CertificateFetcher();
		String spUrl = args[0]; //"https://ecp.cilogon.org/secure/getcert/";
		String idpUrl = args[1]; // "https://idp.protectnetwork.org/protectnetwork-idp/profile/SAML2/SOAP/ECP";
		String username = args[2]; //"leinfelder";
		String password = args[3]; //"";
		cf.authenticate(spUrl, idpUrl, username, password);
	}

}
