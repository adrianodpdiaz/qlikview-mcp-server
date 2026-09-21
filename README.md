# QlikView MCP Server

A read-only [MCP](https://modelcontextprotocol.io) server that gives any MCP-capable client
(Claude Code, Claude Desktop, GitHub Copilot in Agent mode, Cursor, or a custom agent) access to a QlikView Desktop document.

## Tools

| Tool | What it does | Needs |
|---|---|---|
| `list_documents` | Lists `.qvw`/`.qvf` files under the allowlisted roots | File system only |
| `get_script` | Returns a document's load script | The document's `-prj` export folder (see below) |
| `get_variables` | Returns variables as declared in the load script (`SET`/`LET` statements), with an optional name filter | The document's `-prj` export folder (see below) |
| `get_data_model` | Returns table names (from the script) and field tags - associative keys, QlikView's own system fields - from the `-prj` export | The document's `-prj` export folder (see below) |
| `get_sheets` | Returns a document's sheets and the ids of the objects placed on each | The document's `-prj` export folder (see below) |
| `get_object` | Returns one sheet object's type, and for chart-type objects, its dimensions (field names) and expressions | The document's `-prj` export folder (see below) |
| `evaluate` | Evaluates a QlikView expression against the document's currently loaded data and selections | QlikView Desktop installed, licensed, and running, with the document open or reachable |

`get_variables` reflects the script's declared values, not necessarily a document's current
in-memory state: a `LET` value that depends on a function or another variable is returned as the
literal, unevaluated script text, and any variable changed at runtime after the last reload will
not be reflected.

`get_data_model` is a partial data model: it does not report which table a given field belongs to,
row counts, or distinct-value counts, since none of these are recorded anywhere in the `-prj`
export - only QlikView's own live, in-memory data model has them.

### The `-prj` export folder

`get_script`, `get_variables`, `get_data_model`, `get_sheets`, and `get_object` all read from a
`<document>-prj` folder QlikView writes next to the document on save - but only if that folder
already exists at save time; QlikView does not create it itself. To enable it for a document:
create an empty folder named `<documentName>-prj` next to the `.qvw`/`.qvf` file, then open and
save the document once in QlikView Desktop.

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
