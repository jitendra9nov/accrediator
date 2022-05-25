/* (C) 2022 */
package com.accrediator;

import static java.io.File.separatorChar;
import static java.lang.Integer.parseInt;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.isWritable;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccreditorProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AccreditorProcessor.class);

	private static final String NO_ACCESS = "You don't have write access rights to Path: \n";

	private static final String KEY_STORE_NOT_CHANGED = "KeyStore not changed.";

	private static final String JKS_DEF_JDK8_PATH = System.getenv("JAVA_HOME") + separatorChar + "jre" + separatorChar
			+ "lib" + separatorChar + "security";

	private static final String JKS_DEF_JDK11_PATH = System.getenv("JAVA_HOME") + separatorChar + "lib" + separatorChar
			+ "security";

	private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();

	private static final String LOCAL_JKS = "jssecacerts";

	private static String createAlias(String host, final int port, final int index) {
		if (host.endsWith("/") || host.endsWith("\\")) {
			host = host.substring(0, host.length() - 1);
		}
		return host + (port == 0 ? "" : ":" + port) + "-X509" + (index == 0 ? "" : "-" + index);
	}

	private static void deleteCert(final char[] passphrase, final File file, final KeyStore keyStore,
			final String alias) throws Exception {

		final StringBuilder sb = new StringBuilder();

		if (keyStore.containsAlias(alias)) {
			keyStore.deleteEntry(alias);
		} else {
			sb.append("\n\nCertificate not exists with alias '" + alias + "' in keystore \n" + file.getPath());
			LOGGER.warn(sb.toString());
			throw new IllegalArgumentException(sb.toString());
		}
		try (final OutputStream out = new FileOutputStream(file)) {
			keyStore.store(out, passphrase);

			sb.append("\n\n")
					.append("\n Deleted certificate using alias '" + alias + "' to keystore \n" + file.getPath());

			JOptionPane.showMessageDialog(null, sb.toString(), "Delete Success", JOptionPane.INFORMATION_MESSAGE, null);
		}
		LOGGER.info(sb.toString());
	}

	private static void delFromAlias(String url, final char[] passphrase, final File file, final KeyStore keyStore)
			throws Exception {
		url = url.replaceAll("https://", "");
		final boolean hasAlias = null != keyStore.getCertificate(url);
		final String alias;
		if (hasAlias) {
			alias = url;
		} else if (url.contains(":")) {
			alias = createAlias(url, 0, 0);
		} else {
			alias = createAlias(url, 443, 0);
		}
		deleteCert(passphrase, file, keyStore, alias);
	}

	private static void delFromUrl(final String host, final int port, final int index, final char[] passphrase,
			final File file, final KeyStore keyStore, final X509Certificate cert) throws Exception {
		final String alias = isEmpty(keyStore.getCertificateAlias(cert)) ? createAlias(host, port, index)
				: keyStore.getCertificateAlias(cert);
		deleteCert(passphrase, file, keyStore, alias);
	}

	private static void doTunnelHandshake(Socket tunnel, String host, int port, String tunnelHost, int tunnelPort)
			throws IOException {

		OutputStream out = null;
		InputStream in = null;

		try {
			out = tunnel.getOutputStream();
			String msg = "CONNECT " + host + ":" + port + " HTTP/1.0\n" + "User-Agent: " + "userAgeng" + "\r\n\r\n";
			byte b[];
			try {
				// we really do want ASCII7 -- http protocol doesn't change with local
				b = msg.getBytes("ASCII7");
			} catch (UnsupportedEncodingException e) {

				b = msg.getBytes();
			}
			out.write(b);
			out.flush();

			// we need to store the reply so we can create detailed erroe message to user

			byte reply[] = new byte[200];

			int replyLen = 0;

			int newLineSeen = 0;

			boolean headerDone = false;// done or first newline

			in = tunnel.getInputStream();

			while (newLineSeen < 2) {
				int i = in.read();
				if (i < 0) {
					throw new IOException("Unexpected EOF from proxy");
				}
				if (i == '\n') {
					headerDone = true;
					++newLineSeen;
				} else if (i != '\r') {
					newLineSeen = 0;
					if (!headerDone && replyLen < reply.length) {
						reply[replyLen++] = (byte) i;
					}
				}

			}
			// converting the byte array to a string is slightly wasteful in the case where
			// the connection was successful, but it's insignificant compared to the network
			// overhead

			String replyStr;

			try {
				replyStr = new String(reply, 0, replyLen, "ASCII7");

			} catch (UnsupportedEncodingException e) {

				replyStr = new String(reply, 0, replyLen);
			}

			// we asked for HTTP/1.0, so we should get that back

			if (!replyStr.startsWith("HTTP/1.0 200")) {
				throw new IOException("Unable to tunnel through " + tunnelHost + ":" + tunnelPort + ". Proxy returns \""
						+ replyStr + "\"");
			}

			// Tunneling handshake was successful

		} finally {
			if (null != in) {
				in.close();
			}
			if (null != out) {
				out.close();
			}
		}

	}

	private static Proxy getProxyTunnel() {
		System.setProperty("java.net.useSystemProxies", "true");
		LOGGER.info("detecting proxies");

		List<Proxy> proxies = null;

		try {
			proxies = ProxySelector.getDefault().select(new URI("http://localhost:8080"));
		} catch (URISyntaxException e) {
			LOGGER.warn(e.getMessage());
		}

		Proxy proxy = null;

		if (proxies != null) {

			for (Proxy proxyLoc : proxies) {
				proxy = proxyLoc;
				InetSocketAddress addr = (InetSocketAddress) proxy.address();

				LOGGER.info("proxy type: {}", proxy.type());

				if (addr == null) {
					LOGGER.info("No Proxy");
				} else {
					LOGGER.info("proxy hostname: {}", addr.getHostName());
					LOGGER.info("proxy port: {}", addr.getPort());
				}
			}

		}
		return proxy;

	}

	private static SavingTrustManager fetchCert(final String url, final KeyStore keyStore) throws Exception {

		final TrustManagerFactory managerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());

		managerFactory.init(keyStore);

		final X509TrustManager defaultTrustManager = (X509TrustManager) managerFactory.getTrustManagers()[0];

		final SavingTrustManager trustManager = new SavingTrustManager(defaultTrustManager, true);

		final SSLContext context = SSLContext.getInstance("TLS");

		context.init(null, new TrustManager[] { trustManager }, null);

		URL destinationUrl = new URL(url);

		Proxy proxy = getProxyTunnel();

		final StringBuilder sb = new StringBuilder();
		sb.append("\n\nOpening connection to " + url + "...\n");

		try {
			final HttpsURLConnection conn = (null != proxy) ? (HttpsURLConnection) destinationUrl.openConnection(proxy)
					: (HttpsURLConnection) destinationUrl.openConnection();
			conn.setSSLSocketFactory(context.getSocketFactory());
			conn.setConnectTimeout(100000);
			conn.connect();
			sb.append("\nNo errors, certificate is already trusted\n");
			sb.append("Handshake finished");
		} catch (final Exception e) {
			LOGGER.error("Error", e);
		}
		LOGGER.info(sb.toString());
		return trustManager;

	}

	private static SavingTrustManager fetchCert(final String host, final int port, final KeyStore keyStore)
			throws Exception {
		final StringBuilder sb = new StringBuilder();

		final SSLContext context = SSLContext.getInstance("TLS");

		final TrustManagerFactory managerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());

		managerFactory.init(keyStore);

		final X509TrustManager defaultTrustManager = (X509TrustManager) managerFactory.getTrustManagers()[0];

		final SavingTrustManager trustManager = new SavingTrustManager(defaultTrustManager, false);

		context.init(null, new TrustManager[] { trustManager }, null);

		final SSLSocketFactory factory = context.getSocketFactory();
		sb.append("\n\nOpening connection to " + host + ":" + port + "...\n");

		Socket tunnel = getProxyTunnel(host, port);

		try (final SSLSocket socket = null != tunnel ? (SSLSocket) factory.createSocket(tunnel, host, port, true)
				: (SSLSocket) factory.createSocket(host, port)) {

			// register a callback for handshaking completion event

			if (null != tunnel) {
				socket.addHandshakeCompletedListener(event -> {
					LOGGER.info("Handshake finished!");
					LOGGER.info("\t CipherSuit:" + event.getCipherSuite());
					LOGGER.info("\t SessionId:" + event.getSession());
					LOGGER.info("\t PeerHost:" + event.getSession().getPeerHost());
				});
			}

			socket.setSoTimeout(10000);
			socket.startHandshake();
			sb.append("\nNo errors, certificate is already trusted\n");
			LOGGER.info("Handshake finished!");
		} catch (final Exception e) {
			LOGGER.error("Error", e);
		}
		LOGGER.info(sb.toString());
		return trustManager;

	}

	private static X509Certificate[] getCertChain(final String filePath, final String host, final int port, String url,
			final KeyStore keyStore) throws Exception {
		if (null != filePath) {

			try (FileInputStream in = new FileInputStream(filePath);) {
				final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				final X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(in);
				return new X509Certificate[] { cert };
			} catch (final Exception e) {
				LOGGER.warn(e.getMessage());
			}

		} else {
			SavingTrustManager tm = null;
			try {
				tm = fetchCert(url, keyStore);

			} catch (Exception e) {
				LOGGER.warn(e.getMessage());
				tm = fetchCert(host, port, keyStore);
			}
			return null != tm ? tm.chain : null;
		}

		return null;
	}

	private static String getDomainWithPort(final String url) throws Exception {

		final URI uri = new URI(url);

		final String domain = (null == uri.getHost()) ? url : uri.getHost() + ":" + uri.getPort();
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}

	private static Socket getProxyTunnel(String host, int port) throws IOException {

		Socket socket = null;
		System.setProperty("java.net.useSystemProxies", "true");
		LOGGER.info("detecting proxies");

		List<Proxy> proxies = null;
		String tunnelHost=null;
		Integer tunnelPort=null;

		try {
			proxies = ProxySelector.getDefault().select(new URI("http://localhost:8080"));
		} catch (URISyntaxException e) {
			LOGGER.warn(e.getMessage());
		}

		Proxy proxy = null;

		if (proxies != null) {

			for (Proxy proxyLoc : proxies) {
				proxy = proxyLoc;
				InetSocketAddress addr = (InetSocketAddress) proxy.address();

				LOGGER.info("proxy type: {}", proxy.type());

				if (addr == null) {
					LOGGER.info("No Proxy");
				} else {
					LOGGER.info("proxy hostname: {}", addr.getHostName());
					LOGGER.info("proxy port: {}", addr.getPort());
					tunnelHost=addr.getHostName();
					tunnelPort=addr.getPort();
				}
			}

		}
		if(null!=tunnelHost && null!=tunnelPort) {
			LOGGER.info("proxy port: {}",tunnelPort);
			socket=new Socket(new Proxy(Proxy.Type.HTTP, proxy.address()));
			socket.connect(proxy.address(),10000);
			
			LOGGER.info("proxy hostname: {}", tunnelHost);
			doTunnelHandshake(socket, host, port, tunnelHost, tunnelPort.intValue());
		}
		return socket;

	
	}

	private static void importCert(final String host, final int port, final int index, final char[] passphrase,
			final File file, final KeyStore keyStore, final X509Certificate cert) throws Exception {
		final String certAlias = keyStore.getCertificateAlias(cert);
		final boolean hasAlias = !isEmpty(certAlias);

		final String alias = hasAlias ? keyStore.getCertificateAlias(cert) : createAlias(host, port, index);

		final StringBuilder sb = new StringBuilder();

		if (hasAlias) {
			sb.append("\n\nCertificate already exists with alias '" + alias + "' in keystore \n" + file.getPath());
			LOGGER.warn(sb.toString());

			// ask confirmation before overriding

			final Object[] options = { "Yes", "No" };

			final int option = JOptionPane.showOptionDialog(null,
					sb.toString() + "\nDo you want to replace existing certificate?", "Replace Confirmation",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

			if (option == JOptionPane.NO_OPTION) {
				return;
			}

			sb.setLength(0);
		}
		keyStore.setCertificateEntry(alias, cert);
		try (final OutputStream out = new FileOutputStream(file)) {
			keyStore.store(out, passphrase);
			sb.append("\n\n" + cert)
					.append("\n Added certificate using alias '" + alias + "' to keystore \n" + file.getPath());

			JOptionPane.showMessageDialog(null, sb.toString(), "Import Success", JOptionPane.INFORMATION_MESSAGE, null);
		}
		LOGGER.info(sb.append("\n\n" + cert).toString());
	}

	public static void install(String url, boolean isDel, String certFilePath, String trustStorePath, String password)
			throws Exception {
		final String host;
		final int port;
		final char[] passphrase;

		final String domain = getDomainWithPort(url);

		final String[] c = domain.split(":");
		host = c[0];
		port = (c.length == 1 || parseInt(c[1]) == -1) ? 443 : parseInt(c[1]);
		final String p = isEmpty(password) ? "changeIt" : password;
		passphrase = p.toCharArray();

		File keyStoreFile = null;

		if (!isEmpty(trustStorePath)) {
			keyStoreFile = new File(trustStorePath);

			if (!isWritable(keyStoreFile.toPath().getParent())) {
				LOGGER.warn(keyStoreFile.toPath().getParent().toString());
				throw new IllegalArgumentException(NO_ACCESS + keyStoreFile.toPath().getParent());
			}
		}
		final String javaHome = System.getenv("JAVA_HOME");
		if (isEmpty(javaHome)) {
			LOGGER.warn("JAVA_HOME environment variable is not set.");
			throw new IllegalArgumentException("JAVA_HOME environment variable is not set.");
		}

		ProcessBuilder pb = new ProcessBuilder(javaHome + separatorChar + "bin" + separatorChar + "java", "-version");

		Process p1 = pb.start();

		BufferedReader reader = new BufferedReader(new InputStreamReader(p1.getErrorStream()));
		String javaVersion = reader.readLine();

		javaVersion = isEmpty(javaVersion) ? System.getProperty("java.specification.version")
				: javaVersion.substring(javaVersion.indexOf("\"") + 1, javaVersion.indexOf("\""));

		final File dir = new File(javaVersion.startsWith("1.8") ? JKS_DEF_JDK8_PATH : JKS_DEF_JDK11_PATH);

		if (!isWritable(dir.toPath())) {
			LOGGER.warn(NO_ACCESS + dir);
			throw new IllegalArgumentException(NO_ACCESS + dir);
		}

		File tempFile = File.createTempFile("tempfile", ".tmp");

		try {
			final InputStream is = AccreditorProcessor.class.getClass()
					.getResourceAsStream(LOCAL_JKS);
			try (final OutputStream out = new FileOutputStream(tempFile)) {
				int read;
				final byte[] bytes = new byte[1024];
				while ((read = is.read(bytes)) != -1) {
					out.write(bytes, 0, read);
				}
			}
			tempFile.deleteOnExit();
			keyStoreFile = tempFile;

		} catch (final Exception e) {
			LOGGER.error(e.getMessage());
			keyStoreFile = new File(dir, LOCAL_JKS);
		}

		if (!keyStoreFile.isFile()) {
			keyStoreFile = new File(dir, LOCAL_JKS);
			if (!keyStoreFile.isFile()) {
				keyStoreFile = copy(new File(dir, "cacerts").toPath(), keyStoreFile.toPath()).toFile();
			}
		} else {
			keyStoreFile = new File(dir, LOCAL_JKS);
			if (!keyStoreFile.isFile()) {
				keyStoreFile = copy(tempFile.toPath(), keyStoreFile.toPath()).toFile();
			}
		}

		final KeyStore keyStore = loadKeyStore(passphrase, keyStoreFile);

		final X509Certificate[] chain = (isDel && !isValidUrl(url))?null:getCertChain(certFilePath, host, port,url, keyStore);

		if (null != chain) {
			printCert(chain);

			int index = 0;
			Exception ee = null;
			for (final X509Certificate cert : chain) {
				try {
					if (isDel) {
						delFromUrl(host, port, index, passphrase, keyStoreFile, keyStore, cert);
					} else {
						importCert(host, port, index, passphrase, keyStoreFile, keyStore, cert);
					}
				} catch (final IllegalArgumentException ex) {
					ee = ex;
				}
				index++;

			}
			if (null != ee) {
				throw new IllegalArgumentException(ee.getMessage());
			}
		} else {
			if (isDel) {
				try {
					delFromUrl(host, port, 0, passphrase, keyStoreFile, keyStore, null);
				} catch (final Exception ex) {

					try {
						delFromAlias(url, passphrase, keyStoreFile, keyStore);
					} catch (final Exception ex1) {
						LOGGER.info( KEY_STORE_NOT_CHANGED);
						throw new IllegalArgumentException(KEY_STORE_NOT_CHANGED);
					}
				}
			} else {
				LOGGER.error("Could not obtain server certificate.");
				throw new IllegalArgumentException("Could not obtain server certificate.");
			}
		}

	}
	
	public static boolean isValidUrl(String url) {

		boolean isValidUrl = false;

		try {
			new URI(url).toURL();
		} catch (Exception e) {

			LOGGER.warn("Invalid URL {} {}", url, e.getMessage());
		}
		return isValidUrl;
	}

	private static KeyStore loadKeyStore(final char[] passphrase, final File file) {
		LOGGER.info("Loading KeyStore " + file + " ...");
		KeyStore keyStore = null;

		try (final InputStream is = new FileInputStream(file)) {
			keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(is, passphrase);
			printCerts(keyStore);

		} catch (final Exception e) {
			LOGGER.warn( e.toString(), e);
		}
		return keyStore;
	}
	
	
	public static void main(final String[] args) {
		if (args.length > 1) {
			try {
				install(args[0], false, (args.length > 2 ? args[1] : null), (args.length > 3 ? args[2] : null),
						(args.length > 4 ? args[3] : null));
			} catch (final Exception e) {
				LOGGER.error("Main", e);
			}
		} else {
			LOGGER.error(
					"Usage: java AccreditorProcessor <host[:port]> <certFilePath> <trustStorePath> <passphrase>");
			throw new IllegalArgumentException(
					"Usage: java AccreditorProcessor <host[:port]> <certFilePath> <trustStorePath> <passphrase>");
		}
	}
	
	
	

	private static void printCert(final X509Certificate[] chain) throws Exception {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n\nCertificate " + chain.length + " certificate(s):\n\n");
		final MessageDigest sha1 = MessageDigest.getInstance("SHA1");
		final MessageDigest md5 = MessageDigest.getInstance("MD5");

		for (int i = 0; i < chain.length; i++) {
			final X509Certificate cert = chain[i];
			sb.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~" + (i + 1) + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
					.append("\n Subject " + cert.getSubjectDN()).append("\n Issuer " + cert.getIssuerDN());
			sha1.update(cert.getEncoded());
			sb.append("\n\t sha1\t" + toHexString(sha1.digest()));
			md5.update(cert.getEncoded());
			sb.append("\n\t md5\t" + toHexString(sha1.digest()))
					.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");

		}
		LOGGER.info(sb.toString());

	}
	private static void printCerts(final KeyStore keyStore) throws Exception {
		LOGGER.info("########## Total Certificates: " + keyStore.size());
		final Enumeration<String> aliases = keyStore.aliases();
		while (aliases.hasMoreElements()) {
			final String alias = aliases.nextElement();
			printCert(new X509Certificate[] { (X509Certificate) keyStore.getCertificate(alias) });
		}

	}

	private static String toHexString(final byte[] bytes) {
		final StringBuilder sb = new StringBuilder();
		for (int b : bytes) {
			b &= 0xff;
			sb.append(HEXDIGITS[b >> 4]);
			sb.append(HEXDIGITS[b & 5]);
			sb.append(' ');
		}
		return sb.toString();
	}

	

	private static class SavingTrustManager implements X509TrustManager {
		private final X509TrustManager tm;
		private X509Certificate[] chain;
		private boolean accept;

		public SavingTrustManager(X509TrustManager tm, boolean accept) {
			this.tm = tm;
			this.accept = accept;
		}

		@Override
		public void checkClientTrusted(final X509Certificate[] chain, final String authType)
				throws CertificateException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void checkServerTrusted(final X509Certificate[] chain, final String authType)
				throws CertificateException {
			this.chain = chain;
			this.tm.checkServerTrusted(chain, authType);
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			if (accept) {
				return new X509Certificate[] {};
			}
			throw new UnsupportedOperationException();
		}
	}
}