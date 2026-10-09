#!/usr/bin/env python3
"""
End-to-end demo scenario (spec §51) against a running stack, through the API gateway.

    python3 scripts/smoke_test.py [--base http://localhost:8080]

Requires SECUREBANK_DEMO_SEED=true. The scenario is re-runnable: it measures balances
relative to their starting values and unfreezes the account at the end.
Standard library only.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.request
import uuid

GREEN, RED, DIM, RESET = "\033[32m", "\033[31m", "\033[2m", "\033[0m"
failures = 0


class Resp:
    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self.body = body

    def json(self):
        return json.loads(self.body) if self.body else None


def call(base, method, path, token=None, body=None, headers=None):
    h = {"Content-Type": "application/json", "X-Correlation-Id": "smoke-" + uuid.uuid4().hex[:16]}
    if token:
        h["Authorization"] = "Bearer " + token
    h.update(headers or {})
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + "/api/v1" + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return Resp(r.status, dict(r.headers), r.read().decode())
    except urllib.error.HTTPError as e:
        return Resp(e.code, dict(e.headers), e.read().decode())


def check(label, condition, detail=""):
    global failures
    if condition:
        print(f"  {GREEN}✔{RESET} {label}")
    else:
        failures += 1
        print(f"  {RED}✘ {label}{RESET} {DIM}{detail}{RESET}")
    return condition


def login(base, username, password):
    r = call(base, "POST", "/auth/login", body={"username": username, "password": password})
    if not check(f"login {username}", r.status == 200, f"HTTP {r.status} {r.body[:200]}"):
        sys.exit(1)
    return r.json()["accessToken"]


def account(base, token, number):
    accounts = call(base, "GET", "/accounts", token).json()
    return next(a for a in accounts if a["accountNumber"] == number)


def poll(fn, timeout=30):
    deadline = time.time() + timeout
    while time.time() < deadline:
        result = fn()
        if result:
            return result
        time.sleep(1)
    return None


def transfer(base, token, key, amount, dest="1000000002", desc="Demo transfer"):
    return call(base, "POST", "/transfers", token, headers={"Idempotency-Key": key}, body={
        "sourceAccountNumber": "1000000001", "destinationAccountNumber": dest,
        "amount": amount, "currency": "VND", "description": desc})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    base = ap.parse_args().base

    print("1. Customer signs in and sees the account")
    c1 = login(base, "customer1", "Customer@123")
    acc = account(base, c1, "1000000001")
    start_balance = acc["balance"]
    check("account 1000000001 is visible", acc["accountNumber"] == "1000000001")
    c2_start = account(base, login(base, "customer2", "Customer@123"), "1000000002")["balance"]

    print("2. Transfer 1,000,000 VND to 1000000002")
    key = str(uuid.uuid4())
    r = transfer(base, c1, key, 1000000.00)
    check("201 Created", r.status == 201, f"HTTP {r.status} {r.body[:300]}")
    tx = r.json() or {}
    check("status SUCCESS", tx.get("status") == "SUCCESS")
    check("exactly two ledger lines (DEBIT + CREDIT)",
          sorted(l["entryType"] for l in tx.get("ledgerEntries", [])) == ["CREDIT", "DEBIT"])
    check("source balance −1,000,000",
          account(base, c1, "1000000001")["balance"] == start_balance - 1000000)
    c2 = login(base, "customer2", "Customer@123")
    check("destination balance +1,000,000",
          account(base, c2, "1000000002")["balance"] == c2_start + 1000000)

    print("3. Retry with the same Idempotency-Key")
    r2 = transfer(base, c1, key, 1000000.00)
    check("same transaction returned", (r2.json() or {}).get("transactionId") == tx.get("transactionId"))
    check("Idempotent-Replayed header", r2.headers.get("Idempotent-Replayed") == "true")
    check("no additional money movement",
          account(base, c1, "1000000001")["balance"] == start_balance - 1000000)
    r3 = transfer(base, c1, key, 2000000.00)
    check("same key + different payload → 409 IDEMPOTENCY_KEY_CONFLICT",
          r3.status == 409 and (r3.json() or {}).get("code") == "IDEMPOTENCY_KEY_CONFLICT", r3.body[:200])

    print("4. Transfer above the per-transaction limit")
    r = transfer(base, c1, str(uuid.uuid4()), 150000000.00)
    check("rejected with TRANSFER_LIMIT_EXCEEDED",
          r.status == 422 and (r.json() or {}).get("code") == "TRANSFER_LIMIT_EXCEEDED", r.body[:200])

    print("5–6. Burst of transfers triggers fraud detection; staff sees the alert")
    for i in range(6):
        transfer(base, c1, str(uuid.uuid4()), 10000.00, desc=f"Smoke burst {i + 1}")
    staff = login(base, "staff1", "Staff@123")
    alert = poll(lambda: next((a for a in call(base, "GET", "/fraud/alerts?size=50", staff).json()["content"]
                               if a["sourceAccountNumber"] == "1000000001" and a["status"] == "OPEN"), None))
    check("fraud alert generated for 1000000001", alert is not None)
    if alert:
        detail = call(base, "GET", f"/fraud/alerts/{alert['id']}", staff).json()
        check("HIGH_FREQUENCY rule triggered",
              any(rule["ruleCode"] == "HIGH_FREQUENCY" for rule in detail["rules"]),
              str([r["ruleCode"] for r in detail["rules"]]))
    stats = call(base, "GET", "/admin/stats/today", staff)
    check("ops stats load", stats.status == 200 and stats.json()["transactionsToday"] >= 1)

    print("7. Staff freezes the customer's account")
    acc_id = acc["id"]
    r = call(base, "PATCH", f"/admin/accounts/{acc_id}/freeze", staff, body={"reason": "Smoke test: suspicious burst"})
    check("account frozen", r.status == 200 and r.json()["status"] == "FROZEN", r.body[:200])

    print("8. Customer tries an outgoing transfer")
    r = transfer(base, c1, str(uuid.uuid4()), 1000.00)
    check("rejected with ACCOUNT_FROZEN",
          r.status == 422 and (r.json() or {}).get("code") == "ACCOUNT_FROZEN", r.body[:200])

    print("9. Auditor inspects, but cannot mutate")
    auditor = login(base, "auditor1", "Auditor@123")
    rec = call(base, "GET", f"/admin/reconciliation/transactions/{tx.get('transactionId')}", auditor)
    check("reconciliation balanced", rec.status == 200 and rec.json()["balanced"] is True, rec.body[:200])
    log = poll(lambda: call(base, "GET", f"/audit/logs?action=ACCOUNT_FREEZE&resourceId={acc_id}", auditor)
               .json()["content"])
    check("ACCOUNT_FREEZE audit log stored", bool(log))
    r = call(base, "PATCH", f"/admin/accounts/{acc_id}/unfreeze", auditor, body={"reason": "auditor attempt"})
    check("auditor cannot unfreeze (403)", r.status == 403, f"HTTP {r.status}")
    r = call(base, "GET", f"/accounts/{acc_id}", c2)
    check("customer2 cannot read customer1's account (403)", r.status == 403, f"HTTP {r.status}")

    print("10. Notifications reach the recipient")
    notes = poll(lambda: [n for n in call(base, "GET", "/notifications/me?size=50", c2).json()["content"]
                          if n["templateCode"] == "TRANSFER_RECEIVED" and n["relatedTransactionId"] == tx.get("transactionId")])
    check("customer2 got TRANSFER_RECEIVED", bool(notes))

    print("Cleanup")
    r = call(base, "PATCH", f"/admin/accounts/{acc_id}/unfreeze", staff, body={"reason": "Smoke test cleanup"})
    check("account unfrozen", r.status == 200, r.body[:200])

    print()
    if failures:
        print(f"{RED}{failures} check(s) failed{RESET}")
        sys.exit(1)
    print(f"{GREEN}All checks passed{RESET}")


if __name__ == "__main__":
    main()
