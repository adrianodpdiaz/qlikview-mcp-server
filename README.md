# QlikView MCP Server

A read-only [MCP](https://modelcontextprotocol.io) server that gives any MCP-capable client
(Claude Code, Claude Desktop, GitHub Copilot in Agent mode, Cursor, or a custom agent) access to a
QlikView Desktop document. Currently implemented: evaluating an expression against the document's
currently loaded data and selections.

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
| Call the `evaluate` tool | QlikView Desktop must be installed, licensed, and running, with the target document open or reachable, at the moment the tool is called - not before |

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

For the `evaluate` tool call to succeed, QlikView Desktop must already be running, with the
document you want to work against open or reachable, before an MCP client calls it.
