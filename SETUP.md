# Getting started

How to get the project onto your machine, open it, and run it. Should take
about fifteen minutes the first time.

## 1. What you need installed

| Tool | Why | Where |
|---|---|---|
| **JDK 8 or later** | To compile and run | [adoptium.net](https://adoptium.net) |
| **NetBeans** | The IDE the project is set up for | [netbeans.apache.org](https://netbeans.apache.org) |
| **Git** | To get the code and share your work | [git-scm.com](https://git-scm.com/downloads) |

Check Java is working — open a terminal (Windows: Git Bash) and run:

    java -version
    javac -version

Both should print a version number. If `javac` is missing you have a JRE,
not a JDK — install the JDK.

## 2. Get the code

Ask the team leader to add you as a collaborator on the GitHub repository,
then accept the invitation from your email.

    git clone https://github.com/<leader-username>/COIT20257-Assignment2.git
    cd COIT20257-Assignment2

First time using Git on this machine, set your identity — this is what
appears against your commits:

    git config --global user.name "Your Name"
    git config --global user.email "your.email@cqumail.com"

When you push, GitHub will ask for a password. **Your account password will
not work.** You need a Personal Access Token: GitHub → your avatar →
Settings → Developer settings → Personal access tokens → Tokens (classic) →
Generate new token, tick **repo**, copy the token, and paste it when Git
asks for a password.

## 3. Open it in NetBeans

The repository already contains a NetBeans project, so you do not create a
new one.

1. **File → Open Project**
2. Browse to the cloned `COIT20257-Assignment2` folder
3. It should show with the NetBeans project icon — select it and Open

In the Projects panel you should see:

    Assignment2
      Source Packages
        Contract      (5 classes - shared, frozen)
        EdgeLayer     (the edge server)
        DeviceLayer   (your work, if you are Role B)
        Security      (your work, if you are Role C)

## 4. Check it runs

Right-click **`EdgeLayer/EdgeServer.java`** → **Run File** (Shift+F6).

You should get:

- a window titled **Edge Layer** with four green value boxes showing `--`
- `Server is listening on port 8888` in the Output panel

Stop it with the red square in the Output panel.

### See it actually working

To watch data flow, add the test folder as a source root:

1. Right-click the project → **Properties** → **Sources**
2. *Source Package Folders* → **Add Folder** → select the `test` folder
3. OK

Then right-click **`LiveDemo.java`** → **Run File**. A mock device layer
connects and reports; the four boxes fill with readings, go out of range,
and converge back, with every message printed in the Output panel.

`RoundTripTest.java` runs the same exchange as automated checks and prints
`ALL ROUND TRIP CHECKS PASSED`.

**If you get `Address already in use`**, a previous run is still holding
port 8888 — stop it in the Output panel first.

## 5. Where your work goes

| Role | Your package | Read first |
|---|---|---|
| A — Edge server | `src/EdgeLayer/` | `src/EdgeLayer/README.md` |
| B — Device client | `src/DeviceLayer/` | `src/DeviceLayer/README.md` |
| C — Security | `src/Security/` | `src/Security/README.md` |
| D — GUI, packaging, docs | `docs/` | `docs/README.md` |

Each of those READMEs lists the classes to write and the specific traps for
that part. Read yours before starting — several of them describe bugs that
are hard to diagnose and easy to avoid.

## 6. The Contract package is frozen

`src/Contract/` holds everything both layers must agree on: the message
classes, the port and cipher settings, the sensor thresholds and labels, and
the demonstration output format.

**Code against these classes. Do not edit them.** If something genuinely
needs to change, tell the team leader, who changes it once and pushes, so we
do not end up with four different versions of the same class.

Two conventions to know before you write crypto or networking code:

- **Encrypted values are Base64-encoded** before being stored in a String
  field. Encode on the way in, decode before decrypting.
- **RSA only encrypts small payloads** (245 bytes at 2048-bit). Use RSA for
  the session key and the verification string; use the AES session key for
  whole `SensorFactor` objects.

## 7. Working day to day

**Before you start:**

    git pull

**After each chunk of work:**

    git status                          # see what changed
    git add .
    git commit -m "short summary"       # what changed, not "update"
    git push

Commit several times a session, not once a week. Good messages help later:

    git commit -m "Add CryptoUtil with RSA and AES round trip"
    git commit -m "Fix stream order in DeviceHandler - was deadlocking"

Your Part 2 teamwork report asks about task allocation and a technical
problem the team solved. `git log` becomes a dated record of exactly that,
which beats trying to remember in week 11.

**If you get stuck for more than a day, say so in the group chat.** The late
penalty is 1.75 marks per day and it applies to all four of us, so a problem
raised early is everyone's problem to solve, not yours to hide.
