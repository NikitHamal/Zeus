# Zeus

Zeus is a phone-first GitHub and NEBians Background Agent workbench built with Kotlin, Jetpack Compose and Material 3. It combines durable AI coding tasks, GitHub repository management, a local code workspace, a lightweight file editor, JGit-powered version control and an embedded terminal-like command surface.

Package: `com.zeus.code`  
OAuth callback registered in Android: `zeus://oauth`

## Included features


### NEBians Background Agent

- One-time device authorization through the NEBians Background Agent
- Encrypted persistent device token backed by Android Keystore
- Qwen 3.7 Plus task creation with file and image attachments
- Live task status, progress, messages, changed files and patch review
- Pause, resume, stop, archive, restore and permanent delete
- Generate and download changed-files ZIP and patch artifacts
- Push agent branches and open pull requests
- Clone the agent branch directly into a Zeus workspace for local pull, edit, commit and push

### GitHub

- GitHub OAuth Device Flow login
- Encrypted access-token storage using Android Keystore
- List and search repositories
- Create private or public repositories
- Delete repositories
- Fork and clone repositories
- List branches, issues and pull requests
- Create issues and pull requests
- Submit pull-request comments, approvals or change requests
- Merge pull requests

### Local mobile coding

- Import a folder through Android Storage Access Framework
- Clone HTTPS repositories into Zeus private app storage
- Create local projects and initialize Git
- Recursive project file browser
- Create, edit, save and delete text files
- Export a workspace back to a user-selected folder
- Workspace history and Git status views

### Git

Zeus uses Eclipse JGit, so it does not depend on a `git` binary:

- status, add and commit
- push and force push
- pull and fetch
- create, switch and delete branches
- merge branches
- hard reset
- revert commits
- stash and apply stash
- recent commit log
- set the `origin` remote

### Terminal

Commands starting with `git` are mapped to JGit. Other commands are executed with Android's `/system/bin/sh` in the selected workspace. Android does not include a full GNU/Linux userland, so available non-Git commands vary by device and remain inside the application sandbox.

## Required GitHub configuration

Your existing OAuth App must have **Device Flow enabled**:

1. GitHub → Settings → Developer settings → OAuth Apps.
2. Open the OAuth App used by Zeus.
3. Enable **Device Flow**.
4. The callback URL may remain `zeus://oauth`; Device Flow itself does not use the callback or client secret.

## GitHub Actions secrets

Add this repository secret:

| Secret | Value |
|---|---|
| `OAUTH_CLIENT_ID` | The OAuth App's Client ID, for example `Ov23li...` |

`OAUTH_CLIENT_SECRET` is **not used** by the Android app or workflow. Keep your existing secret private; do not place it in source code or an APK.

## Automatic signed releases

`.github/workflows/release.yml` runs on pushes to `main` or `master` and on manual dispatch. It:

1. Builds `assembleRelease` on GitHub-hosted runners.
2. Signs with `keystore/zeus-release.jks`.
3. Renames the APK to `Zeus-<8-character-commit>.apk`.
4. Uploads the APK and SHA-256 checksum as an Actions artifact.
5. Creates a GitHub Release containing the APK.

The included public signing configuration is:

- file: `keystore/zeus-release.jks`
- alias: `zeus`
- store password: `zeus-release`
- key password: `zeus-release`
- certificate SHA-256: `A0:0E:31:60:C0:76:A8:B1:95:14:A0:4A:A2:F1:40:62:3E:E7:6F:3E:6D:F0:2B:F2:50:4F:CC:0F:B4:65:0D:62`

> This key is intentionally public and therefore cannot prove that an APK came from you. Anyone can produce an APK with the same signature. Replace it before distributing Zeus beyond personal testing, and never lose the replacement key after users install a release signed by it.

## Local build

Use Android Studio or a machine with Android SDK 35, JDK 17 and Gradle 8.9:

```bash
gradle :app:assembleDebug -POAUTH_CLIENT_ID=YOUR_CLIENT_ID -PBACKGROUND_AGENT_BASE_URL=https://nebians.consica.com.np
gradle :app:assembleRelease -POAUTH_CLIENT_ID=YOUR_CLIENT_ID -PBACKGROUND_AGENT_BASE_URL=https://nebians.consica.com.np
```

## Important behavior

- Imported folders are copied into Zeus private storage rather than live-mounted. Use **Export** to copy edits back to a selected external folder.
- The editor intentionally refuses files larger than 2 MB.
- Export excludes `.git` to avoid leaking repository internals into arbitrary shared-storage destinations.
- GitHub's API is much larger than any single mobile client. Zeus implements the core repository, issue, pull-request and local Git workflows needed for phone coding; GitHub Projects, Actions log streaming, releases editing, organizations administration, discussions and every enterprise-only API are not included in this initial codebase.

## Architecture

- `BackgroundAgentApi`: authenticated NEBians mobile API through OkHttp + kotlinx.serialization
- `BackgroundAgentViewModel`: persistent device authorization, task lifecycle and live refresh
- `GitHubApi`: OAuth and GitHub REST calls through OkHttp + kotlinx.serialization
- `SecureTokenStore`: AES-GCM token encryption backed by Android Keystore
- `GitService`: JGit operations
- `WorkspaceManager`: private workspace and Storage Access Framework import/export
- `TerminalEngine`: JGit command mapping plus `/system/bin/sh`
- `MainViewModel`: coroutine-based application state and orchestration
- `ZeusApp`: Compose Material 3 UI with downloadable Poppins font and system fallback
