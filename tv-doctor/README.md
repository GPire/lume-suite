# TV Doctor

Cleans and tunes a TV without a coding agent or a command line.

- **Mac:** double-click `TV Doctor.command`. **Windows:** double-click `TV Doctor.bat`. **Linux:** `./tv-doctor.sh`.
- Needs Python 3.9 or newer. No other install: if the `adb` program is missing, TV Doctor uses its own client.
- The first time, the TV shows "Allow debugging?". Only the person at the TV can answer.

Tests (no TV needed): `python3 test_scenarios.py` and `python3 test_pyadb.py`.
