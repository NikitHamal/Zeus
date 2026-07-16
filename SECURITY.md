# Security notes

- Zeus uses GitHub OAuth Device Flow. The OAuth client secret is not compiled into the Android app.
- The access token is encrypted with an AES-GCM key generated and held by Android Keystore.
- The requested OAuth scopes are intentionally broad because Zeus can manage private repositories, workflows, notifications and repository deletion.
- The included release keystore is public and disposable. It provides reproducible zero-setup signing, not publisher identity or supply-chain trust.
- Force push, hard reset, repository deletion and file deletion are destructive operations. Review the target before confirming.
- Shell commands execute as the Zeus Android application user and cannot escape Android's sandbox without external tooling or a rooted device.
