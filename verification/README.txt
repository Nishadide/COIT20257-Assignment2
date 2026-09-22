VERIFICATION HARNESSES
======================

These four programs were used during development to prove the framework
behaves as the specification requires. They are NOT part of the submitted
program and are not needed to run it. They are kept here as evidence of
testing.

  SpecCheck.java        33 checks against section 1 of the specification,
                        the nine steps of the mutual authentication and
                        the session key exchange, clause by clause.

  WorkflowCheck.java    31 checks against section 2, the secured
                        interaction workflow: what the device reports,
                        what the edge decides, and that every SensorFactor
                        crossing the connection is encrypted.

  CryptoTest.java       22 checks on the cryptography itself, including
                        four man-in-the-middle attack scenarios that must
                        all fail.

  IntegrationTest.java  39 checks driving a real device layer and a real
                        edge server over a real socket, from connection
                        through authentication, reporting, actuator
                        correction and disconnection.

  LiveDemo.java         starts both layers and captures screenshots of the
                        edge layer window.

TOTAL: 125 checks, all passing.

TO RUN THEM
-----------
From the project root, with the project already compiled to a folder
called build:

    javac -cp build -d build verification\*.java
    java -cp build SpecCheck
    java -cp build WorkflowCheck
    java -cp build Security.CryptoTest
    java -cp build IntegrationTest

CryptoTest declares "package Security", so it compiles into the Security
package and is run as Security.CryptoTest.

IntegrationTest opens a real server socket on port 8888, so no other edge
server may be running when it is used.
