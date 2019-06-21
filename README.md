# Build instructions
To start the HTTPS server:

    ./gradlew run

To start the HTTP server (no TLS):

    ./gradlew run --args="--no-tls"

## Prerequisites
* JDK 11

# Reproduce CPU issue with [HttpsServer](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpsServer.html)

After starting the server (see above), send an HTTP request with [curl](https://curl.haxx.se/):

    curl -k https://localhost:8443/open

This will trigger the [handler](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpHandler.html) that leaves the stream to which we write the [request body](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpExchange.html#getResponseBody()) open. This works fine, it does *not* cause one CPU to be used 100%.

But this will:

    curl -k https://localhost:8443/close

This other handler processing the request *does* close the stream to which we write the [request body](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpExchange.html#getResponseBody()). This will yield an HTTP response but the server becomes unresponsive afterwards due to high CPU load.

## Platforms where the bug occurs
* Arch Linux 5.1.7:
  * OpenJDK 12.0.1+12
  * OpenJDK 11.0.4+7
  * OpenJDK 11.0.3+4

## What works
High CPU load does not occur when connecting to the HTTPS server using a browser:

    firefox https://localhost:8443/close

Also, using curl to connect to a regular HTTP server without TLS works fine:

    ./gradlew run --args="--no-tls"
    curl http://localhost:8443/close

## Findings from Java Flight Recorder
### Threads Allocating
The thread performing the most allocation is likely 'HTTP-Dispatcher'. This is the most common allocation path for that class:

    SSLEngineImpl.writeRecord(ByteBuffer[], int, int, ByteBuffer[], int, int) (50.2 %)
    SSLEngineImpl.wrap(ByteBuffer[], int, int, ByteBuffer[], int, int)
    SSLEngineImpl.wrap(ByteBuffer[], int, int, ByteBuffer)
    SSLEngine.wrap(ByteBuffer, ByteBuffer)
    SSLStreams$EngineWrapper.wrapAndSendX(ByteBuffer, boolean)
    SSLStreams.doClosure()

### Allocated Classes
The most allocated class is likely 'javax.net.ssl.SSLEngineResult'. This is the most common allocation path for that class:

    SSLEngineImpl.writeRecord(ByteBuffer[], int, int, ByteBuffer[], int, int) (100 %)
    SSLEngineImpl.wrap(ByteBuffer[], int, int, ByteBuffer[], int, int)
    SSLEngineImpl.wrap(ByteBuffer[], int, int, ByteBuffer)
    SSLEngine.wrap(ByteBuffer, ByteBuffer)
    SSLStreams$EngineWrapper.wrapAndSendX(ByteBuffer, boolean)
    SSLStreams.doClosure()

