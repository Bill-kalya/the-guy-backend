"""
TheGuy Backend — Production Load Test (v2)
===========================================

Graduated read-only load test with realistic user behaviour.

Stages:
    warmup → 25-vu → 50-vu → 100-vu → 250-vu → 500-vu → 1000-vu

Usage:
    python loadtest/load_test.py --base-url https://api.theguy.co.ke \
        --auth-token "$AUTH_TOKEN" \
        --admin-token "$ADMIN_TOKEN"

    Two SEPARATE tokens are required:
      AUTH_TOKEN  → customer or provider account (for /api/* endpoints)
      ADMIN_TOKEN → admin account (for /api/v1/admin/* endpoints)

Environment variables:
    BASE_URL
    AUTH_TOKEN
    ADMIN_TOKEN

IMPORTANT:
    - This test only calls GET endpoints.
    - Do NOT add payment/order/write endpoints without test data + cleanup.
    - Revocation of any pasted tokens is recommended.
"""

import argparse
import asyncio
import os
import random
import statistics
import sys
import time
from dataclasses import dataclass, field
from typing import Optional

import aiohttp


# ============================================================================
# LOAD PROFILE — graduated, with ramp-up
# ============================================================================

STAGES = [
    {"name": "warmup",   "vus": 10,  "duration": 30, "ramp": 10},
    {"name": "25-vu",    "vus": 25,  "duration": 60, "ramp": 15},
    {"name": "50-vu",    "vus": 50,  "duration": 60, "ramp": 20},
    {"name": "100-vu",   "vus": 100, "duration": 60, "ramp": 30},
    {"name": "250-vu",   "vus": 250, "duration": 60, "ramp": 45},
    {"name": "500-vu",   "vus": 500, "duration": 60, "ramp": 60},
    {"name": "1000-vu",  "vus": 1000,"duration": 60, "ramp": 90},
]

THRESHOLDS = {
    "warmup":  500,
    "25-vu":   600,
    "50-vu":   800,
    "100-vu":  1000,
    "250-vu":  1500,
    "500-vu":  2500,
    "1000-vu": 4000,
}

MIN_SUCCESS_RATE = 0.95


# ============================================================================
# ENDPOINTS
# ============================================================================

@dataclass(frozen=True)
class Endpoint:
    name: str
    method: str
    path: str
    auth: bool = False
    admin_only: bool = False
    weight: int = 1
    think_time: tuple[float, float] = (1.0, 3.0)


ENDPOINTS = [

    # ── PUBLIC ────────────────────────────────────────────────────────────────

    Endpoint("categories",        "GET", "/api/categories",                                                    weight=3, think_time=(1.5, 4.0)),
    Endpoint("platform-stats",    "GET", "/api/platform/stats",                                                weight=2, think_time=(1.0, 3.0)),
    Endpoint("nearby-providers",  "GET", "/api/providers/nearby?lat=-1.286388&lng=36.817223&radius=10",        weight=5, think_time=(1.0, 2.5)),
    Endpoint("search-providers",  "GET", "/api/search/providers?q=plumber&lat=-1.286388&lng=36.817223",        weight=5, think_time=(1.0, 2.5)),
    Endpoint("search-suggestions","GET", "/api/search/suggestions?q=plumb",                                     weight=3, think_time=(2.0, 5.0)),
    Endpoint("nearby-jobs-public","GET", "/api/jobs/nearby?lat=-1.286388&lng=36.817223&radius=10",             weight=4, think_time=(1.0, 3.0)),

    # ── AUTHENTICATED (customer / provider) ──────────────────────────────────

    Endpoint("user-profile",       "GET", "/api/users/profile",                              auth=True, weight=4, think_time=(2.0, 5.0)),
    Endpoint("job-history",        "GET", "/api/jobs/history?page=0&size=10",                auth=True, weight=4, think_time=(1.5, 4.0)),
    Endpoint("customer-stats",     "GET", "/api/jobs/stats",                                 auth=True, weight=2, think_time=(2.0, 5.0)),
    Endpoint("payment-history",    "GET", "/api/payments/history?page=0&size=10",            auth=True, weight=3, think_time=(2.0, 4.0)),
    Endpoint("notifications",      "GET", "/api/notifications?page=0&size=10",               auth=True, weight=4, think_time=(1.5, 3.0)),
    Endpoint("unread-count",       "GET", "/api/notifications/unread-count",                 auth=True, weight=5, think_time=(1.0, 2.5)),
    Endpoint("wallet",             "GET", "/api/wallet",                                     auth=True, weight=3, think_time=(2.0, 5.0)),
    Endpoint("wallet-transactions","GET", "/api/wallet/transactions?page=0&size=10",         auth=True, weight=2, think_time=(2.0, 5.0)),
    Endpoint("provider-me",        "GET", "/api/providers/me",                               auth=True, weight=3, think_time=(2.0, 4.0)),
    Endpoint("provider-dashboard", "GET", "/api/providers/me/dashboard",                     auth=True, weight=2, think_time=(2.0, 5.0)),
    Endpoint("provider-wallet",    "GET", "/api/providers/me/wallet",                        auth=True, weight=2, think_time=(2.0, 5.0)),
    Endpoint("disputes",           "GET", "/api/disputes?page=0&size=10",                    auth=True, weight=1, think_time=(3.0, 6.0)),
    Endpoint("payout-history",     "GET", "/api/payouts/history?page=0&size=10",             auth=True, weight=1, think_time=(3.0, 6.0)),
    Endpoint("customer-quotes",    "GET", "/api/quotes/customer?page=0&size=10",             auth=True, weight=1, think_time=(2.0, 5.0)),
    Endpoint("provider-quotes",    "GET", "/api/quotes/provider?page=0&size=10",             auth=True, weight=1, think_time=(2.0, 5.0)),

    # ── ADMIN ONLY ───────────────────────────────────────────────────────────

    Endpoint("admin-users-summary",     "GET", "/api/v1/admin/users/summary",          admin_only=True, weight=1, think_time=(3.0, 8.0)),
    Endpoint("admin-providers-summary", "GET", "/api/v1/admin/providers/summary",      admin_only=True, weight=1, think_time=(3.0, 8.0)),
    Endpoint("admin-finance-summary",   "GET", "/api/v1/admin/finance/summary",        admin_only=True, weight=1, think_time=(3.0, 8.0)),
    Endpoint("admin-jobs-summary",      "GET", "/api/v1/admin/jobs/summary",           admin_only=True, weight=1, think_time=(3.0, 8.0)),
    Endpoint("admin-safety-summary",    "GET", "/api/v1/admin/trust-safety/summary",   admin_only=True, weight=1, think_time=(3.0, 8.0)),
    Endpoint("admin-audit-logs",        "GET", "/api/v1/admin/audit-logs?size=10",     admin_only=True, weight=1, think_time=(4.0, 10.0)),
]


# ============================================================================
# RESULT MODELS
# ============================================================================

@dataclass
class RequestResult:
    status: int
    latency_ms: float
    success: bool
    endpoint: str
    error: Optional[str] = None


@dataclass
class StageResult:
    name: str
    vus: int
    duration_s: float
    total_requests: int = 0
    successful: int = 0
    failed: int = 0
    latencies: list[float] = field(default_factory=list)
    errors: dict[str, int] = field(default_factory=dict)
    statuses: dict[str, int] = field(default_factory=dict)
    p50: float = 0
    p95: float = 0
    p99: float = 0
    avg: float = 0
    rps: float = 0
    success_rate: float = 0
    threshold_met: bool = True

    def calculate(self):
        if not self.latencies:
            return
        self.latencies.sort()
        self.total_requests = len(self.latencies) + self.failed
        self.p50 = _percentile(self.latencies, 0.50)
        self.p95 = _percentile(self.latencies, 0.95)
        self.p99 = _percentile(self.latencies, 0.99)
        self.avg = statistics.mean(self.latencies)
        self.rps = self.total_requests / max(self.duration_s, 0.01)
        self.success_rate = self.successful / max(self.total_requests, 1)
        self.threshold_met = self.p95 <= THRESHOLDS.get(self.name, 999999)


def _percentile(values: list[float], p: float) -> float:
    if not values:
        return 0
    return values[int((len(values) - 1) * p)]


# ============================================================================
# ENDPOINT SELECTION — correct token per endpoint type
# ============================================================================

def pick_endpoint(
    has_auth: bool,
    has_admin: bool,
) -> Optional[Endpoint]:

    candidates = []
    for ep in ENDPOINTS:
        if ep.admin_only and not has_admin:
            continue
        if ep.auth and not has_auth:
            continue
        candidates.append(ep)

    if not candidates:
        return None

    weights = [e.weight for e in candidates]
    return random.choices(candidates, weights=weights, k=1)[0]


# ============================================================================
# VIRTUAL USER — realistic think time
# ============================================================================

async def virtual_user(
    session: aiohttp.ClientSession,
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
    results: list[RequestResult],
    stop_event: asyncio.Event,
):
    has_auth = auth_token is not None
    has_admin = admin_token is not None

    while not stop_event.is_set():
        endpoint = pick_endpoint(has_auth, has_admin)
        if endpoint is None:
            await asyncio.sleep(1.0)
            continue

        # Select the correct token for this endpoint type
        if endpoint.admin_only:
            token = admin_token
        elif endpoint.auth:
            token = auth_token
        else:
            token = None

        headers = {
            "Accept": "application/json",
            "User-Agent": "TheGuy-LoadTest/2.0",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"

        url = base_url.rstrip("/") + endpoint.path
        start = time.perf_counter()

        try:
            async with session.request(
                endpoint.method,
                url,
                headers=headers,
                timeout=aiohttp.ClientTimeout(total=15),
            ) as resp:
                await resp.read()
                latency = (time.perf_counter() - start) * 1000
                success = 200 <= resp.status < 300
                results.append(RequestResult(
                    status=resp.status,
                    latency_ms=latency,
                    success=success,
                    endpoint=endpoint.name,
                    error=None if success else f"HTTP {resp.status}",
                ))
        except asyncio.TimeoutError:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, endpoint.name, "timeout"))
        except aiohttp.ClientError as e:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, endpoint.name, type(e).__name__))
        except Exception as e:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, endpoint.name, str(e)[:100]))

        # Realistic think time per endpoint
        await asyncio.sleep(random.uniform(*endpoint.think_time))


# ============================================================================
# STAGE RUNNER
# ============================================================================

async def run_stage(
    stage: dict,
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
) -> StageResult:

    name = stage["name"]
    vus = stage["vus"]
    duration = stage["duration"]
    ramp = stage["ramp"]

    print()
    print("=" * 75)
    print(f"STARTING {name}: {vus} VUs / {duration}s / ramp {ramp}s")
    print("=" * 75)

    results: list[RequestResult] = []
    stop_event = asyncio.Event()

    connector = aiohttp.TCPConnector(
        limit=vus + 100,
        limit_per_host=vus + 100,
        keepalive_timeout=30,
        enable_cleanup_closed=True,
    )
    timeout = aiohttp.ClientTimeout(total=15, connect=10)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        tasks = []
        delay = ramp / vus if vus > 0 else 0

        for i in range(vus):
            task = asyncio.create_task(virtual_user(
                session, base_url, auth_token, admin_token, results, stop_event,
            ))
            tasks.append(task)
            if delay > 0:
                await asyncio.sleep(delay)

        print(f"All {vus} VUs started. Running for {duration}s...")
        await asyncio.sleep(duration)
        stop_event.set()

        # Allow in-flight requests to finish
        await asyncio.sleep(2)
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    # Calculate
    sr = StageResult(name=name, vus=vus, duration_s=duration)
    sr.total_requests = len(results)
    sr.successful = sum(1 for r in results if r.success)
    sr.failed = sr.total_requests - sr.successful
    sr.latencies = [r.latency_ms for r in results]

    for r in results:
        if not r.success:
            err = r.error or "unknown"
            sr.errors[err] = sr.errors.get(err, 0) + 1
        status = str(r.status)
        sr.statuses[status] = sr.statuses.get(status, 0) + 1

    sr.calculate()
    return sr


# ============================================================================
# PRINT
# ============================================================================

def print_stage_result(result: StageResult):
    threshold = THRESHOLDS.get(result.name, 999999)
    passed = result.success_rate >= MIN_SUCCESS_RATE and result.threshold_met

    print()
    print("-" * 75)
    print(f"Stage: {result.name}  ({result.vus} VUs)")
    print(f"  Requests:     {result.total_requests}  ({result.rps:.1f} req/s)")
    print(f"  Success:      {result.success_rate * 100:.2f}%  ({result.successful} ok / {result.failed} fail)")
    print(f"  Latency:      avg={result.avg:.0f}ms  p50={result.p50:.0f}ms  p95={result.p95:.0f}ms  p99={result.p99:.0f}ms")
    print(f"  Threshold:    p95 <= {threshold}ms  ->  {'MET' if result.threshold_met else 'EXCEEDED'}")
    print(f"  Result:       [{'PASS' if passed else 'FAIL'}]")

    # Separate client errors from server errors
    client_errors = {k: v for k, v in result.errors.items() if k.startswith("HTTP 4")}
    server_errors = {k: v for k, v in result.errors.items() if k.startswith("HTTP 5")}
    other_errors  = {k: v for k, v in result.errors.items() if not k.startswith("HTTP 4") and not k.startswith("HTTP 5")}

    if client_errors:
        print(f"  Client errors (4xx):")
        for err, cnt in sorted(client_errors.items(), key=lambda x: -x[1]):
            print(f"    {err}: {cnt}")

    if server_errors:
        print(f"  Server errors (5xx) — INVESTIGATE:")
        for err, cnt in sorted(server_errors.items(), key=lambda x: -x[1]):
            print(f"    {err}: {cnt}")

    if other_errors:
        print(f"  Other errors:")
        for err, cnt in sorted(other_errors.items(), key=lambda x: -x[1]):
            print(f"    {err}: {cnt}")

    if result.statuses:
        print(f"  HTTP status breakdown:")
        for status, cnt in sorted(result.statuses.items()):
            print(f"    {status}: {cnt}")

    print("-" * 75)


# ============================================================================
# MAIN
# ============================================================================

async def main(
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
    stages: list[dict],
):

    print()
    print("#" * 75)
    print("THE GUY — PRODUCTION LOAD TEST v2")
    print("#" * 75)
    print(f"  Target:                {base_url}")
    print(f"  Customer/provider JWT: {'YES (' + str(len(auth_token)) + ' chars)' if auth_token else 'NO'}")
    print(f"  Admin JWT:             {'YES (' + str(len(admin_token)) + ' chars)' if admin_token else 'NO'}")

    if auth_token and admin_token and auth_token == admin_token:
        print()
        print("  WARNING: AUTH_TOKEN and ADMIN_TOKEN are IDENTICAL.")
        print("  Admin endpoints will get 403 (admin JWT used for user endpoints).")
        print("  Use two different accounts for accurate results.")
        print()

    print(f"  Stages: {', '.join(s['name'] for s in stages)}")
    print("#" * 75)

    # Safety: require at least one token for production
    if base_url.startswith("https://api.theguy.co.ke") and not auth_token and not admin_token:
        print("\nERROR: Production target requires at least one token.")
        return 1

    # Connectivity check
    connector = aiohttp.TCPConnector(limit=20, keepalive_timeout=30)
    async with aiohttp.ClientSession(connector=connector) as session:
        url = base_url.rstrip("/") + "/api/platform/stats"
        print(f"\nConnectivity check: {url}")
        try:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=10)) as resp:
                print(f"  HTTP {resp.status}")
                if resp.status >= 500:
                    print("  WARNING: API already returning 5xx.")
                    return 1
        except Exception as e:
            print(f"  ERROR: Cannot reach API: {e}")
            return 1

    # Run stages
    results = []
    for stage in stages:
        result = await run_stage(stage, base_url, auth_token, admin_token)
        results.append(result)
        print_stage_result(result)

        # Stop on severe failure
        if result.success_rate < 0.50 or (result.p95 > THRESHOLDS.get(result.name, 999999) * 3 and result.total_requests > 10):
            print("\n!!! SEVERE FAILURE — stopping before next stage !!!")
            break

    # Final summary
    print()
    print("#" * 75)
    print("FINAL SUMMARY")
    print("#" * 75)
    overall_pass = True
    for r in results:
        passed = r.success_rate >= MIN_SUCCESS_RATE and r.threshold_met
        if not passed:
            overall_pass = False
        print(f"  {r.name:>10}  |  {r.vus:>5} VUs  |  {r.total_requests:>7} reqs  |  {r.success_rate * 100:>6.2f}%  |  p95={r.p95:>6.0f}ms  |  [{'PASS' if passed else 'FAIL'}]")
    print("#" * 75)
    print(f"OVERALL: {'ALL STAGES PASSED' if overall_pass else 'FAILURES DETECTED'}")
    print("#" * 75)

    return 0 if overall_pass else 1


def parse_args():
    parser = argparse.ArgumentParser(description="TheGuy production load test v2")
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--auth-token", default=os.environ.get("AUTH_TOKEN"))
    parser.add_argument("--admin-token", default=os.environ.get("ADMIN_TOKEN"))
    parser.add_argument("--skip-stages", nargs="*", help="Stage names to skip (e.g. 250-vu 500-vu)")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    stages = STAGES
    if args.skip_stages:
        stages = [s for s in stages if s["name"] not in args.skip_stages]
    sys.exit(asyncio.run(main(args.base_url, args.auth_token, args.admin_token, stages)))
