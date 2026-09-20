# DeviceLayer package — Role B

The Assignment 1 dashboard, refactored into a network client.

## What goes in here

| Class | Purpose |
|---|---|
| `DeviceMain` | `main`; builds the window, the Edge and Security menus |
| `SensorPanel` | One sensor's slider, alarm and actuator (from Assignment 1) |
| `EdgeConnection` | Socket, streams, connect and disconnect |
| `ReportingThread` | `extends Thread`; sends four `SensorFactor`s every 3 seconds |
| `CommandThread` | `extends Thread`; receives commands and applies them |

## The critical change from Assignment 1

**The device no longer decides anything.** In Assignment 1 each sensor
evaluated its own reading and set its own alarm and actuator text. In
Assignment 2 that logic moves to the edge server: the device reports its raw
reading and applies whatever alarm and actuator states come back.

If the old local logic is left in, the system will look correct in a demo
while the edge server does nothing — and a marker reading the source will
see it. Strip it out deliberately.

## Rules to follow

- The reporting thread must **never block** waiting for a reply. The edge
  responds only when a command is needed, so receiving happens on its own
  thread.
- Apply incoming commands through `SwingUtilities.invokeLater`.
- Honour the "no new value" convention: `value == 0` means keep the current
  reading, `"NA"` means keep the current alarm or actuator text. Use
  `hasValue()`, `hasAlarm()` and `hasActuator()` on `SensorFactor`.
- Title bar states, per the demonstration document:
  `Device Layer: Edge Disconnected!` and `Device Layer: Edge Connected!`

## Menus required by the demonstration document

    Edge     > Connect (host + port dialog), Disconnect
    Security > Authentication, Demo On
