/**
 * HAProxy PROXY protocol support for the Numdrassl proxy.
 *
 * <p>This package provides support for the HAProxy PROXY protocol (versions 1 and 2),
 * which allows DDoS protection services and load balancers to preserve the original
 * client IP address when proxying connections to the Numdrassl proxy.</p>
 *
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link me.internalizable.numdrassl.pipeline.proxy.ProxyProtocolHandler} - Decodes PROXY protocol headers</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Enable PROXY protocol in the proxy configuration:</p>
 * <pre>
 * proxyProtocol:
 *   enabled: true
 *   required: true
 *   trustedProxies:
 *     - "192.168.1.1"
 *     - "10.0.0.0/8"
 * </pre>
 *
 * <h2>Security Considerations</h2>
 * <p><b>WARNING:</b> Only enable PROXY protocol if you're behind a DDoS protection service
 * that sends PROXY protocol headers. If enabled without proper trusted proxy configuration,
 * malicious clients could spoof their IP addresses.</p>
 *
 * @see <a href="https://www.haproxy.org/download/2.0/doc/proxy-protocol.txt">PROXY Protocol Specification</a>
 */
package me.internalizable.numdrassl.pipeline.proxy;

