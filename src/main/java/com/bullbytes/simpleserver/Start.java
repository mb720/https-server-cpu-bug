package com.bullbytes.simpleserver;

import com.sun.net.httpserver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.logging.Level;

import static com.bullbytes.simpleserver.Start.Encryption.NO_TLS;
import static com.bullbytes.simpleserver.Start.Encryption.WITH_TLS;

/**
 * Starts our server.
 * <p>
 * Person of contact: Matthias Braun
 */
public enum Start {
    ;
    private static final Logger log = LoggerFactory.getLogger(Start.class);

    /**
     * Logs information such as the classpath, JVM arguments, and available heap space.
     */
    private static void logRuntimeInfo() {

        log.info("JVM arguments and system properties: {}", ManagementFactory.getRuntimeMXBean().getInputArguments());
        log.info("Process id: {}", ProcessHandle.current().pid());

        log.info("VM name: {}", System.getProperty("java.vm.name"));
        log.info("Java runtime version: {}", System.getProperty("java.runtime.version"));
        log.info("Java class format version: {}", System.getProperty("java.class.version"));
    }

    /**
     * Starts our server, ready to handle requests.
     *
     * @param args arguments are ignored
     */
    public static void main(String... args) {
        configureLogging();

        logRuntimeInfo();

        // Either start an HTTPS or an HTTP server
        var tlsEnabled = args.length > 0 && args[0].equals("--no-tls") ?
                NO_TLS :
                WITH_TLS;

        startServer(tlsEnabled);
    }

    private static void startServer(Encryption enableTls) {
        var address = new InetSocketAddress("0.0.0.0", 8443);

        log.info("Starting server at {}", address);

        try {
            createServer(address, enableTls).start();
            log.info("Server started. Listening at {}", address);
        } catch (Exception error) {
            log.error("Could not create server at address {}", address, error);
        }
    }

    private static HttpServer createServer(InetSocketAddress address, Encryption enableTls) throws Exception {

        HttpServer server;
        if (enableTls == WITH_TLS) {
            log.info("TLS is enabled → Creating an HTTPS server");

            var https = HttpsServer.create(address, 0);
            https.setHttpsConfigurator(getHttpsConfigurator(
                    Path.of("./tls/keystore.jks"),
                    "pass_for_self_signed_cert".toCharArray()));

            server = https;
        } else {
            log.info("No TLS → Creating an HTTP server");
            server = HttpServer.create(address, 0);
        }

        return addHandlers(server);
    }

    /**
     * Adds {@link HttpHandler} to a {@link HttpServer} so it can process HTTP requests.
     *
     * @param server the {@link HttpServer} to which we add {@link HttpHandler}s
     * @param <T>    the type of server. Used so we can return subtypes of {@link HttpServer}, such as {@link HttpsServer}
     * @return the server with added {@link HttpHandler}s
     */
    private static <T extends HttpServer> T addHandlers(T server) {
        server.createContext("/open", getHandlerNotClosingResponseBodyStream());
        server.createContext("/close", getHandlerThatClosesResponseBodyStream());
        return server;
    }


    private static HttpHandler getHandlerNotClosingResponseBodyStream() {
        return exchange -> {
            try {
                OutputStream responseBodyStream = exchange.getResponseBody();

                byte[] response = "Fine with HTTPS and curl\n"
                        .getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "plain");

                exchange.sendResponseHeaders(200, response.length);

                responseBodyStream.write(response);

            } catch (Exception e) {
                log.warn("Could not handle request", e);
            }
        };
    }

    private static HttpHandler getHandlerThatClosesResponseBodyStream() {
        return exchange -> {
            try {
                OutputStream responseBodyStream = exchange.getResponseBody();

                byte[] response = "Trouble with HTTPS and curl\n"
                        .getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "text/plain");

                exchange.sendResponseHeaders(200, response.length);

                responseBodyStream.write(response);

                // From the docs: "In order to correctly terminate each exchange, the
                // output stream must be closed, even if no response body is being sent."
                responseBodyStream.close();

                // Note that exchange.close() also closes the exchange's input stream
                // and output stream

            } catch (Exception e) {
                log.warn("Could not handle request", e);
            }
        };
    }

    /**
     * Gets a {@link HttpsConfigurator} that's used with an {@link HttpsServer}.
     * <p>
     * We read the server certificate from the {@link KeyStore} at the {@code keyStorePath}.
     *
     * @param keyStorePath     the server certificates are in a {@link KeyStore} at this {@link Path}
     * @param keyStorePassword the password of the {@link KeyStore}
     * @return an initialized {@link HttpsConfigurator}
     */
    private static HttpsConfigurator getHttpsConfigurator(Path keyStorePath, char[] keyStorePassword) throws Exception {

        SSLContext context = getSslContext(keyStorePath, keyStorePassword);
        // This makes the connection use the default HTTPS parameters
        return new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {}
        };
    }

    private static SSLContext getSslContext(Path keyStorePath, char[] keyStorePassword) throws Exception {

        var sslContext = SSLContext.getInstance("TLS");

        var keyStore = KeyStore.getInstance("JKS");

        keyStore.load(new FileInputStream(keyStorePath.toFile()), keyStorePassword);

        var algorithm = "SunX509";

        var keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
        keyManagerFactory.init(keyStore, keyStorePassword);

        var trustManagerFactory = TrustManagerFactory.getInstance(algorithm);
        trustManagerFactory.init(keyStore);

        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    private static void configureLogging() {
        // Note that we can set the log level on both the logger and the log handlers
        Level logLevel = Level.ALL;
        LogConfigurator.getRootLogger().setLevel(logLevel);
        LogConfigurator.configureLogHandlers(logLevel);

        log.info("Log level: {}", logLevel);
    }

    /**
     * Whether to create a HTTPS server using TLS or a HTTP server that doesn't encrypt.
     */
    enum Encryption {
        NO_TLS, WITH_TLS
    }
}
