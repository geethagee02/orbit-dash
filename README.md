# Orbit Dash

A one-thumb orbit game by [Geetha M](https://github.com/geethagee02).

A ship holds a circle around a planet. Arcs fall inward. One tap reverses direction. Miss the arc and the run gets faster. Touch one and the orbit ends.

## Portfolio

```
Orbit Dash is a one-thumb Android game: a ship holds orbit around a planet, and a tap reverses direction to dodge arcs falling inward.
The playfield is a Jetpack Compose Canvas with a parallax starfield, a lit planet, an engine trail, and glowing hazard arcs, stepped by a ViewModel on a coroutine clock.
Personal best and the pilot name are stored in Room; Retrofit posts a finished run to a Node and SQLite leaderboard and loads the board back.
Collision, dodge scoring, and the speed curve are pure Kotlin covered by JUnit. The app is MVVM with Hilt, Coroutines, Retrofit, and Room.
```

## Run the server

Node.js 22 or newer. SQLite is `node:sqlite`, which ships with Node. Express is the only dependency.

```bash
cd server
npm install
npm test
npm start
```

Listens on port 8791. Override with `PORT` and `ORBIT_DB`. The default database file is `server/data/orbit-dash.sqlite`.

```bash
curl http://127.0.0.1:8791/health
curl http://127.0.0.1:8791/v1/scores
curl -X POST http://127.0.0.1:8791/v1/scores \
  -H 'content-type: application/json' \
  -d '{"name":"Geetha","score":40,"dodges":3}'
```

The phone is the authority for a run. The server checks the shape of the payload and stores it. It does not replay the physics.

A pilot name is 1–16 characters after trimming: letters, numbers, spaces, hyphens, dots, and apostrophes, and it must contain a letter or a number. Score is an integer from 0 to 1000000. Dodges is an integer from 0 to 100000. The board is the top scores, highest first, earlier post first on a tie. `limit` defaults to 20 and cannot exceed 50.

## Run the app

Android Studio, JDK 17, and SDK 35. Portrait.

```bash
bash ./gradlew testDebugUnitTest assembleDebug
```

The unit tests are JUnit on the JVM. They cover collision, dodge scoring, the speed curve, and the name rule. They do not need a device. They do not start Hilt or Room.

`API_BASE` in `app/build.gradle.kts` is `http://10.0.2.2:8791/`, which is the emulator's alias for the computer running the server. On a phone, change it to the computer's LAN address, for example `http://192.168.1.20:8791/`. The sample server is plain HTTP, so the manifest allows cleartext.

Launch, then tap anywhere to flip. The amber mark shows which way the ship is headed. The board is on the title screen and again after a run. Posting a run saves the pilot name on the phone. The best score is kept in Room when the new score is strictly higher, whether or not the board is reachable.

## How a frame works

```text
tap
 │
 ▼
GameViewModel clock          coroutine, ~60 Hz, pauses when the activity stops
 │
 ▼
OrbitRules.step              pure: flip, move, collide, score, spawn
 │
 ├── Room                     personal best and pilot name
 └── Retrofit  POST /v1/scores
               GET  /v1/scores     Node, SQLite
```

The ship rides the circle of radius 1. An arc is an annular slice. Overlap in both radius and angle is a hit, and a hit in a slice cancels dodges from that same slice. An arc that passes the orbit outside the ship's angle is a dodge: 10 points, plus 5 for each step of the streak, capped at 50. Angular speed, inbound speed, and the gap between spawns all tighten as dodges add up. The first arcs are placed off the ship so the opening is readable.

The canvas is the whole playfield: three layers of stars drift at different speeds, the planet is a lit disc with an atmosphere, hazard arcs are stroked with a soft outer edge, and the ship leaves a fading trail on the orbit.
