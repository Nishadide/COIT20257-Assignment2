# docs — Role D

Documentation deliverables and the screenshots that evidence them.

## Required by the specification (5 marks)

1. **Developer instruction** (2 marks) — how to compile, run and test the
   system. Audience: a future developer of this implementation.
2. **End user instruction** (2 marks) — how to set up a runtime environment
   OUTSIDE NetBeans: create the two folders, place the key files and JARs,
   run both programs from a command prompt. Audience: a non-developer. The
   demonstration document's screenshots are effectively the specification
   for this section.
3. **Man-in-the-middle explanation** (1 mark) — why an attacker cannot
   mislead either layer by transmitting fake `SensorFactor`s.

## Screenshots to capture

- Edge server console showing `Server is listening on port 8888`
- Device layer titled `Device Layer: Edge Disconnected!`
- The Connect dialog (hostname `localhost`, port `8888`)
- Both windows once connected
- Authentication output on BOTH consoles: verification string and session
  key, each in plain and cipher text, matching across the two
- Demo On output on BOTH consoles: `Sent in plain text 1`, `Sent in cipher
  text 1`, `Received in cipher text 1`, `Received in plain text 1` — with
  the sender's cipher text identical to the receiver's
- All four sensors alarming and being corrected
- The edge display updating live
