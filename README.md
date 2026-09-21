# QlikView MCP Server

A read-only [MCP](https://modelcontextprotocol.io) server that gives any MCP-capable client
(Claude Code, Claude Desktop, GitHub Copilot in Agent mode, Cursor, or a custom agent) access to a QlikView Desktop document.

## Tools

| Tool | What it does | Needs |
|---|---|---|
| `list_documents` | Lists `.qvw`/`.qvf` files under the allowlisted roots | File system only |
| `get_script` | Returns a document's load script | The document's `-prj` export folder (see below) |
| `get_variables` | Returns variables as declared in the load script (`SET`/`LET` statements), with an optional name filter. With `live=true`, returns current in-memory values from a running QlikView Desktop instead | The document's `-prj` export folder (see below); QlikView Desktop running for `live=true` |
| `get_data_model` | Returns table names and fields - associative keys, QlikView's own system fields - from the `-prj` export. With `live=true`, returns the real, complete data model from a running QlikView Desktop instead, including cardinality and table membership the `-prj` export does not record | The document's `-prj` export folder (see below); QlikView Desktop running for `live=true` |
| `get_sheets` | Returns a document's sheets and the ids of the objects placed on each. With `live=true`, reads from a running QlikView Desktop instead - see the note below on how the two sources differ | The document's `-prj` export folder (see below); QlikView Desktop running for `live=true` |
| `get_object` | Returns one sheet object's type, and for chart-type objects, its dimensions (field names) and expressions. With `live=true`, reads from a running QlikView Desktop instead | The document's `-prj` export folder (see below); QlikView Desktop running for `live=true` |
| `get_data_sources` | Returns connection statements, `FROM`-clause file references, includes, and the `BINARY` statement's source document, derived from the load script | The document's `-prj` export folder (see below) |
| `evaluate` | Evaluates a QlikView expression against the document's currently loaded data and selections | QlikView Desktop installed, licensed, and running, with the document open or reachable |

`get_variables` reflects the script's declared values, not necessarily a document's current
in-memory state: a `LET` value that depends on a function or another variable is returned as the
literal, unevaluated script text, and any variable changed at runtime after the last reload will
not be reflected.

`get_data_model`'s static (default) source is a partial data model: it does not report which
table a given field belongs to, row counts, or distinct-value counts, since none of these are
recorded anywhere in the `-prj` export - use `live=true` for the complete picture.

`get_sheets`'s two sources identify a sheet and its objects differently and are not directly
comparable: the static source gives the sheet's internal id (e.g. `Document\SH01`) and each
object's type as an XML element name (e.g. `GraphProperties`); `live=true` gives the sheet's
displayed title (e.g. `"Main"`) and each object's type as QlikView's own numeric object-type code
(e.g. `"11"`). Object ids from either source work as input to `get_object`, which has the same
type-vocabulary difference between its two sources (dimensions and expressions match either way).

`get_data_sources` is a best-effort text parse of the load script, not an evaluation of it: a
source path built from a variable (e.g. `$(vPath)\file.qvd`) is returned as the literal,
unevaluated text, and connection strings are returned exactly as written, credentials included -
treat them as sensitive, the same as `get_script`'s output.

### The `-prj` export folder

`get_script`, `get_variables`, `get_data_model`, `get_sheets`, `get_object`, and
`get_data_sources` all read from a `<document>-prj` folder QlikView writes next to the document on
save - but only if that folder already exists at save time; QlikView does not create it itself. To
enable it for a document: create an empty folder named `<documentName>-prj` next to the
`.qvw`/`.qvf` file, then open and save the document once in QlikView Desktop.

## Windows only

The bridge to QlikView is COM automation (`QlikTech.QlikView`), a Windows-only mechanism, driven
from a Windows PowerShell (`powershell.exe`) worker process. It only ever talks to a QlikView
Desktop instance running on the same machine, in the same interactive Windows session, as the
server process - it cannot reach a different machine or a different user's session.

What each stage needs:

| Stage | Needs |
|---|---|
| Compile | Java 21 and Maven only - works on any OS |
| Test | Windows with `powershell.exe` on PATH - the gateway's tests launch real PowerShell processes end to end, against fake worker scripts, so QlikView itself is not required |
| Start the server | Windows, with `powershell.exe` on PATH. QlikView Desktop is **not** required to be open for the server itself to start - it starts and waits for an MCP client to connect regardless |

There is currently no CI configuration.

## Requirements

- Windows, with `powershell.exe` on PATH
- Java 21
- Maven
- QlikView Desktop installed and licensed - only needed when the `evaluate` tool is actually
  called, not to compile, test, or start the server

## Building and testing

```
mvn test
```

## Running

```
mvn spring-boot:run
```

or, after packaging:

```
mvn package
java -jar target/qlikview-mcp-server.jar
```

## Configuration

| Property | Purpose |
|---|---|
| `qlikview.roots` | Comma-separated list of folders a document path must resolve under. Required - if empty, every tool call is rejected |
| `qlikview.output.max-chars` | Caps how much text a tool response returns before truncating |
| `qlikview.call.timeout` | Maximum time a single QlikView call may run before it is force-killed |
