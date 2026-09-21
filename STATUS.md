# Project status and task board

Last updated: 21 September 2026

Tick items off as you finish them and push, so everyone can see where the
project stands without asking.

---

## DONE

### Shared foundation (team leader)

- [x] GitHub repository created, structure in place
- [x] `Contract` package written, tested and **frozen**
  - `SensorFactor` — the message exchanged in both directions
  - `CSAuthenticator` — the mutual authentication message
  - `Protocol` — port 8888, cipher settings, key file names, timings
  - `SensorSpec` — the four sensors' ranges, perfect values, alarm and
    actuator texts, shared so the two layers cannot disagree
  - `DemoLogger` — the numbered plain/cipher console output the
    demonstration document requires from both sides
- [x] Key file scheme decided (see `keys/README.md`)
- [x] Message rhythm decided (see below)
- [x] NetBeans project configured and committed
- [x] `SETUP.md` written for the team

### Role A — Edge server

- [x] `EdgeServer` — `ServerSocket` on 8888, accept loop
- [x] `DeviceHandler` — `extends Thread`, one per connection
- [x] `EdgeAnalyser` — the threshold logic, moved here from Assignment 1
- [x] `EdgeWindow` — four labelled value boxes, 2x2, thread-safe updates
- [x] Demonstration logging wired in
- [x] Plaintext round trip verified over a real socket (`test/RoundTripTest`)

---

## TO DO

### Role B — Device layer client  **(BLOCKING the whole team)**

The round trip cannot be tested end to end until this exists. Everything in
Phase 2 waits on it.

- [ ] Copy the Assignment 1 device layer into `src/DeviceLayer/`
- [ ] **Remove the local threshold logic.** The device no longer decides its
      own alarm or actuator text — it reports raw readings and applies what
      the edge sends back. If the old logic is left in, the system looks
      correct while the edge does nothing, and a marker reading the source
      will see it.
- [ ] `Edge` menu: **Connect** (dialog with hostname `localhost`, port
      `8888`) and **Disconnect** (disabled until connected)
- [ ] `Security` menu: **Authentication**, **Demo On**
- [ ] Title bar states: `Device Layer: Edge Disconnected!` and
      `Device Layer: Edge Connected!`
- [ ] Reporting thread — sends four `SensorFactor`s every 3 seconds
- [ ] Command thread — receives commands and applies them through
      `SwingUtilities.invokeLater`
- [ ] Honour the "no new value" convention: `value == 0` keeps the current
      reading, `"NA"` keeps the current alarm or actuator text. Use
      `hasValue()`, `hasAlarm()`, `hasActuator()`.
- [ ] On start-up, no sensor is running until the device connects and
      authenticates (see the demonstration document)

**Start here:** run `test/LiveDemo.java` — it is a mock device layer that
already talks to the working server correctly. It shows exactly what to
send and what comes back.

**Traps:** create `ObjectOutputStream` BEFORE `ObjectInputStream` or both
ends block. The reporting thread must never wait for a reply, because the
edge only replies when a command is needed — receive on its own thread.

### Role C — Security

Testable on its own; do not wire it into the other layers until the
plaintext round trip works.

- [ ] `KeyGenerator` — a `main` that generates both RSA key pairs and writes
      `EdgePri.ser`, `EdgePub.ser`, `DevicesPri.ser`, `DevicesPub.ser`
- [ ] Generate the keys **once** and commit them, so the whole team shares
      one set — otherwise two machines cannot authenticate with each other
- [ ] `CryptoUtil` — RSA encrypt/decrypt, AES encrypt/decrypt, key loading,
      Base64 helpers. Use the algorithm names in `Contract.Protocol`.
- [ ] A standalone `main` proving the round trip: encrypt, decrypt, assert
      it matches
- [ ] Build the device side of the `CSAuthenticator` exchange
- [ ] Build the edge side of the exchange, including session key generation
- [ ] Print verification string and session key, plain and cipher, on both
      consoles — the values must match across the two

**Traps:** RSA holds 245 bytes at most, so use it only for the 128-character
verification string and the 16-byte AES key; whole `SensorFactor` objects go
through AES. Serialize an object to bytes first, then encrypt the bytes.

### Role D — Edge GUI, packaging, documentation

- [x] Edge layer window (built alongside the server)
- [ ] Build `EdgeServer.jar` and `IOTDevices.jar` as executable JARs
- [ ] Create the two runtime folders with the correct key files in each:
      - `EdgeLayer/` — `EdgePri.ser`, `EdgePub.ser`, `DevicesPub.ser`
      - `DeviceLayer/` — `DevicesPri.ser`, `DevicesPub.ser`, `EdgePub.ser`
- [ ] Test from a command prompt **outside NetBeans**:
      `java -jar EdgeServer.jar` then `java -jar IOTDevices.jar`
- [ ] Developer instruction — how to compile, run and test (2 marks)
- [ ] End user instruction — how to set up the runtime outside the IDE
      (2 marks)
- [ ] Man-in-the-middle explanation (1 mark)
- [ ] Screenshots: server listening, disconnected, connect dialog, connected,
      authentication output on both consoles, Demo On output on both
      consoles, all four sensors alarming, edge display updating live

**Do not build the line graphs.** The demonstration document says they are
optional and they earn no marks.

### Integration — everyone

- [ ] **Checkpoint: plaintext round trip working end to end** (Roles A + B)
- [ ] Wire the authentication handshake into `DeviceHandler` before the
      `SensorFactor` loop (Roles A + C)
- [ ] Encrypt every `SensorFactor` with the session key (Roles A + B + C)
- [ ] Verify the sender's cipher text is identical to the receiver's
- [ ] Test the two layers on **two separate machines**
- [ ] Final zip, submitted by the team leader

### Individual — everyone, separately

- [ ] **Part 2 teamwork report** (5 marks each, own words only)
  - Task allocations and project timeline
  - A technical problem and how team cooperation solved it
  - A cooperation problem and how it was negotiated

Keep a personal log from now: dates, what you did, what the team decided.
Written from memory at the end these answers are vague, and vague loses
marks. Copying between members is treated as plagiarism.

---

## Agreed decisions

**Message rhythm.** The device sends four `SensorFactor`s (one per sensor)
every 3 seconds. The edge replies **only when a command is needed**. The
device never blocks waiting for a reply — reporting and receiving are on
separate threads.

**The device decides nothing.** All threshold logic lives in the edge
server. The device reports raw readings and applies the alarm and actuator
states it is sent.

**The two thresholds.** The alarm is governed by the ideal range; the
actuator runs until the perfect value. A reading back inside the range but
not yet perfect shows "Normal" while its actuator is still running.

**Contract is frozen.** Any change goes through the team leader.

**Key files are shared and committed**, so every machine uses the same set.

---

## Marks this is chasing

| Criterion | Marks | Owner |
|---|---|---|
| Compiles by JDK/NetBeans, runs on JRE | 3 | D + all |
| Mutual authentication correct | 2 | C |
| Session key exchange correct | 2 | C |
| Edge server sound as a TCP server | 3 | A ✅ |
| Multithreading where concurrency exists | 3 | A ✅ + B |
| Edge server GUI layout clear | 3 | D ✅ |
| `SensorFactor` exchange correct and secured | 3 | A + B + C |
| Real-time sensor status reporting | 4 | A + B |
| Real-time actuator triggering | 4 | A + B |
| Four sensors responding concurrently | 3 | A + B |
| Documentation | 5 | D |
| Teamwork report | 5 | each individually |

The three largest items — real-time reporting, real-time actuator
triggering, and four sensors responding — total **11 marks** and all depend
on the round trip working continuously. That is why it comes before
encryption.
