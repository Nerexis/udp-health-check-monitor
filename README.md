# UDP Health Check Monitor

This application is a Micronaut-based health check monitor for UDP services. It sends a configurable UDP packet to a specified host and port, expecting a response within a defined timeout.

## Features
- Configurable target host and port
- Customizable UDP payload
- Adjustable timeouts for UDP requests and client calls
- Micronaut-based lightweight HTTP endpoint for health checks

## Usage

### **1. Start the Application**
Run the application with the following JVM parameters:

```sh
java -jar udp-health-check-monitor-0.1.jar \
    -Dudp.monitor.host=192.168.1.129 \
    -Dudp.monitor.port=2303 \
    -Dudp.monitor.payload="ping" \
    -Dudp.monitor.timeout=5000 \
    -Dudp.monitor.caller.timeout=10
```

### **2. Available Configuration Parameters**
The application supports several configurable parameters via system properties or environment variables.

| Property                          | Description                                 | Default Value |
|------------------------------------|---------------------------------------------|--------------|
| `udp.monitor.host`                | Target UDP host IP                          | `localhost`  |
| `udp.monitor.port`                | Target UDP port                             | `2303`       |
| `udp.monitor.payload`             | UDP payload to send                         | `"ping"`     |
| `udp.monitor.timeout`             | UDP response timeout (ms)                   | `5000`       |
| `udp.monitor.caller.timeout`      | Client request timeout (s)                  | `0` (disabled) |

### **3. Steam Query Example**
For Arma 3 Steam Query, use the following payload:

```sh
-Dudp.monitor.payload="\xFF\xFF\xFF\xFFTSource Engine Query\x00"
```

### **4. Health Check API**
Once running, the service provides an HTTP endpoint to check the UDP server status.

#### **Check UDP Server Health**
```sh
curl -v --max-time 5 -X HEAD http://localhost:8080/health
```

#### **Expected Responses**
- **200 OK** → UDP port is open and responding.
- **404 Not Found** → UDP port is down.
- **400 Bad Request OR TIMEOUT** → UDP port is open but unresponsive, with a configured delay OR TIMEOUT if caller timeout is set >0 and caller has set his HttpClient timeout below that value

### **5. Debugging**
To troubleshoot UDP communication, use `nc` (netcat) in verbose mode:

```sh
nc -ulv 2303
```
This will open a UDP listener on port `2303`, allowing you to manually check if packets are received.

For further debugging, use **Wireshark** or **tcpdump** to inspect UDP packets.

---

### Author
Damian Winnicki (nerexis@gmail.com)\
Buy my a coffee: 
https://buymeacoffee.com/nerexis