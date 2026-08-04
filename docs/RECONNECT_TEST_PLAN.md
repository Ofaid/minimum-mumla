# Minimum reconnect and TX-failure acceptance plan

Last reviewed: 2026-08-04

## Required behavior

For a provisioned managed radio, every unexpected disconnect enters an indefinite retry loop. The
delay starts at 2 seconds, doubles with repeated failures and caps at 60 seconds. Network return
triggers an immediate attempt; a 60-second timer is the fallback for OEM firmware that misses the
connectivity broadcast. Successful Mumble synchronization resets the backoff. Android process
recovery uses redelivery of the validated connection intent.

The only deliberate retry hold is a certificate-policy failure such as a configured SHA-256 pin
mismatch. Retrying an unchanged unsafe certificate forever would weaken the fail-closed boundary;
the full-screen UI instead states that connection is blocked for certificate safety.

## Acceptance scenarios

| Scenario | Injection | Expected evidence |
| --- | --- | --- |
| Wi-Fi/LTE loss | Disable every active transport for 20–60 seconds | Full-screen reconnecting state; no TX; immediate retry after transport returns; Ready after room join |
| Server TCP close/restart | Stop the disposable test listener or reject the client socket | Repeated attempts continue through 2/4/8/16/32/60-second backoff and recover without operator input |
| DNS failure | Point a disposable candidate config at an unresolvable test name | Candidate fails and Last Known Good reconnects; active config is not replaced |
| Port closed | Use a disposable candidate with an unused port | Same Last Known Good rollback behavior; no false Ready state |
| Server reject/kick | Use a disposable test account/server policy | Full-screen retry state and continuing capped retry; record server reason without credentials |
| Network absent for hours | Leave transports unavailable for at least two hours | Service remains safe, fallback polling continues, and return to Ready requires no app restart |
| Android kills process | Kill the process without force-stopping the package | Service intent is redelivered and the radio reconnects/joins the configured room |
| Certificate changes | Present a certificate that mismatches the configured pin | No auto-trust, no retry storm, full-screen certificate safety hold |
| PTT while offline | Press the certified hardware PTT path while reconnecting | Failure tone once per press, no TX state and no stuck input |
| Encoder produces no packet | Deny/break audio only in a disposable test environment | Failure tone if no encoded packet is handed to the synchronized connection within 1.5 seconds |
| Disconnect during TX | Remove network while a supervised test transmission is active | TX releases immediately, watchdog work clears, playback unmutes and reconnect starts |

`scripts/test-radio-reconnect.ps1` automates the reversible network-loss case. It records Wi-Fi and
mobile-data state, disables both transports for a bounded interval, restores the exact prior state
in `finally`, verifies both settings after restoration, and waits for the stable ASCII accessibility
marker `minimum-state-ready`. It refuses to begin if either original transport setting is not an
unambiguous `0` or `1`. It never presses PTT, mutates config, clears app data or prints tokens.

```powershell
Set-Location D:\mumla-dev
.\scripts\test-radio-reconnect.ps1 -WhatIf
.\scripts\test-radio-reconnect.ps1 -Force -OutageSeconds 30
```

## Meaning of the TX alert

Mumble voice packets do not provide an application-level per-packet delivery acknowledgement.
Minimum can prove that it was synchronized and handed an encoded packet to the Mumble connection;
it cannot prove from the handset alone that a remote listener heard it. Final acceptance therefore
requires a second client or server-side observer to monitor the test room. The warning implemented
in Minimum covers every locally detectable failure and must not be described as a server receipt.

Never inject a test transmission without an operator explicitly pressing PTT. Keep the supplied
access token and any certificate material outside logs, screenshots and this repository.
