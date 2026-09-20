# COIT20257 Assignment 2 - Secured Edge Computing Framework

A two-layer edge computing framework for a smart-farming scenario, with
secured communication between the Device Layer and the Edge Layer.

## What it does

The **Device Layer** simulates four IoT sensors (Temperature, Humidity,
Moisture, Light), each with a slider reading, an alarm and an actuator.
It reports sensor readings continuously to the edge server.

The **Edge Layer** is a multithreaded TCP server. It analyses incoming
readings against each sensor's ideal range and sends actuator commands
back in real time. All decision-making happens here, not on the device.

All traffic is secured: the two layers perform RSA mutual authentication,
exchange an AES session key, and encrypt every message with it.

## Structure

    Contract/     Shared serializable classes: SensorFactor, CSAuthenticator
    Security/     Key generation, RSA and AES encryption helpers
    EdgeLayer/    TCP server, connection handler threads, analysis, edge GUI
    DeviceLayer/  Sensor dashboard, connect and authenticate menus, threads

## Running it

The two layers run as separate JARs from separate folders, each holding
its own key files.

    cd EdgeLayer
    java -jar EdgeServer.jar        # listens on port 8888

    cd DeviceLayer
    java -jar IOTDevices.jar        # Edge > Connect, then Security > Authentication

## Responsibility 
|------|--------|----------------|
| Leader | | Contract classes, integration, submission |
| Edge Server | | TCP server, handler threads, analysis logic |
| Device Client | | Device layer refactor, reporting and command threads |
| Security | | Key generation, authentication, encryption |
| GUI & Docs | | Edge interface, JAR packaging, documentation |


**Key files:** EdgeLayer holds EdgePri.ser, EdgePub.ser, DevicesPub.ser.
DeviceLayer holds DevicesPri.ser, DevicesPub.ser, EdgePub.ser.

**Frozen:** the Contract classes are frozen once written. Any change goes
through the team leader with all members notified.

## Milestones

- [ ] Contract classes written and frozen
- [ ] Plaintext round trip working (device reports, edge commands)
- [ ] Mutual authentication and session key exchange
- [ ] All SensorFactors encrypted
- [ ] JARs built and tested outside NetBeans
- [ ] Documentation complete

## Requirements

Java SE 8 or later. Standard library only — no external dependencies.
