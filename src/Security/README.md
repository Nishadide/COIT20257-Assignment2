# Security package — Role C

Everything cryptographic. Nothing here depends on the network code, so this
package can be built and tested on its own before integration.

## What goes in here

| Class | Purpose |
|---|---|
| `KeyGenerator` | A one-off `main` that generates both RSA key pairs and writes the four `.ser` files |
| `CryptoUtil` | RSA encrypt/decrypt, AES encrypt/decrypt, key loading, Base64 helpers |
| `Authenticator` | Builds and verifies the two `CSAuthenticator` messages for each side |

## Rules to follow

- Use the algorithm names and key sizes from `Contract.Protocol`. Do not
  write cipher transformation strings by hand anywhere else.
- Every encrypted value is **Base64-encoded** before it goes into a
  `CSAuthenticator` field, and Base64-decoded before it is decrypted.
- **RSA holds 245 bytes at most** (2048-bit, PKCS#1). Use it only for the
  128-character verification string and the 16-byte AES session key. Whole
  `SensorFactor` objects are encrypted with AES.
- Serialize an object to bytes first, then encrypt the bytes. Do not try to
  encrypt the object directly.

## Deliverable before integration

A `main` method that proves the round trip on its own: generate keys,
encrypt a string, decrypt it, assert it matches. Do not wire into the
edge or device layers until that passes.
