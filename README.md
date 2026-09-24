# QlikView Desktop MCP Server
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F.svg)](https://spring.io/projects/spring-boot)

A read-only [MCP](https://modelcontextprotocol.io) server that lets AI assistants (Claude Code,
Claude Desktop, GitHub Copilot, Cursor, or any other MCP client) inspect a QlikView Desktop
document - its load script, variables, data model, sheets, charts, and data sources - and, when
QlikView is open, evaluate expressions against its actual current data and selections.

## Why

A QlikView document's real structure lives inside a verbose, deeply nested XML export that mixes
real content with internal bookkeeping (a single chart's dimension definition can run 2000+ lines
of XML to extract one field name from). Handing that to an AI assistant raw wastes tokens and
invites wrong answers. This server parses it once and returns a clean, structured response.

It can also answer what no file on disk ever can: `evaluate` runs a QlikView expression against
the document's actual currently loaded data and selections - "what does `Sum(SalesAmount)` return
right now, given what's selected?" - by talking directly to a running QlikView Desktop instance.
The same live connection powers an optional, more accurate mode (`live=true`) on several other
tools too, returning real cardinality, table relationships, and current in-memory state instead of
what was last saved to disk.

## Static vs. live

Every tool reads from a document's `-prj` export folder on disk by default (**static** - no
QlikView needed, shareable, reflects the document as last saved). Some tools also accept
`live=true` to instead talk to a running QlikView Desktop instance via COM automation (**live** -
requires QlikView installed, licensed, and running with the document open; reflects the document's
actual current state, including unsaved changes).

| Tool | Static source | Live source (`live=true`) |
|---|---|---|
| `list_documents` | File-system scan for `.qvw`/`.qvf` | - |
| `get_script` | `-prj` export's `LoadScript.txt`, credentials redacted | - |
| `get_variables` | `SET`/`LET` parsed from the script | Current in-memory values, including runtime changes |
| `get_data_model` | Table names and field tags from the `-prj` export - no cardinality or table-to-field grouping | Real cardinality and table membership |
| `get_sheets` | Sheets and object ids from the `-prj` export's project index | Sheets and objects from the live document |
| `get_object` | One object's type, dimensions, and expressions, from its `-prj` XML file | Same, read live via COM |
| `get_data_sources` | Connections, file references, includes, and `BINARY` source, parsed from the script, credentials redacted | - |
| `evaluate` | - | The only way to get this: evaluates an expression against current data/selections |

Two things worth knowing about the static source:
- **`get_variables`** reflects the script's *declared* values: a `LET` that depends on a function
  or another variable comes back as literal, unevaluated script text, not its resolved value.
- **`get_sheets`/`get_object`** use a different vocabulary than the live source for sheet identity
  and object type (internal id vs. displayed caption; XML element name vs. numeric code). Object
  ids from either source work as input to `get_object`.

The `-prj` export folder is required for every static call (see [Usage](#usage), step 1) - QlikView
never creates it on its own, only fills it in on save once it exists.

## Limitations

- **Read-only.** No writing to documents, no reload, no macro execution - unreachable
  from tool code by design, not just unused.
- **Live tools reach one machine only.** COM automation only talks to a QlikView Desktop instance
  in the same interactive Windows session as the server process. There's no shared instance for a
  team - each person runs their own server next to their own QlikView license.
- **Only the live path is Windows-only.** Compiling, starting the server, and calling any
  static-mode tool need nothing but Java 21 and the files on disk, on any OS. `powershell.exe` is
  only ever launched from inside a live call. The packaged jar isn't split by platform, though, so
  building and testing end to end still needs Windows.
- **Secret redaction is pattern-based, not guaranteed.** `get_script` and `get_data_sources` mask
  credential-shaped `key=value` pairs (`PWD=`, `UID=`, `Password=`, ...); `get_variables` flags
  (doesn't withhold) variables whose name suggests a secret. A credential embedded some other way
  won't be caught - treat output as likely safe, not guaranteed safe.
- **QlikView Desktop only** - no Qlik Sense support (different product, different API).
- **Section Access is unverified.** Whether a Section-Access-protected document returns
  silently-reduced data, blocks, or hits the call timeout via a live call hasn't been tested.

## Requirements

- Windows, with `powershell.exe` on PATH, for the live path and for the test suite
- Java 21 - skip this if using `run-server.bat` from a [release](../../releases/latest), which
  downloads a private copy automatically if none is found
- Maven - only if building from source
- QlikView Desktop, installed and licensed - only for live tool calls

## Usage

1. **(Optional) Enable the `-prj` export** for any document you want to query without `live=true`:
   create an empty folder named `<documentName>-prj` next to the `.qvw`/`.qvf` file, then open and
   save the document once in QlikView Desktop.
2. **Set `qlikview.roots`** to the folder(s) containing your documents (see
   [Configuration](#configuration)) - every tool call is rejected until this is set.
3. **Register the server with your MCP client** (see [Building and running](#building-and-running))
   and call a tool with an absolute document path:

   ```json
   { "name": "get_script", "arguments": { "document": "C:\\Data\\Sales.qvw" } }
   ```

4. **Add `"live": true`** to read from a running QlikView Desktop instead of the `-prj` export:

   ```json
   { "name": "get_data_model", "arguments": { "document": "C:\\Data\\Sales.qvw", "live": true } }
   ```

5. **Use `evaluate`** for the document's actual current state - it has no static mode:

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
| `qlikview.roots` | *(empty)* | Comma-separated list of folders a document path must resolve under. Required - if empty, every tool call is rejected. Set via `QLIKVIEW_MCP_ROOTS` |
| `qlikview.output.max-chars` | `200000` | Caps free-text response size (`evaluate`, `get_script`) before truncating |
| `qlikview.output.max-items` | `500` | Caps how many entries any list a tool returns may contain before truncating |
| `qlikview.call.timeout` | `15s` | Maximum time a single live (COM) call may run before the worker process is force-killed |

`qlikview.roots` has a dedicated environment variable because its empty default makes the server
unusable. All properties can also be set the usual Spring Boot ways: a JVM system property
(`-Dqlikview.call.timeout=30s`), an environment variable via relaxed binding
(`QLIKVIEW_CALL_TIMEOUT=30s`), or your own `application.properties` override.

## License

[MIT](LICENSE)
