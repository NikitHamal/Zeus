# JGit 6.10.0 — Android (API 26+) compatibility patch

`../jgit-android-6.10.0.202406032230-patch1.jar` is the stock
`org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r` jar with the 11
classes in this directory re-compiled from the official sources jar, plus the
jar signature removed (patched entries break digests; Android/dex ignores jar
signatures anyway).

## Why

Stock JGit 6.10 calls Java 9+/11+ runtime APIs that **do not exist on Android
API 26–32** and are **not covered by core-library desugaring** (verified
against desugar_jdk_libs 2.1.4/2.1.5 machine specs). On such devices every
clone dies mid-way with, e.g.:

```
NoSuchMethodError: No virtual method readNBytes(I)[B in class Ljava/io/FileInputStream; or its super classes
```

This is the root cause of Zeus's persistent clone failures on older devices.

## Patched call sites (only changes vs. stock)

| File | Replaced |
|---|---|
| `util/IO.java` | `SilentFileInputStream.readNBytes(int)`, `InputStream.readAllBytes()`, `InputStream.readNBytes(byte[],int,int)` → plain read loops |
| `dircache/DirCacheCheckout.java` | 2× `InputStream.transferTo(OutputStream)` → copy loop |
| `lib/CommitConfig.java` | `String.stripTrailing()` → manual trim |
| `internal/storage/file/PackObjectSizeIndexLoader.java` | 2× `readNBytes(int)` → read loop |
| `internal/storage/file/PackObjectSizeIndexV1.java` | 4× `readNBytes(...)` → read loops |
| `internal/storage/commitgraph/CommitGraphWriter.java` | `Optional.isEmpty()` → `!isPresent()` |
| `internal/transport/ssh/OpenSshConfigFile.java` | 3× `String.strip()` → `StringUtils.stripCompat(...)` |
| `storage/file/UserConfigFile.java` | `String.strip()` → manual trim |
| `util/StringUtils.java` | `String.strip()` → new public `stripCompat(String)` helper |
| `util/io/ByteBufferInputStream.java` | `Objects.checkFromIndexSize(...)` → inline bounds check |
| `patch/PatchApplier.java` | `InputStream::nullInputStream` → empty `ByteArrayInputStream`; `transferTo(nullOutputStream())` → drain loop |

Everything else is byte-identical to the upstream 6.10.0 release.

## Reproduce

```bash
# 1. download org.eclipse.jgit-6.10.0.202406032230-r.jar and its -sources.jar from Maven Central
# 2. apply the edits visible in this directory (git diff vs the sources jar)
# 3. javac (JDK 11+, -encoding UTF-8) against the original jar + JavaEWAH 1.2.3 + slf4j-api
# 4. replace classes in a copy of the original jar, strip META-INF/*.SF/RSA signature + manifest digests
```

License: JGit is © Eclipse Foundation, distributed under the Eclipse
Distribution License 1.0 (BSD-3-Clause); the jar's `about.html` and
`OSGI-INF/` license texts are included unmodified. These modified sources
remain under the same license.
