"""
TheGuy Backend — Load Test Suite
=================================
Graduated load test: 100 → 500 → 1000 → 2500 → 5000 concurrent virtual users.

Usage:
    python load_test.py --base-url https://api.theguy.co.ke
    python load_test.py --base-url http://localhost:8080 --admin-token <JWT>

Environment variables (alternative to flags):
    BASE_URL     — API base URL (default: http://localhost:8080)
    AUTH_TOKEN   — JWT Bearer token for authenticated endpoints
    ADMIN_TOKEN  — JWT Bearer token for admin-only endpoints
"""

import argparse
import asyncio
import json
import os
import random
import string
import statistics
import sys
import time
from dataclasses import dataclass, field
from typing import Optional

import aiohttp

# ── Configuration ────────────────────────────────────────────────────────────

STAGES = [
    {"name": "warmup",   "vus": 10,   "duration": 30},
    {"name": "100-vu",   "vus": 100,  "duration": 60},
    {"name": "500-vu",   "vus": 500,  "duration": 60},
    {"name": "1000-vu",  "vus": 1000, "duration": 60},
    {"name": "2500-vu",  "vus": 2500, "duration": 60},
    {"name": "5000-vu",  "vus": 5000, "duration": 60},
]

# p95 latency thresholds (ms) per stage — fail if exceeded
THRESHOLDS = {
    "warmup":  500,
    "100-vu":  800,
    "500-vu":  1200,
    "1000-vu": 2000,
    "2500-vu": 3000,
    "5000-vu": 5000,
}

MIN_SUCCESS_RATE = 0.95  # 95% of requests must return 2xx


# ── Endpoint definitions ─────────────────────────────────────────────────────

@dataclass
class Endpoint:
    name: str
    method: str
    path: str
    auth: bool = False
    admin_only: bool = False
    weight: int = 1          # relative probability of being selected
    body: Optional[dict] = None


ENDPOINTS: list[Endpoint] = [
    # ── Public (no auth) ─────────────────────────────────────────────────────
    Endpoint("categories",           "GET",  "/api/categories",                    weight=3),
    Endpoint("platform-stats",       "GET",  "/api/platform/stats",                weight=2),
    Endpoint("nearby-providers",     "GET",  "/api/providers/nearby?lat=-1.286388&lng=36.817223&radius=10", weight=5),
    Endpoint("search-providers",     "GET",  "/api/search/providers?q=plumber&lat=-1.286388&lng=36.817223", weight=5),
    Endpoint("search-suggestions",   "GET",  "/api/search/suggestions?q=plumb",     weight=3),
    Endpoint("nearby-jobs-public",   "GET",  "/api/jobs/nearby?lat=-1.286388&lng=36.817223&radius=10", weight=4),

    # ── Authenticated (customer + provider) ───────────────────────────────────
    Endpoint("user-profile",         "GET",  "/api/users/profile",                  auth=True, weight=4),
    Endpoint("job-history",          "GET",  "/api/jobs/history?page=0&size=10",    auth=True, weight=4),
    Endpoint("customer-stats",       "GET",  "/api/jobs/stats",                     auth=True, weight=2),
    Endpoint("payment-history",      "GET",  "/api/payments/history?page=0&size=10",auth=True, weight=3),
    Endpoint("notifications",        "GET",  "/api/notifications?page=0&size=10",   auth=True, weight=4),
    Endpoint("unread-count",         "GET",  "/api/notifications/unread-count",     auth=True, weight=5),
    Endpoint("wallet",               "GET",  "/api/wallet",                         auth=True, weight=3),
    Endpoint("wallet-transactions",  "GET",  "/api/wallet/transactions?page=0&size=10", auth=True, weight=2),
    Endpoint("provider-me",          "GET",  "/api/providers/me",                   auth=True, weight=3),
    Endpoint("provider-dashboard",   "GET",  "/api/providers/me/dashboard",         auth=True, weight=2),
    Endpoint("provider-wallet",      "GET",  "/api/providers/me/wallet",            auth=True, weight=2),
    Endpoint("reviews-provider",     "GET",  "/api/reviews/provider/00000000-0000-0000-0000-000000000001/summary", auth=True, weight=2),
    Endpoint("disputes",             "GET",  "/api/disputes?page=0&size=10",        auth=True, weight=1),
    Endpoint("payout-history",       "GET",  "/api/payouts/history?page=0&size=10", auth=True, weight=1),
    Endpoint("customer-quotes",      "GET",  "/api/quotes/customer?page=0&size=10", auth=True, weight=1),
    Endpoint("provider-quotes",      "GET",  "/api/quotes/provider?page=0&size=10", auth=True, weight=1),

    # ── Admin-only ────────────────────────────────────────────────────────────
    Endpoint("admin-users-summary",      "GET", "/api/v1/admin/users/summary",        admin_only=True, weight=1),
    Endpoint("admin-providers-summary",  "GET", "/api/v1/admin/providers/summary",    admin_only=True, weight=1),
    Endpoint("admin-finance-summary",    "GET", "/api/v1/admin/finance/summary",      admin_only=True, weight=1),
    Endpoint("admin-jobs-summary",       "GET", "/api/v1/admin/jobs/summary",         admin_only=True, weight=1),
    Endpoint("admin-safety-summary",     "GET", "/api/v1/admin/trust-safety/summary", admin_only=True, weight=1),
    Endpoint("admin-audit-logs",         "GET", "/api/v1/admin/audit-logs?size=10",   admin_only=True, weight=1),
]


# ── Result tracking ──────────────────────────────────────────────────────────

@dataclass
class RequestResult:
    status: int
    latency_ms: float
    success: bool
    error: Optional[str] = None


@dataclass
class StageResult:
    name: str
    vus: int
    duration_s: float
    total_requests: int = 0
    successful: int = 0
    failed: int = 0
    latencies: list = field(default_factory=list)
    errors: dict = field(default_factory=dict)
    threshold_p95_ms: float = 0
    threshold_met: bool = True
    success_rate: float = 0.0

    def summary(self) -> str:
        if not self.latencies:
            return f"  {self.name}: no requests recorded"
        p50 = statistics.median(self.latencies)
        p95 = self.latencies[int(len(self.latencies) * 0.95)] if len(self.latencies) >= 20 else max(self.latencies)
        p99 = self.latencies[int(len(self.latencies) * 0.99)] if len(self.latencies) >= 100 else p95
        avg = statistics.mean(self.latencies)
        rps = self.total_requests / max(self.duration_s, 0.01)
        threshold_limit = THRESHOLDS.get(self.name, 9999)
        ok = "PASS" if self.threshold_met and self.success_rate >= MIN_SUCCESS_RATE else "FAIL"

        lines = [
            f"\n{'='*70}",
            f"  Stage: {self.name}  ({self.vus} VUs, {self.duration_s:.0f}s)",
            f"{'='*70}",
            f"  Requests:    {self.total_requests}  ({rps:.0f} req/s)",
            f"  Success:     {self.success_rate*100:.1f}%  ({self.successful} ok / {self.failed} fail)",
            f"  Latencies:   avg={avg:.0f}ms  p50={p50:.0f}ms  p95={p95:.0f}ms  p99={p99:.0f}ms",
            f"  Threshold:   p95 <= {threshold_limit}ms → {'MET' if self.threshold_met else 'EXCEEDED'}",
            f"  Result:      [{ok}]",
        ]
        if self.errors:
            lines.append(f"  Errors:      {json.dumps(self.errors, indent=4)}")
        return "\n".join(lines)


# ── Load generation ───────────────────────────────────────────────────────────

def _random_string(n: int = 8) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


def _pick_endpoint(
    auth_token: Optional[str],
    admin_token: Optional[str],
) -> Endpoint:
    """Weighted random selection of an endpoint the VU is allowed to call."""
    pool = []
    for ep in ENDPOINTS:
        if ep.admin_only and not admin_token:
            continue
        if ep.auth and not auth_token and not admin_token:
            continue
        pool.append(ep)
    weights = [ep.weight for ep in pool]
    return random.choices(pool, weights=weights, k=1)[0]


async def _vu_loop(
    session: aiohttp.ClientSession,
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
    results: list[RequestResult],
    stop_event: asyncio.Event,
):
    """Single virtual user: continuously hit random endpoints until stopped."""
    while not stop_event.is_set():
        ep = _pick_endpoint(auth_token, admin_token)

        # Pick token
        if ep.admin_only:
            token = admin_token
        elif ep.auth:
            token = auth_token or admin_token
        else:
            token = None

        headers = {}
        if token:
            headers["Authorization"] = f"Bearer {token}"

        url = f"{base_url.rstrip('/')}{ep.path}"
        start = time.perf_counter()
        try:
            async with session.request(
                ep.method,
                url,
                headers=headers,
                json=ep.body,
                timeout=aiohttp.ClientTimeout(total=10),
            ) as resp:
                await resp.read()
                latency = (time.perf_counter() - start) * 1000
                results.append(RequestResult(
                    status=resp.status,
                    latency_ms=latency,
                    success=200 <= resp.status < 300,
                    error=None if 200 <= resp.status < 300 else f"HTTP {resp.status}",
                ))
        except asyncio.TimeoutError:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, "timeout"))
        except aiohttp.ClientError as e:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, str(type(e).__name__)))
        except Exception as e:
            latency = (time.perf_counter() - start) * 1000
            results.append(RequestResult(0, latency, False, str(e)[:100]))

        # Small jitter to avoid thundering herd
        await asyncio.sleep(random.uniform(0.01, 0.05))


async def run_stage(
    stage: dict,
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
    connector: aiohttp.TCPConnector,
) -> StageResult:
    vus = stage["vus"]
    duration = stage["duration"]
    name = stage["name"]

    print(f"\n▶ Starting stage '{name}' — {vus} VUs for {duration}s ...")

    results: list[RequestResult] = []
    stop_event = asyncio.Event()

    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [asyncio.create_task(_vu_loop(session, base_url, auth_token, admin_token, results, stop_event)) for _ in range(vus)]

        await asyncio.sleep(duration)
        stop_event.set()

        # Allow VUs to finish in-flight requests
        await asyncio.sleep(2)
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    sr = StageResult(name=name, vus=vus, duration_s=duration)
    sr.total_requests = len(results)
    sr.successful = sum(1 for r in results if r.success)
    sr.failed = sr.total_requests - sr.successful
    sr.success_rate = sr.successful / max(sr.total_requests, 1)
    sr.latencies = sorted(r.latency_ms for r in results)

    # Error breakdown
    err_counts: dict[str, int] = {}
    for r in results:
        if not r.success and r.error:
            err_counts[r.error] = err_counts.get(r.error, 0) + 1
    sr.errors = err_counts

    # Threshold check
    if sr.latencies:
        p95 = sr.latencies[int(len(sr.latencies) * 0.95)]
        sr.threshold_p95_ms = p95
        sr.threshold_met = p95 <= THRESHOLDS.get(name, 9999)

    return sr


# ── Main ──────────────────────────────────────────────────────────────────────

async def main(base_url: str, auth_token: Optional[str], admin_token: Optional[str], stages: Optional[list[dict]] = None):
    stages = stages or STAGES
    all_results: list[StageResult] = []

    print(f"\n{'#'*70}")
    print(f"  TheGuy Backend — Load Test")
    print(f"  Target: {base_url}")
    print(f"  Auth:   {'yes' if auth_token else 'no'}  |  Admin: {'yes' if admin_token else 'no'}")
    print(f"  Stages: {', '.join(s['name'] for s in stages)}")
    print(f"{'#'*70}")

    connector = aiohttp.TCPConnector(limit=0, force_close=True, enable_cleanup_closed=True)

    # Quick connectivity check
    async with aiohttp.ClientSession(connector=connector) as session:
        try:
            async with session.get(f"{base_url}/api/platform/stats", timeout=aiohttp.ClientTimeout(total=5)) as resp:
                print(f"\n  Connectivity check: HTTP {resp.status}")
                if resp.status >= 500:
                    print("  WARNING: Server returning 5xx — results may be unreliable")
        except Exception as e:
            print(f"\n  ERROR: Cannot reach {base_url}: {e}")
            sys.exit(1)

    for stage in stages:
        result = await run_stage(stage, base_url, auth_token, admin_token, connector)
        all_results.append(result)
        print(result.summary())

    # ── Final summary ─────────────────────────────────────────────────────────
    print(f"\n{'#'*70}")
    print(f"  FINAL SUMMARY")
    print(f"{'#'*70}")

    overall_pass = True
    for r in all_results:
        ok = r.threshold_met and r.success_rate >= MIN_SUCCESS_RATE
        if not ok:
            overall_pass = False
        p95 = r.threshold_p95_ms
        print(f"  {r.name:>12s}  │  {r.total_requests:>6d} reqs  │  {r.success_rate*100:>5.1f}% ok  │  p95={p95:>6.0f}ms  │  [{ 'PASS' if ok else 'FAIL' }]")

    print(f"\n  Overall: {'ALL STAGES PASSED' if overall_pass else 'SOME STAGES FAILED'}")
    print(f"{'#'*70}\n")

    return 0 if overall_pass else 1


def parse_args():
    parser = argparse.ArgumentParser(description="TheGuy Backend Load Test")
    parser.add_argument("--base-url", default=os.environ.get("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--auth-token", default=os.environ.get("AUTH_TOKEN"))
    parser.add_argument("--admin-token", default=os.environ.get("ADMIN_TOKEN"))
    parser.add_argument("--skip-stages", nargs="*", help="Stage names to skip (e.g. 5000-vu)")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    stages = STAGES
    if args.skip_stages:
        stages = [s for s in stages if s["name"] not in args.skip_stages]
    exit_code = asyncio.run(main(args.base_url, args.auth_token, args.admin_token, stages))
    sys.exit(exit_code)
