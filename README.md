# QlikView Desktop MCP Server
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Platform: Windows](https://img.shields.io/badge/platform-Windows-0078D6.svg)](#requirements)

A read-only [MCP](https://modelcontextprotocol.io) server that lets AI assistants (Claude Code,
Claude Desktop, GitHub Copilot, Cursor, or any other MCP client) inspect a running QlikView Desktop
instance - its load script, variables, data model, sheets, charts, and data sources - and evaluate
expressions against its actual current data and selections.

## Why

An AI assistant can explore a QlikView document's structure and live data through one consistent,
typed tool interface - the same way it already works with a codebase - instead of needing someone
to export, screenshot, or manually explain what's inside.

It can also answer what no file on disk ever could: `evaluate` runs a QlikView expression against
the document's actual currently loaded data and selections - "what does `Sum(SalesAmount)` return
right now, given what's selected?" - by talking directly to a running QlikView Desktop instance via
COM automation. Every other tool talks to the same live instance, so results always reflect the
document's real current state, including anything not yet saved.

## Tools

Every tool talks to a running QlikView Desktop instance via COM automation - there is no
file-based/offline mode. Requires QlikView Desktop installed, licensed, and running, with the
document open or reachable, at the moment a tool is called.

| Tool | Returns |
|---|---|
| `list_documents` | File-system scan for `.qvw`/`.qvf` under the allowlisted roots - the only tool that doesn't need QlikView running |
| `get_script` | The load script, including tab markers. Connection-string credentials are redacted |
| `get_variables` | Current in-memory variable values, including runtime changes |
| `get_data_model` | Real table names, cardinality, and table membership per field |
| `get_sheets` | Sheets and the objects placed on each |
| `get_object` | One object's type and, for charts, its dimensions/expressions |
| `get_data_sources` | Connection statements, `FROM`-clause file references, includes, and the `BINARY` statement's source document, parsed from the load script. Connection-string credentials are redacted |
| `evaluate` | Evaluates an expression against current data/selections - the only way to get this |

## Limitations

- **Read-only.** No writing to documents, no reload, no macro execution - unreachable from tool
  code by design, not just unused.
- **Reaches one machine only.** COM automation only talks to a QlikView Desktop instance in the
  same interactive Windows session as the server process. There's no shared instance for a team -
  each person runs their own server next to their own QlikView license.
- **Windows-only.** COM automation only exists on Windows. Compiling and starting the server
  don't require Windows, but no tool call can succeed without it.
- **Secret redaction is pattern-based, not guaranteed.** `get_script` and `get_data_sources` mask
  credential-shaped `key=value` pairs (`PWD=`, `UID=`, `Password=`, ...); `get_variables` flags
  (doesn't withhold) variables whose name suggests a secret. A credential embedded some other way
  won't be caught - treat output as likely safe, not guaranteed safe.
- **QlikView Desktop only** - no Qlik Sense support (different product, different API).
- **Section Access is unverified.** Whether a Section-Access-protected document returns
  silently-reduced data, blocks, or hits the call timeout hasn't been tested.

## Requirements

- Windows
- QlikView Desktop, installed, licensed, and running, with the document open or reachable
- Java 21 - skip this if using `run-server.bat` from a [release](../../releases/latest), which
  downloads a private copy automatically if none is found
- Maven - only if building from source

## Usage

1. **Set `qlikview.roots`** to the folder(s) containing your documents (see
   [Configuration](#configuration)). This is the allowlist of folders a document path is allowed
   to resolve under - it stops an MCP client from pointing a tool at an arbitrary file on your
   machine, not just a config nicety. Every tool call is rejected until this is set.
2. **Register the server with your MCP client** (see [Building and running](#building-and-running))
   and call a tool with an absolute document path:

   ```json
   { "name": "get_variables", "arguments": { "document": "C:\\Data\\Sales.qvw" } }
   ```

3. **Use `evaluate`** for anything that depends on the document's actual current state:

   ```json
   { "name": "evaluate", "arguments": { "document": "C:\\Data\\Sales.qvw", "expression": "=Sum(SalesAmount)" } }
   ```

## Building and running

### Option 1: download a release (no clone, no Maven, Java optional)

Download and extract the zip from the [latest release](../../releases/latest), then run
`run-server.bat` instead of `java -jar ...`. It finds a Java 21+ runtime on its own - a previously
downloaded copy in `.\jre`, then `JAVA_HOME`, then `java` on `PATH` - and downloads a private JRE
into `.\jre` if none qualifies (via `curl.exe`/`tar.exe`, both built into Windows since 10 1803).
Nothing is installed system-wide; deleting the folder removes everything.

```
set QLIKVIEW_MCP_ROOTS=C:\path\to\your\qlikview\documents
run-server.bat
```

Register it with your MCP client pointing at `run-server.bat` - for Claude Code:
`claude mcp add qlikview -- <path-to>\run-server.bat`.

### Option 2: build from source

Requires Java 21 and Maven.

```
mvn package
set QLIKVIEW_MCP_ROOTS=C:\path\to\your\qlikview\documents
java -jar target\qlikview-mcp-server.jar
```

(`set` is `cmd.exe` syntax; in PowerShell use `$env:QLIKVIEW_MCP_ROOTS = "C:\path\to\..."`.)

Register it with your MCP client the way that client expects a local STDIO server - for Claude
Code: `claude mcp add qlikview -- java -jar <path-to-jar>`. Clients configured via a settings file
(Claude Desktop, VS Code Copilot, and others) need the same command and the `QLIKVIEW_MCP_ROOTS`
environment variable added to that client's own MCP server configuration.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `qlikview.roots` | *(empty)* | Security allowlist: comma-separated folders a document path must resolve under before any tool call reaches QlikView. Required - if empty, every tool call is rejected. Set via `QLIKVIEW_MCP_ROOTS` |
| `qlikview.output.max-chars` | `200000` | Caps free-text response size (`evaluate`, `get_script`) before truncating |
| `qlikview.output.max-items` | `500` | Caps how many entries any list a tool returns may contain before truncating |
| `qlikview.call.timeout` | `15s` | Maximum time a single live (COM) call may run before it is abandoned as stuck |

`qlikview.roots` defaults to empty and rejects everything until set, rather than defaulting to
"anywhere" - an AI assistant driving this server should only ever be able to reach the documents
you explicitly opted in, never an arbitrary path it decides to try. It has a dedicated environment
variable for exactly this reason: an empty-by-default, security-relevant setting needs to be easy
to set correctly, not easy to skip. All properties can also be set the usual Spring Boot ways: a
JVM system property
(`-Dqlikview.call.timeout=30s`), an environment variable via relaxed binding
(`QLIKVIEW_CALL_TIMEOUT=30s`), or your own `application.properties` override.

## License

[MIT](LICENSE)
