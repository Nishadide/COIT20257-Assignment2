COIT20257 Distributed Systems - Assignment 2
A Secured Edge Computing Framework for Smart Farming
====================================================

START HERE:  HOW-TO-RUN.txt


WHAT THIS IS
------------
Two programs that talk to each other over TCP.

  The DEVICE LAYER simulates four IoT sensors on a farm: Temperature,
  Humidity, Moisture and Light. It reports its readings and it carries
  out the commands it is given. It decides nothing for itself.

  The EDGE LAYER is a multi-threaded TCP server. It receives the
  readings, decides whether each one is out of its ideal range, and
  commands the alarm and the actuator that put it right.

Everything between them is encrypted, and neither will talk to the other
until they have proved who they are.


THE FOUR PACKAGES
-----------------
Contract     The classes both layers must agree on, so neither can drift
             from the other: SensorFactor (the message), CSAuthenticator
             (the authentication message), Protocol (the port, the
             algorithms, the timings), SensorSpec (the four sensors and
             their ranges), DemoLogger (the required demonstration
             output).

EdgeLayer    EdgeServer    opens the ServerSocket and accepts connections
             DeviceHandler one thread per connected device layer
             EdgeAnalyser  the decision logic, stateless
             EdgeWindow    the monitoring interface

DeviceLayer  DeviceMain         the window and the two menus
             SensorPanel        one sensor's slider, alarm and actuator
             EdgeConnection     the socket, the streams, the handshake
             ReportingThread    sends the four readings every 3 seconds
             CommandThread      receives commands and applies them
             ActuatorController one thread per sensor, correcting it

Security     Authenticator  builds and verifies the two authentication
                            messages, the nine specification steps
             CryptoUtil     RSA-2048 and AES-128, key files, encoding
             SecurityKeys   the pair of keys one layer holds
             KeyGenerator   creates the four key files


HOW THE SECURITY WORKS, IN SHORT
--------------------------------
1. The device layer sends its name signed with its own private key, and a
   fresh random verification string encrypted with the edge layer's
   public key. Only the real edge layer can read that string.

2. The edge layer checks the signed name with the device layer's public
   key, recovers the verification string, and generates a session key.

3. The edge layer replies with its own name signed with its private key,
   the verification string encrypted under the new session key, and the
   session key itself encrypted with the device layer's public key.

4. The device layer recovers the session key, checks the edge layer's
   signed name, and checks that the verification string came back intact.

Step 4 is what defeats an attacker. A recorded signed name can be
replayed, because it never changes. The current verification string
cannot be: it is fresh every time and only the holder of the edge
layer's private key can read it.

From then on every SensorFactor travels encrypted with AES-128 in CBC
mode, with a fresh random initialisation vector for each message, so the
same reading never produces the same cipher text twice.


TESTING
-------
See verification\README.txt. 125 automated checks, all passing.
