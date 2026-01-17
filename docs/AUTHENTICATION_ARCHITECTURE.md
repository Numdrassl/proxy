# Numdrassl Authentication Architecture

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│    ┌──────────┐                   ┌──────────┐                ┌──────────┐ │
│    │          │  Full Hytale Auth │          │ HMAC Secret    │          │ │
│    │  Client  │◄─────────────────►│  Proxy   │◄──────────────►│ Backend  │ │
│    │          │  (TLS + JWT)      │          │ (No Hytale)    │          │ │
│    └──────────┘                   └──────────┘                └──────────┘ │
│         │                              │                                    │
│         │                              │                                    │
│         └──────────────────────────────┼────────────────────────────────────┤
│                                        │                                    │
│                                        ▼                                    │
│                               ┌────────────────┐                            │
│                               │   sessions.    │                            │
│                               │  hytale.com    │                            │
│                               └────────────────┘                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Client ↔ Proxy Authentication Flow

The proxy acts as a legitimate Hytale server and performs full authentication:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  Client                         Proxy                      Session Service  │
│    │                              │                              │          │
│    │ 1. Connect packet            │                              │          │
│    │  (identity_token, uuid,      │                              │          │
│    │   username)                  │                              │          │
│    │─────────────────────────────►│                              │          │
│    │                              │                              │          │
│    │                              │ 2. POST /server-join/auth-grant         │
│    │                              │  (validate client, get grant)│          │
│    │                              │─────────────────────────────►│          │
│    │                              │                              │          │
│    │                              │◄─────────────────────────────│          │
│    │                              │   authorization_grant,       │          │
│    │                              │   (proxy's identity_token)   │          │
│    │                              │                              │          │
│    │ 3. AuthGrant packet          │                              │          │
│    │  (authorization_grant,       │                              │          │
│    │   server_identity_token)     │                              │          │
│    │◄─────────────────────────────│                              │          │
│    │                              │                              │          │
│    │ 4. AuthToken packet          │                              │          │
│    │  (access_token,              │                              │          │
│    │   server_authorization_grant)│                              │          │
│    │─────────────────────────────►│                              │          │
│    │                              │                              │          │
│    │                              │ 5. POST /server-join/auth-token         │
│    │                              │  (exchange server_auth_grant)│          │
│    │                              │─────────────────────────────►│          │
│    │                              │                              │          │
│    │                              │◄─────────────────────────────│          │
│    │                              │   server_access_token        │          │
│    │                              │                              │          │
│    │ 6. ServerAuthToken packet    │                              │          │
│    │  (server_access_token)       │                              │          │
│    │◄─────────────────────────────│                              │          │
│    │                              │                              │          │
│    │         ✓ CLIENT AUTHENTICATED TO PROXY                     │          │
│    │                              │                              │          │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Proxy ↔ Backend Authentication Flow

The backend trusts the proxy via HMAC-signed referral data (no Hytale auth):

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  Proxy                          Backend                                      │
│    │                              │                                          │
│    │ 1. Connect packet            │                                          │
│    │  (original client info +     │                                          │
│    │   HMAC-signed referralData)  │                                          │
│    │─────────────────────────────►│                                          │
│    │                              │                                          │
│    │                              │ 2. Validate HMAC signature               │
│    │                              │    using shared secret                   │
│    │                              │                                          │
│    │                              │ 3. Extract player info:                  │
│    │                              │    - UUID                                │
│    │                              │    - Username                            │
│    │                              │    - Backend name                        │
│    │                              │    - Client IP                           │
│    │                              │    - Timestamp                           │
│    │                              │                                          │
│    │ 4. ConnectAccept packet      │                                          │
│    │  (no auth required)          │                                          │
│    │◄─────────────────────────────│                                          │
│    │                              │                                          │
│    │         ✓ PROXY AUTHENTICATED TO BACKEND                                │
│    │                              │                                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

## HMAC Referral Data Format

The proxy signs player information with HMAC-SHA256:

```
┌───────────────────────────────────────────────────────┐
│ Offset │ Size   │ Field                               │
├────────┼────────┼─────────────────────────────────────┤
│ 0      │ 4      │ Protocol version (int LE)           │
│ 4      │ 8      │ UUID MSB (long LE)                  │
│ 12     │ 8      │ UUID LSB (long LE)                  │
│ 20     │ 4      │ Username length (int LE)            │
│ 24     │ N      │ Username (UTF-8)                    │
│ 24+N   │ 4      │ Backend name length (int LE)        │
│ ...    │ M      │ Backend name (UTF-8)                │
│ ...    │ 4      │ Remote address length (int LE)      │
│ ...    │ K      │ Remote address (UTF-8)              │
│ ...    │ 4      │ Timestamp (unix seconds, int LE)    │
│ ...    │ 32     │ HMAC-SHA256 signature               │
└───────────────────────────────────────────────────────┘
```

## Configuration

### Proxy (config/proxy.yml)

```yaml
proxySecret: "your-shared-secret-here"  # Must match backend
```

### Backend (Bridge plugin config)

```yaml
proxySecret: "your-shared-secret-here"  # Must match proxy
backendName: "lobby"                     # Validated in HMAC
```

## Key Points

1. **Client sees the proxy as a real Hytale server** - Full authentication with session service
2. **Backend trusts the proxy via shared secret** - No Hytale authentication needed
3. **Proxy can inspect/modify all traffic** - Decrypted at proxy level
4. **Backend runs in insecure mode** - Relies on proxy for player verification
5. **HMAC prevents replay attacks** - Timestamp validation (5 minute window)

