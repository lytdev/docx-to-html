# JDK 8 → JDK 21 Upgrade Design

## Goal

Upgrade the project from JDK 8 to JDK 21, remove JDK 8 compatibility shim (`Jdk8Helpers`), and update documentation accordingly.

## Scope

- Bump Java compiler source/target from 1.8 to 21 in `pom.xml`
- Remove `Jdk8Helpers` class and replace all 12 call sites with standard JDK APIs
- Update `CLAUDE.md`, `AGENTS.md`, and `.idea/misc.xml` to reflect JDK 21
- Preserve all business logic — no refactoring to use streams/lambdas/records etc. unless separately requested
- `build-exe.bat` already uses JDK 21 — no change needed

## Changes

### 1. pom.xml

- `<maven.compiler.source>1.8</maven.compiler.source>` → `21`
- `<maven.compiler.target>1.8</maven.compiler.target>` → `21`
- `<source>1.8</source>` → `21` (maven-compiler-plugin)
- `<target>1.8</target>` → `21` (maven-compiler-plugin)

### 2. Delete Jdk8Helpers.java

Remove `src/main/java/cn/p4u/smart/util/Jdk8Helpers.java` entirely.

### 3. Replace call sites

| Old | New | Files |
|-----|-----|-------|
| `Jdk8Helpers.writeString(p, s)` | `Files.writeString(p, s)` | CliRunner, GuiRunner, CliConvertTest, RelsParserTest, StylesParserTest, ThemeParserTest |
| `Jdk8Helpers.readString(p)` | `Files.readString(p)` | DocxExtractorTest |
| `Jdk8Helpers.transferTo(in, out)` | `in.transferTo(out)` | WmfConverter |
| `Jdk8Helpers.nullOutputStream()` | `OutputStream.nullOutputStream()` | WmfConverter |

Also remove all `import cn.p4u.smart.util.Jdk8Helpers` lines and add `import java.io.OutputStream` to WmfConverter.

### 4. Documentation

- `CLAUDE.md` & `AGENTS.md`: Remove JDK 8 JAVA_HOME line, remove Jdk8Helpers paragraph, update "JDK 8 Compatibility" section to "JDK 21", update packaging section
- `.idea/misc.xml`: `JDK_1_8` → `JDK_21`, `1.8` → `21`

### 5. Verify

Run `mvn test` with JDK 21 to confirm all tests pass.
