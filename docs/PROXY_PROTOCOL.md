# PROXY Protocol Support

Numdrassl supports the HAProxy PROXY protocol (versions 1 and 2), allowing DDoS protection services and load balancers to preserve the original client IP address when proxying connections.

## Overview

When Numdrassl is deployed behind a DDoS protection service (e.g., OVH Anti-DDoS, Cloudflare Spectrum, TCPShield), the incoming connection's IP address is that of the DDoS protection service, not the actual client. The PROXY protocol allows these services to communicate the real client IP to Numdrassl.

```
┌────────┐         ┌─────────────┐          ┌───────────┐
│ Client │ ──QUIC→ │ DDoS Shield │ ──QUIC→  │ Numdrassl │
│  IP:A  │         │   IP:B      │          │   Proxy   │
└────────┘         └─────────────┘          └───────────┘
                          │
                   Sends PROXY header
                   with real IP:A
```

## Configuration

Enable PROXY protocol in your `config.yml`:

```yaml
# ==================== Proxy Protocol (HAProxy) ====================

proxyProtocol:
  # Enable PROXY protocol support
  enabled: true

  # Whether PROXY protocol header is required (reject if missing)
  required: true

  # Timeout for receiving PROXY protocol header (seconds)
  headerTimeoutSeconds: 5

  # Trusted proxy IPs (empty = trust all, NOT recommended!)
  # Add the IPs of your DDoS protection service here
  trustedProxies:
    - "192.168.1.1"
    - "10.0.0.1"
    - "2001:db8::1"
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Enable/disable PROXY protocol support |
| `required` | `true` | If `true`, reject connections without PROXY headers |
| `headerTimeoutSeconds` | `5` | Timeout waiting for PROXY header |
| `trustedProxies` | `[]` | List of trusted proxy IPs (empty = trust all) |

## Security Considerations

> ⚠️ **WARNING**: Improperly configured PROXY protocol support can allow attackers to spoof their IP addresses!

### Best Practices

1. **Always specify `trustedProxies`**: Only accept PROXY headers from known DDoS protection IPs
2. **Use firewall rules**: Block direct connections to your proxy, only allow from DDoS protection
3. **Set `required: true`**: Ensure all connections provide PROXY headers
4. **Monitor logs**: Watch for untrusted PROXY protocol attempts

### Common DDoS Protection Service IPs

Contact your DDoS protection provider for their egress IP ranges. Examples:

- **OVH Anti-DDoS**: Check your OVH control panel for allowed IPs
- **TCPShield**: Refer to [TCPShield's IP list](https://tcpshield.com/docs/ips)
- **Cloudflare Spectrum**: Uses Cloudflare's IP ranges

## Protocol Details

### Version 1 (Human-readable)

```
PROXY TCP4 192.168.1.100 10.0.0.1 56324 25565\r\n
```

Format: `PROXY <protocol> <src_addr> <dst_addr> <src_port> <dst_port>\r\n`

Supported protocols: `TCP4`, `TCP6`, `UDP4`, `UDP6`, `UNKNOWN`

### Version 2 (Binary)

More efficient binary format with a 12-byte signature:
```
\x0D\x0A\x0D\x0A\x00\x0D\x0A\x51\x55\x49\x54\x0A
```

Followed by version/command byte, address family, and address data.

## How It Works with QUIC

For QUIC connections, the PROXY protocol header is sent as the **first data** on the bidirectional stream. The flow is:

1. DDoS protection terminates client's QUIC connection
2. DDoS protection establishes new QUIC connection to Numdrassl
3. On first stream, DDoS protection sends PROXY header before any Hytale packets
4. Numdrassl reads PROXY header, extracts real client IP
5. Normal Hytale protocol continues

## Accessing the Real Client IP

### In Plugin Code

```java
import me.internalizable.numdrassl.api.player.Player;

public void onConnect(Player player) {
    // This returns the real IP (from PROXY protocol or direct)
    InetSocketAddress clientAddr = player.getAddress();
    String ip = clientAddr.getAddress().getHostAddress();
    
    getLogger().info("Player {} connected from {}", player.getName(), ip);
}
```

### In Session Handling

```java
ProxySession session = ...;

// Get the effective client address
// Checks PROXY protocol first, falls back to direct connection
InetSocketAddress realAddress = session.getEffectiveClientAddress();
```

## Testing

### Testing with nginx

You can test PROXY protocol using nginx with stream module:

```nginx
stream {
    server {
        listen 25565;
        proxy_pass your_proxy:24322;
        proxy_protocol on;
    }
}
```

### Manual Testing

You can send a PROXY v1 header manually with netcat/socat:

```bash
(echo -ne "PROXY TCP4 192.168.1.100 10.0.0.1 56324 25565\r\n"; cat) | nc localhost 24322
```

## Troubleshooting

### "Invalid or missing PROXY protocol header"

- Ensure your DDoS protection is configured to send PROXY protocol
- Check if `required: true` but clients connecting directly
- Verify the PROXY header format is correct

### "PROXY protocol received from untrusted source"

- The connecting IP is not in `trustedProxies`
- Add the DDoS protection service's IP to the list
- Check for IP spoofing attempts

### Connection immediately closes

- PROXY header timeout exceeded (`headerTimeoutSeconds`)
- Invalid PROXY protocol format
- Check proxy server logs for details

## References

- [HAProxy PROXY Protocol Specification](https://www.haproxy.org/download/2.0/doc/proxy-protocol.txt)
- [Velocity Proxy Protocol Support](https://docs.papermc.io/velocity/configuration#proxy-protocol)
- [nginx PROXY Protocol](https://nginx.org/en/docs/stream/ngx_stream_proxy_module.html#proxy_protocol)

