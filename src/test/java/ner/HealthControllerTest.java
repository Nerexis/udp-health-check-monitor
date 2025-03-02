package ner;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Allows @BeforeAll to initialize test setup
class HealthControllerTest {

    static final DatagramSocket TEST1_SOCKET;
    static final DatagramSocket TEST2_SOCKET;
    static final DatagramSocket TEST3_SOCKET;

    static {
        try {
            TEST1_SOCKET = new DatagramSocket(0);
            System.setProperty("TEST1_SOCKET_PORT", String.valueOf(TEST1_SOCKET.getLocalPort())); // Inject port
            TEST2_SOCKET = new DatagramSocket(0);
            System.setProperty("TEST2_SOCKET_PORT", String.valueOf(TEST2_SOCKET.getLocalPort()));
            TEST3_SOCKET = new DatagramSocket(0);
            System.setProperty("TEST3_SOCKET_PORT", String.valueOf(TEST3_SOCKET.getLocalPort()));
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }


    @Nested
    @MicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PositiveTest {
        private DatagramSocket udpServer;

        @BeforeAll
        void setupUdpServer() throws InterruptedException {
            udpServer = TEST1_SOCKET; // Bind a random UDP port

            System.out.println("Starting UDP server on port " + udpServer.getLocalPort());

            new Thread(() -> {
                byte[] buffer = new byte[10];
                while (!udpServer.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpServer.receive(packet);

                        DatagramPacket response = new DatagramPacket(
                                "pong".getBytes(), 4, packet.getAddress(), packet.getPort()
                        );
                        udpServer.send(response);
                    } catch (IOException ignored) {
                    }
                }
            }).start();

            Thread.sleep(500); // Ensure UDP server is ready
        }

        @AfterAll
        void tearDown() {
            if (udpServer != null && !udpServer.isClosed()) {
                System.out.println("Stopping UDP server");
                udpServer.close();
            }
        }

        @Inject
        EmbeddedApplication<?> application;

        @Inject
        @Client("/")
        HttpClient client;

        @Test
        @Property(name = "udp.monitor.port", value = "${TEST1_SOCKET_PORT}")
            // Inject dynamic port
        void testHealthEndpoint() throws InterruptedException {
            Thread.sleep(1000);
            HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.HEAD("/health"), String.class);
            assertEquals(200, response.getStatus().getCode());
        }
    }

    @Nested
    @MicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NegativeTest {
        private DatagramSocket udpServer;

        @BeforeAll
        void setupUdpServer() {
            udpServer = TEST2_SOCKET;
            System.out.println("Starting UDP server for negative test on port " + udpServer.getLocalPort());
        }

        @AfterAll
        void tearDown() {
            if (udpServer != null && !udpServer.isClosed()) {
                System.out.println("Stopping UDP server for negative test");
                udpServer.close();
            }
        }

        @Inject
        EmbeddedApplication<?> application;

        @Inject
        @Client("/")
        HttpClient client;

        @Test
        @Property(name = "udp.monitor.port", value = "${TEST2_SOCKET_PORT}")
            // Inject dynamic port
        void testHealthEndpointWhenUdpServerIsDown() throws InterruptedException {
            // ✅ This test stops its own UDP instance, not affecting other tests
            if (udpServer != null && !udpServer.isClosed()) {
                System.out.println("Stopping UDP server for negative test...");
                udpServer.close();
            }

            Thread.sleep(1000); // Allow time for Micronaut to detect failure

            try {
                client.toBlocking().exchange(HttpRequest.HEAD("/health"), String.class);
                fail("Expected to fail");
            } catch (HttpClientResponseException e) {
                assertEquals(HttpStatus.NOT_FOUND.getCode(), e.getStatus().getCode());
            }
        }
    }

    @Nested
    @MicronautTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NegativeTestTimeout {
        private DatagramSocket udpServer;

        @BeforeAll
        void setupUdpServer() throws IOException {
            udpServer = TEST3_SOCKET;
            System.out.println("Starting UDP server for negative test on port " + udpServer.getLocalPort());
        }

        @AfterAll
        void tearDown() {
            if (udpServer != null && !udpServer.isClosed()) {
                System.out.println("Stopping UDP server for negative test");
                udpServer.close();
            }
        }

        @Inject
        EmbeddedApplication<?> application;

        @Inject
        @Client("/")
        HttpClient client;

        @Test
        @Property(name = "udp.monitor.port", value = "${TEST3_SOCKET_PORT}")
        @Property(name = "udp.monitor.caller.timeout", value = "2")
        @Property(name = "micronaut.http.client.read-timeout", value = "2s")
        void testHealthEndpointWhenUdpServerIsDownCallerTimeout() throws InterruptedException {
            // ✅ This test stops its own UDP instance, not affecting other tests
            if (udpServer != null && !udpServer.isClosed()) {
                System.out.println("Stopping UDP server for negative test...");
                udpServer.close();
            }

            Thread.sleep(1000); // Allow time for Micronaut to detect failure
            assertThrows(ReadTimeoutException.class, () -> client.toBlocking().exchange(HttpRequest.HEAD("/health"), String.class));
        }
    }
}