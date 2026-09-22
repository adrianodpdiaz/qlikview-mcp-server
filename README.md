# QlikView Desktop MCP Bridge
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F.svg)](https://spring.io/projects/spring-boot)

A read-only [MCP](https://modelcontextprotocol.io) server that lets AI coding assistants
(Claude Code, Claude Desktop, GitHub Copilot in Agent mode, Cursor, or any other MCP client)
inspect a QlikView Desktop document - its load script, variables, data model, sheets, charts, and
data sources - and, when QlikView is open, evaluate expressions against the document's actual
current data and selections.

## The value

A QlikView document's real structure - its data model, sheets, charts, dimensions, and
expressions - lives inside a verbose, deeply nested XML export that mixes real content with
QlikView's internal bookkeeping (a single chart's dimension definition can run 2000+ lines of XML
to extract one field name from). Handing that to an AI assistant raw wastes tokens and invites
wrong answers. This server parses it once and returns a clean, structured response instead.

More importantly, it can also answer questions no file on disk ever can: **`evaluate`** runs a
QlikView expression against the document's actual currently loaded data and selections - "what
does `Sum(SalesAmount)` return right now, given what's selected?" - by talking directly to a
running QlikView Desktop instance. The same live connection also powers an optional, more accurate
mode (`live=true`) on several other tools, returning real cardinality, real table relationships,
and current in-memory state instead of what was last saved to disk.

The result: an AI assistant can explore a QlikView document's script, structure, and live data
through one consistent, typed tool interface - the same way it already works with a codebase -
instead of needing someone to export, parse, or manually explain what's inside.

## What this doesn't do

- **It's read-only, permanently.** No writing to documents, no reload, no macro execution - the
  gateway is built so those calls are unreachable from tool code, by design, not just unused.
- **The live tools only reach one machine.** COM automation only talks to a QlikView Desktop
  instance in the same interactive Windows session as the server process - there's no way to run
  one shared instance for a team; each person runs their own, next to their own QlikView license.
- **Secret redaction is pattern-based, not guaranteed.** `get_script` and `get_data_sources` mask
  credential-shaped `key=value` pairs (`PWD=`, `UID=`, `Password=`, ...) in connection strings
  before returning them, and `get_variables` flags variables whose name suggests a secret rather
  than withholding the value. A credential embedded in the script some other way won't be caught -
  treat the output as likely safe, not guaranteed safe.
- **QlikView Desktop only** - no Qlik Sense support (different product, different API).

## Two ways each tool answers a question

Every tool has a **static** source (reads the document's load script and its `-prj` export folder
on disk, no QlikView needed) and some tools also support a **live** source (`live=true`, talks to
a running QlikView Desktop instance via COM automation). They are not interchangeable:

| | Static (default) | Live (`live=true`) |
|---|---|---|
| Needs | Nothing but the files on disk | QlikView Desktop installed, licensed, and running, with the document open or reachable |
| Reflects | The document as last saved (with its `-prj` folder present) | The document's actual current state, including anything not yet saved and any runtime changes |
| Shareable | Yes - any number of people/clients can read the same files, no per-user QlikView needed | No - only works for whoever is at the machine QlikView is running on (see [Windows-only, single-machine](#windows-only-single-machine)) |

| Tool | Static source | Live source (`live=true`) |
|---|---|---|
| `list_documents` | File-system scan for `.qvw`/`.qvf` | - |
| `get_script` | `-prj` export's `LoadScript.txt` | - |
| `get_variables` | `SET`/`LET` parsed from the script | Current in-memory values, including ones changed at runtime by an input box, action, or macro |
| `get_data_model` | Table names (from the script) and field tags (from the `-prj` export) - no cardinality, no table-to-field grouping, since neither is recorded in the export | The real associative data model: actual row/distinct-value counts and which fields link which tables |
| `get_sheets` | Sheets and object ids from the `-prj` export's project index | Sheets and objects from the live document |
| `get_object` | One object's type and, for charts, its dimensions/expressions, from its `-prj` XML file | Same, read live via COM |
| `get_data_sources` | Connection statements, `FROM`-clause file references, includes, and the `BINARY` statement, parsed from the script | - (QlikView has no dedicated "list data sources" API; a live version would just re-parse the same script text) |
| `evaluate` | - | The only way to get this: runs an expression against the document's currently loaded data and selections |

A few sources are worth calling out explicitly:

- **`get_sheets` and `get_object`'s two sources use different vocabularies** and are not directly
  comparable. Static gives a sheet's internal id (e.g. `Document\SH01`) and an object's type as an
  XML element name (e.g. `GraphProperties`); live gives the sheet's displayed title (e.g. `"Main"`)
  and QlikView's own numeric object-type code (e.g. `"11"`). Dimensions and expressions agree
  either way; object ids from either source work as input to `get_object`.
- **`get_variables` (static)** reflects the script's *declared* values, not necessarily what's
  currently in memory: a `LET` value that depends on a function or another variable comes back as
  the literal, unevaluated script text.
- **`get_data_sources` and `get_script` mask credential-shaped key=value pairs** (`PWD=`, `UID=`,
  `Password=`, ...) in connection strings before returning them. This is pattern matching, not
  parsing - a credential embedded some other way in the script won't be caught.

### The `-prj` export folder

Every static-source tool needs this folder to exist (see [Usage](#usage), step 1). QlikView never
creates it on its own - only fills it in on save if it's already there.

## Windows-only, single-machine — but only the live path

**Only calling a live tool is actually Windows-only.** The live path is COM automation
(`QlikTech.QlikView`), driven from a PowerShell worker process — a Windows-only mechanism that
only ever talks to a QlikView Desktop instance running in the same interactive Windows session as
the server process. It cannot reach a different machine or a different user's session, so **one
server instance serves one person on one machine** — it is not something you can host centrally
for a team. Anyone who wants the live tools runs their own local instance, next to their own
licensed QlikView Desktop installation.

Compiling the server, and starting it, need nothing Windows-specific — the `powershell` process is
only ever launched from inside a live tool call, not at startup. Calling a *static*-mode tool
(the default for every tool except `evaluate`) needs nothing but the files on disk, on any OS.

What each stage actually needs:

| Stage | Needs |
|---|---|
| Compile | Java 21 and Maven only — works on any OS |
| Start the server | Java 21 only — works on any OS. `powershell.exe` is never invoked at startup, and QlikView Desktop does not need to be open; the server starts and waits for an MCP client to connect regardless |
| Call a static-mode tool (the default for every tool except `evaluate`) | Nothing but the `-prj` export files on disk — works on any OS |
| Run the full test suite (`mvn test`) | On any OS: most of it runs regardless. The gateway's tests launch real PowerShell processes end to end (against fake worker scripts, so QlikView itself is not required) and are skipped, not failed, on non-Windows |
| Call a live tool (`evaluate`, or any tool with `live=true`) | Windows, with `powershell.exe` on PATH, and QlikView Desktop installed, licensed, and running, with the document open or reachable, at the moment the tool is called |

The packaged jar itself isn't split by platform today — it's one artifact containing both paths —
so in practice you still need Windows to build and test it end to end, even though the static
tools' own logic has no OS dependency.

## Requirements

- Windows, with `powershell.exe` on PATH
- Java 21
- Maven
- QlikView Desktop, installed and licensed - only needed for live tool calls, not to compile,
  test, or start the server

## Usage

1. **(Optional) Enable the `-prj` export for any document you want to query without `live=true`.** Create an
   empty folder named `<documentName>-prj` next to the `.qvw`/`.qvf` file, then open and save the
   document once in QlikView Desktop. `evaluate` and any call with `live=true` skip this entirely
   and go straight to a running QlikView Desktop instead - the folder is only needed for the
   default (static) mode of the other four tools.
2. **Set `qlikview.roots`** to the folder(s) containing your documents (see
   [Configuration](#configuration)) - every tool call is rejected until this is set.
3. **Register the server with your MCP client** (see [Building and running](#building-and-running))
   and call a tool with an absolute document path, for example:

   ```json
   { "name": "get_script", "arguments": { "document": "C:\\Data\\Sales.qvw" } }
   ```

4. **Add `"live": true`** on `get_variables`, `get_data_model`, `get_sheets`, or `get_object` to
   read from a running QlikView Desktop instead of the `-prj` export - requires QlikView open with
   the document reachable at that moment:

   ```json
   { "name": "get_data_model", "arguments": { "document": "C:\\Data\\Sales.qvw", "live": true } }
   ```

5. **Use `evaluate` for anything that needs the document's actual current state** - it has no
   static mode, only live:

   ```json
   { "name": "evaluate", "arguments": { "document": "C:\\Data\\Sales.qvw", "expression": "=Sum(SalesAmount)" } }
   ```


## Building and running

```
mvn test
```

Every tool call is rejected until `qlikview.roots` is configured (see [Configuration](#configuration)
below) - set it before running the server, not after:

```
set QLIKVIEW_MCP_ROOTS=C:\path\to\your\qlikview\documents
mvn spring-boot:run
```

or, packaged:

```
mvn package
set QLIKVIEW_MCP_ROOTS=C:\path\to\your\qlikview\documents
java -jar target\qlikview-mcp-server.jar
```

(`set` is `cmd.exe` syntax; in PowerShell use `$env:QLIKVIEW_MCP_ROOTS = "C:\path\to\..."` instead.)

The server speaks MCP over STDIO. Register it with your MCP client the way that client expects a
local STDIO server - for a CLI-based client like Claude Code:
`claude mcp add qlikview -- java -jar <path-to-jar>`. Clients configured via a settings file
(Claude Desktop, VS Code Copilot, and others) instead need the same command and the
`QLIKVIEW_MCP_ROOTS` environment variable added to that client's own MCP server configuration -
check your client's documentation for where that goes.

## Configuration

| Property | Default | Purpose |
|---|---|---|
| `qlikview.roots` | *(empty)* | Comma-separated list of folders a document path must resolve under. Required - if empty, every tool call is rejected. Set via the `QLIKVIEW_MCP_ROOTS` environment variable, or override `qlikview.roots` directly (see below) |
| `qlikview.output.max-chars` | `200000` | Caps how much text a single free-text response returns before truncating - applied by `evaluate` and `get_script` |
| `qlikview.output.max-items` | `500` | Caps how many entries each list a tool returns may contain before truncating - applied to every list-returning tool |
| `qlikview.call.timeout` | `15s` | Maximum time a single live (COM) call may run before the worker process is force-killed |

`qlikview.roots` has a dedicated environment variable (`QLIKVIEW_MCP_ROOTS`, used above) because
its default (empty) makes the server unusable, so it needs to be easy to set without touching a
properties file. The other two are ordinary Spring Boot `@ConfigurationProperties` and can be
overridden any of the usual Spring Boot ways - a JVM system property
(`java -Dqlikview.call.timeout=30s -jar ...`), an environment variable following Spring's relaxed
binding (`QLIKVIEW_CALL_TIMEOUT=30s`), or your own `application.properties` override.

## Other known limitations

Beyond what's covered in [What this doesn't do](#what-this-doesnt-do):

- Behaviour against Section Access-protected documents has not been verified - a live tool call
  against one may return silently-reduced data, block, or hit the call timeout.

## License

[MIT](LICENSE)
