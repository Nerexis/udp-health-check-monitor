package ner;

import io.github.resilience4j.micronaut.annotation.RateLimiter;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller("/health")
public class HealthController {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);

    private final String udpHost;
    private final int udpPort;
    private final String udpPayload;
    private final int timeoutMs;
    private final int callerTimeoutMs;

    public HealthController(@Value("${udp.monitor.host:localhost}") String udpHost,
                            @Value("${udp.monitor.port}") int udpPort,
                            @Value("${udp.monitor.payload:ping}") String udpPayload,
                            @Value("${udp.monitor.timeout:5000}") int timeoutMs,
                            @Value("${udp.monitor.caller.timeout:0}") int callerTimeoutSec) {
        this.udpHost = udpHost;
        this.udpPort = udpPort;
        this.udpPayload = udpPayload;
        this.timeoutMs = timeoutMs;
        this.callerTimeoutMs = callerTimeoutSec * 1000;
    }

    @PostConstruct
    void init() {
        LOG.info("UDP host: {}", udpHost);
        LOG.info("UDP port: {}", udpPort);
        LOG.info("UDP timeout: {}", timeoutMs);
        LOG.info("Service caller timeout: {}", callerTimeoutMs);
    }

    @Get("/get")
    @RateLimiter(name = "default")
    public HttpResponse<String> healthViaGet() throws InterruptedException {
        return checkHealth();
    }

    @Head("/head")
    @RateLimiter(name = "default")
    public HttpResponse<String> healthViaHead() throws InterruptedException {
        return checkHealth();
    }

    private MutableHttpResponse<String> checkHealth() throws InterruptedException {
        LOG.info("received check health request");
        boolean isUp = sendUDPCheck();
        if (!isUp) {
            LOG.warn("UDP Host {}, UDP Port {} is DOWN!", udpHost, udpPort);
            if(callerTimeoutMs <= 0) {
                return HttpResponse.notFound("DOWN");
            }
            LOG.debug("thread sleep due to caller timeout: {}", callerTimeoutMs);
            Thread.sleep(callerTimeoutMs);
            return HttpResponse.badRequest();
        }
        return HttpResponse.ok("UP");
    }

    private boolean sendUDPCheck() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.connect(InetAddress.getByName(udpHost), udpPort);

            // Convert payload from properties into actual bytes
            byte[] buffer = parseHexAndTextPayload(udpPayload);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(udpHost), udpPort);
            LOG.info("Sending UDP packet to {}:{} with payload (hex): {}, timeout: {}ms",
                    udpHost, udpPort, bytesToHex(buffer), timeoutMs);
            socket.send(packet);

            // Attempt to receive a response
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.receive(response);

            LOG.info("SUCCESS: Received UDP response from {}:{} - Size: {} bytes",
                    udpHost, udpPort, response.getLength());
            return true; // Port is open and responding
        } catch (SocketTimeoutException e) {
            LOG.warn("UDP port {}:{} is open but no response received within timeout", udpHost, udpPort);
            return false; // Port is open but unresponsive
        } catch (IOException e) {
            LOG.warn("UDP port {}:{} is closed or unreachable: {}", udpHost, udpPort, e.getMessage());
            return false; // Port is closed/unreachable
        }
    }

    /**
     * Parses a payload string that contains both raw text and hex-encoded bytes.
     * Example: "\xFF\xFF\xFF\xFFTSource Engine Query\x00"
     * -> Converts to: [0xFF, 0xFF, 0xFF, 0xFF, 'T', 'S', 'o', 'u', 'r', 'c', 'e', ' ', 'E', 'n', 'g', 'i', 'n', 'e', ' ', 'Q', 'u', 'e', 'r', 'y', 0x00]
     */
    private static byte[] parseHexAndTextPayload(String payload) {
        Pattern hexPattern = Pattern.compile("\\\\x([0-9A-Fa-f]{2})");
        Matcher matcher = hexPattern.matcher(payload);
        byte[] result = new byte[payload.length()]; // Max possible size
        int index = 0;
        int lastEnd = 0;

        while (matcher.find()) {
            // Add previous text part (before \x sequence)
            String textPart = payload.substring(lastEnd, matcher.start());
            byte[] textBytes = textPart.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(textBytes, 0, result, index, textBytes.length);
            index += textBytes.length;

            // Convert hex sequence
            result[index++] = (byte) Integer.parseInt(matcher.group(1), 16);
            lastEnd = matcher.end();
        }

        // Add remaining text (after last \x sequence)
        byte[] textBytes = payload.substring(lastEnd).getBytes(StandardCharsets.UTF_8);
        System.arraycopy(textBytes, 0, result, index, textBytes.length);
        index += textBytes.length;

        return java.util.Arrays.copyOf(result, index); // Trim to actual length
    }

    /**
     * Converts a byte array to a human-readable hex string for debugging.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X ", b));
        }
        return hexString.toString().trim();
    }
}