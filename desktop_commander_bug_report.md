# Desktop Commander — Bug Report: MCP timeout on long-running start_process calls

**Reporter:** Alph (lufisdavid) — blind developer, accessible Android launcher project
**Date:** 2026-07-15
**DC version:** 0.2.44
**Client:** claude-ai 0.1.0 (Claude Desktop, isDXT: true)

---

## Environment

| Field | Value |
|---|---|
| Platform | Windows (win32), x64 |
| Default shell | powershell.exe |
| Node | 24.17.0 |
| Electron | 42.5.1 / Chrome 148.0.7778.271 |
| DC memory at time of report | rss 197.64 MB, heapUsed 64.04 MB |
| Client ID | ec83d328-d9c5-480e-b50f-a919f040b1d2 |
| Total tool calls | 1794 (1778 successful, 16 failed) |
| Sessions | 50 over 35 days |
| Most used tools | read_file: 768, start_process: 435, edit_block: 315 |

---

## Summary

During a long working session (Android/Gradle project), `start_process` calls that
run for roughly **90+ seconds** cause the MCP connection to hang. The client
eventually reports:

```
MCP error -32001: Request timed out
```

or

```
No result received from the Claude Desktop app after waiting 4 minutes.
The local MCP server providing this tool may be unresponsive, crashed, or not running.
```

After this, **every subsequent tool call fails the same way** until the user
manually restarts the MCP server. This happened **6+ times in a single session**.

---

## The key problem: the failures are invisible in telemetry

This is the most important part of this report.

`get_usage_stats` reports a **99% success rate (16 failed / 1794 total)**.
That number does not reflect reality — the timeouts I experienced are **not
counted as failures**, because from the server's perspective the call was
dispatched successfully; the response simply never reached the client.

So the crash statistics look healthy while the actual user experience is
repeated hard stalls. If you are using `failedCalls` as a health metric,
**you are blind to this class of failure.**

---

## Reproduction

Reliable repro on Windows + PowerShell:

```
start_process(
  command: '$env:JAVA_HOME = "C:\\Program Files\\Android\\Android Studio\\jbr"; ' +
           '$env:Path = "$env:JAVA_HOME\\bin;" + $env:Path; ' +
           'Set-Location "C:\\path\\to\\android-project"; ' +
           'cmd /c "gradlew.bat assembleDebug --no-daemon 2>&1" | ' +
           'Select-String -Pattern "BUILD"',
  timeout_ms: 300000
)
```

Observed: the Gradle build itself completes fine (verified via log file), but the
MCP response never arrives. Note that `timeout_ms: 300000` was explicitly passed —
**the caller-supplied timeout appears to be ignored**, and something else (client-
side? transport-level?) cuts the connection at a shorter interval.

Builds taking ~30-60s: usually fine.
Builds taking ~90s+: frequent hang.
Builds taking 2-4 min: hang almost every time.

---

## Workaround we found

Redirect output to a file, run detached, then poll:

```powershell
Start-Process -FilePath "cmd.exe" `
  -ArgumentList "/c gradlew.bat assembleDebug --no-daemon > build_log.txt 2>&1" `
  -NoNewWindow
Start-Sleep -Seconds 100
Get-Content build_log.txt | Select-String -Pattern "BUILD SUCCESS|BUILD FAILED"
```

This works reliably. But it's a workaround — it means any tool-driven workflow with
real build steps (Gradle, Maven, cargo, webpack, large test suites) needs the LLM to
know this trick in advance, and most won't.

---

## Suggested fixes (in priority order)

1. **Count these as failures.** A dispatched call that never returns a result is a
   failure. Right now it silently inflates the success rate. Add a distinct counter
   (e.g. `timedOutCalls`) so this shows up in telemetry.

2. **Honor `timeout_ms`.** It was set to 300000 and the connection still dropped
   well before that. Either respect it or document what actually governs the limit.

3. **Send keepalive/progress during long-running processes.** MCP supports progress
   notifications; emitting one every ~15-30s while a process is still running would
   likely prevent the transport from being considered dead.

4. **Fail gracefully instead of poisoning the session.** Once a timeout happens, all
   subsequent calls fail until manual restart. The server should recover, or at
   minimum report clearly that it needs a restart rather than repeating the same
   4-minute wait.

5. **Document the long-running-process pattern.** If the file-redirect + poll
   approach is the intended way, put it in the tool description so the model does
   it from the start.

---

## Why this matters beyond one user

I'm blind, and I build accessibility software with Claude via Desktop Commander —
it's how I write and ship code. When the connection dies mid-build, I can't quickly
eyeball what state things are in; the recovery cost is much higher than for a
sighted user who can just glance at a terminal. Long builds are unavoidable in
Android work.

The tool is genuinely excellent otherwise — 1794 calls over 35 days on a real
project speaks for itself. This one issue is the single biggest friction point.

---

*Report compiled with concrete data from `get_usage_stats` and `get_config`
on the affected machine.*
