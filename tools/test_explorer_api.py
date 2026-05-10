#!/usr/bin/env python3
import sys, json, urllib.request

BASE = "https://explorer.bitcoin-pocx.org/api"
ADDR = sys.argv[1] if len(sys.argv) > 1 else "pocx1qp00ljf5sy0kdk4h8x5n4erzdshkzj4cdmvjpsv"

def get(path):
    with urllib.request.urlopen(BASE + path, timeout=20) as r:
        return json.loads(r.read().decode())

def all_txs(addr):
    out = []
    page = get(f"/address/{addr}/txs")
    out += page
    while len(page) >= 25:
        last = page[-1]["txid"]
        page = get(f"/address/{addr}/txs/chain/{last}")
        if not page: break
        out += page
        if len(out) > 1000: break
    seen, unique = set(), []
    for tx in out:
        if tx["txid"] not in seen:
            seen.add(tx["txid"]); unique.append(tx)
    return unique

info = get(f"/address/{ADDR}")
txs = all_txs(ADDR)
won = []
for tx in txs:
    if any(vin.get("is_coinbase") for vin in tx.get("vin", [])):
        reward = sum(vout.get("value", 0) for vout in tx.get("vout", []) if vout.get("scriptpubkey_address") == ADDR)
        if reward > 0:
            won.append({"height": tx.get("status", {}).get("block_height"), "txid": tx["txid"], "reward_sats": reward})

chain = info.get("chain_stats", {})
mempool = info.get("mempool_stats", {})
balance = chain.get("funded_txo_sum",0)-chain.get("spent_txo_sum",0)+mempool.get("funded_txo_sum",0)-mempool.get("spent_txo_sum",0)
print(json.dumps({"address": ADDR, "balance_pocx": balance/100000000, "tx_count": chain.get("tx_count",0)+mempool.get("tx_count",0), "won_blocks": len(won), "won": won}, indent=2))
