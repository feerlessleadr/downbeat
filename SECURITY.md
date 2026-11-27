# Security policy

## Reporting a vulnerability

Please report security problems privately through GitHub: the **Security** tab of this repository, then **Report a vulnerability**. Do not open a public issue for a security problem.

## What the app handles

- Your Music Assistant server address and a sign-in token. The token is stored encrypted with the Android Keystore and is sent only to your own server.
- The password is used once to sign in and is never stored.
- The app talks to your server over HTTPS only (cleartext traffic is disabled).

## Supported versions

Only the latest release receives fixes.
