# PoCX Mining Tracker Android App

Native Android-App für die PoCX-Adresse:

`pocx1qadr88lh9nre4asm2qvlzhjtypgj7rx2v47aufz`

## Datenquelle

Die App nutzt direkt die Esplora-kompatible API des PoCX-Explorers:

- `https://explorer.bitcoin-pocx.org/api/address/{address}`
- `https://explorer.bitcoin-pocx.org/api/address/{address}/txs`
- `https://explorer.bitcoin-pocx.org/api/address/{address}/txs/chain/{last_seen_txid}`

## Block-Erkennung

Ein gewonnener Block wird erkannt, wenn eine Transaktion:

1. mindestens einen Coinbase-Input hat: `vin[].is_coinbase == true`
2. an deine Mining-Adresse auszahlt: `vout[].scriptpubkey_address == address`
3. bestätigt ist und eine `block_height` hat

## Benachrichtigungen

Android erlaubt periodische Hintergrundarbeit mit WorkManager standardmässig nur ab ca. 15 Minuten Intervall. Für echte Sekundengenauigkeit bräuchte es später Push/WebSocket oder ein eigenes Backend auf deinem Pi.

## Öffnen

1. Android Studio installieren
2. Ordner `pocx-tracker-explorer` öffnen
3. Gradle Sync ausführen
4. App auf Smartphone installieren

## Adresse ändern

Die Adresse ist aktuell als Default gesetzt in:

`app/src/main/java/ch/zodi/pocxtracker/MainActivity.kt`

```kotlin
private const val DEFAULT_ADDRESS = "pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv"
```

## API-Test lokal

Im Ordner `tools` liegt ein kleines Python-Testscript:

```bash
python3 tools/test_explorer_api.py pocx1qadr88lh9nre4asm2qvlzhjtypgj7rx2v47aufz
```

Es gibt Balance, TX-Zahl und erkannte Coinbase-Rewards aus.
