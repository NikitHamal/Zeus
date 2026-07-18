# Security notes

- Zeus uses GitHub OAuth Device Flow. The OAuth client secret is not compiled into the Android app.
- GitHub and NEBians Background Agent credentials are stored separately and encrypted with AES-GCM keys generated and held by Android Keystore.
- Background Agent authorization is a one-time device pairing. The server stores only an HMAC-SHA-256 digest of the issued bearer token; the raw token is shown only to Zeus and persists until explicit disconnect or server-side revocation.
- Zeus clears saved credentials only after a confirmed HTTP 401. Temporary network, GitHub, or NEBians failures open in offline mode without forcing repeated login.
- Background Agent artifact downloads are accepted only from the configured NEBians HTTPS origin, preventing bearer tokens from being sent to an untrusted URL.
- Android cleartext traffic is disabled. Configure `BACKGROUND_AGENT_BASE_URL` with an HTTPS origin.
- The requested GitHub OAuth scopes are intentionally broad because Zeus can manage private repositories, workflows, notifications and repository deletion.
- JGit clones only canonical `https://github.com/owner/repository.git` URLs, uses the GitHub token as an HTTPS credential, limits clone scope to the requested branch, and removes incomplete destinations after a failed clone.
- The included release keystore is public and disposable. It provides reproducible zero-setup signing, not publisher identity or supply-chain trust.
- Force push, hard reset, repository deletion, task deletion and file deletion are destructive operations. Zeus requires explicit confirmation in the UI where applicable.
- Shell commands execute as the Zeus Android application user and cannot escape Android's sandbox without external tooling or a rooted device.
- Android Auto Backup excludes both encrypted credential preference files. The Android Keystore keys and ciphertext are not exported through backup.
