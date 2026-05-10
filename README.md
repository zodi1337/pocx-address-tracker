# PoCX Mining Tracker Android App

Android app for tracking PoCX addresses, balances, transactions and mined blocks.

Remember....i'm NOT a developer. This project is mostly created with the help of ChatGPT ;)

---

# Data Source

The app directly uses the Esplora-compatible API from the official PoCX Explorer:

```text
https://explorer.bitcoin-pocx.org/api/address/{address}
https://explorer.bitcoin-pocx.org/api/address/{address}/txs
https://explorer.bitcoin-pocx.org/api/address/{address}/txs/chain/{last_seen_txid}
```

---

# Block Detection

A mined block is detected when a transaction:

* contains at least one Coinbase input:
  `vin[].is_coinbase == true`
* pays out to the tracked mining address:
  `vout[].scriptpubkey_address == address`
* is confirmed and contains a valid `block_height`

---

# Notifications

Android only allows periodic background work through WorkManager at roughly 15-minute intervals by default.

For real-time notifications in the future, the app would require:

* Push notifications
* WebSocket support
* or a dedicated backend service

---

# Opening the Project

1. Install Android Studio
2. Open the folder:

```text
pocx-tracker-explorer
```

3. Run Gradle Sync
4. Build and install the app on your Android device

---

# Default Address

The default tracked address can be changed in:

```text
app/src/main/java/ch/zodi/pocxtracker/MainActivity.kt
```

```kotlin
private const val DEFAULT_ADDRESS =
    "pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv"
```

(Default: Nogrod PoCX Mining Pool)

---

# Local API Test

Inside the `tools` folder there is a small Python test script:

```bash
python3 tools/test_explorer_api.py pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv
```

The script prints:

* current balance
* transaction count
* detected Coinbase rewards

---

# Features

* Multi-address tracking
* Push notifications for mined blocks
* DE/EN language support
* Configurable Explorer API
* Clickable TX_ID links
* Statistics dashboard
* Address labels
* Pull-to-refresh
* Modern Material-style UI

---

# License

MIT License
