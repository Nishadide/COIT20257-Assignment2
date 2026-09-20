# EdgeLayer package — Role A

The TCP server, its per-connection threads, the analysis logic, and the
edge monitoring interface.

## What goes in here

| Class | Purpose |
|---|---|
| `EdgeServer` | `main`; opens `ServerSocket` on `Protocol.EDGE_PORT`, accept loop |
| `DeviceHandler` | `extends Thread`; one instance per accepted connection |
| `EdgeAnalyser` | Decides the command `SensorFactor` for a reported reading |
| `EdgeWindow` | The Swing window: four labelled value boxes in a 2×2 layout |

## Rules to follow

- **One handler thread per accepted connection.** This is marked explicitly
  (criterion 5: "multithreading is used when concurrency exists").
- **Create `ObjectOutputStream` BEFORE `ObjectInputStream`**, or both ends
  block on construction.
- Call `reset()` on the output stream when resending a mutated object, or
  Java sends a stale cached copy.
- Never touch Swing from a socket thread — use `SwingUtilities.invokeLater`.
- The analysis logic uses `Contract.SensorSpec`. Do not re-declare the
  thresholds or the label strings here.

## The two-threshold rule

The alarm is governed by the **ideal range**; the actuator runs until the
**perfect value**. A reading that has re-entered the ideal range gets alarm
"Normal" while its actuator is still running. `SensorSpec.alarmFor()` and
`SensorSpec.actuatorFor()` already implement this — use them.

## Note

Line graphs on the edge interface are optional in the specification and earn
no marks. Do not build them.
