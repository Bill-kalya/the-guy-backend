"""
TheGuy Backend — Production Load Test
=======================================

Read-only graduated load test.

Stages:
    warmup → 100 → 500 → 1000 → 2500 → 5000 VUs

Usage:
    python loadtest/load_test.py --base-url https://api.theguy.co.ke \
        --auth-token "$AUTH_TOKEN" \
        --admin-token "$ADMIN_TOKEN"

Environment variables:
    BASE_URL
    AUTH_TOKEN
    ADMIN_TOKEN

IMPORTANT:
    This test only calls GET endpoints.
    Do NOT add payment/order/write endpoints to this test without
    designing dedicated test data and cleanup first.
"""

import argparse
import asyncio
import json
import os
import random
import statistics
import sys
import time
from dataclasses import dataclass, field
from typing import Optional

import aiohttp


# ============================================================================
# LOAD PROFILE
# ============================================================================

STAGES = [
    {"name": "warmup", "vus": 10, "duration": 30, "ramp": 10},
    {"name": "100-vu", "vus": 100, "duration": 60, "ramp": 20},
    {"name": "500-vu", "vus": 500, "duration": 60, "ramp": 30},
    {"name": "1000-vu", "vus": 1000, "duration": 60, "ramp": 45},
    {"name": "2500-vu", "vus": 2500, "duration": 60, "ramp": 90},
    {"name": "5000-vu", "vus": 5000, "duration": 60, "ramp": 120},
]


# p95 maximum acceptable latency.
THRESHOLDS = {
    "warmup": 500,
    "100-vu": 800,
    "500-vu": 1200,
    "1000-vu": 2000,
    "2500-vu": 3000,
    "5000-vu": 5000,
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


ENDPOINTS = [

    # ------------------------------------------------------------------------
    # PUBLIC
    # ------------------------------------------------------------------------

    Endpoint(
        "categories",
        "GET",
        "/api/categories",
        weight=3,
    ),

    Endpoint(
        "platform-stats",
        "GET",
        "/api/platform/stats",
        weight=2,
    ),

    Endpoint(
        "nearby-providers",
        "GET",
        "/api/providers/nearby"
        "?lat=-1.286388&lng=36.817223&radius=10",
        weight=5,
    ),

    Endpoint(
        "search-providers",
        "GET",
        "/api/search/providers"
        "?q=plumber&lat=-1.286388&lng=36.817223",
        weight=5,
    ),

    Endpoint(
        "search-suggestions",
        "GET",
        "/api/search/suggestions?q=plumb",
        weight=3,
    ),

    Endpoint(
        "nearby-jobs-public",
        "GET",
        "/api/jobs/nearby"
        "?lat=-1.286388&lng=36.817223&radius=10",
        weight=4,
    ),

    # ------------------------------------------------------------------------
    # AUTHENTICATED USER
    # ------------------------------------------------------------------------

    Endpoint(
        "user-profile",
        "GET",
        "/api/users/profile",
        auth=True,
        weight=4,
    ),

    Endpoint(
        "job-history",
        "GET",
        "/api/jobs/history?page=0&size=10",
        auth=True,
        weight=4,
    ),

    Endpoint(
        "customer-stats",
        "GET",
        "/api/jobs/stats",
        auth=True,
        weight=2,
    ),

    Endpoint(
        "payment-history",
        "GET",
        "/api/payments/history?page=0&size=10",
        auth=True,
        weight=3,
    ),

    Endpoint(
        "notifications",
        "GET",
        "/api/notifications?page=0&size=10",
        auth=True,
        weight=4,
    ),

    Endpoint(
        "unread-count",
        "GET",
        "/api/notifications/unread-count",
        auth=True,
        weight=5,
    ),

    Endpoint(
        "wallet",
        "GET",
        "/api/wallet",
        auth=True,
        weight=3,
    ),

    Endpoint(
        "wallet-transactions",
        "GET",
        "/api/wallet/transactions?page=0&size=10",
        auth=True,
        weight=2,
    ),

    Endpoint(
        "provider-me",
        "GET",
        "/api/providers/me",
        auth=True,
        weight=3,
    ),

    Endpoint(
        "provider-dashboard",
        "GET",
        "/api/providers/me/dashboard",
        auth=True,
        weight=2,
    ),

    Endpoint(
        "provider-wallet",
        "GET",
        "/api/providers/me/wallet",
        auth=True,
        weight=2,
    ),

    Endpoint(
        "reviews-provider",
        "GET",
        "/api/reviews/provider/"
        "00000000-0000-0000-0000-000000000001/summary",
        auth=True,
        weight=2,
    ),

    Endpoint(
        "disputes",
        "GET",
        "/api/disputes?page=0&size=10",
        auth=True,
        weight=1,
    ),

    Endpoint(
        "payout-history",
        "GET",
        "/api/payouts/history?page=0&size=10",
        auth=True,
        weight=1,
    ),

    Endpoint(
        "customer-quotes",
        "GET",
        "/api/quotes/customer?page=0&size=10",
        auth=True,
        weight=1,
    ),

    Endpoint(
        "provider-quotes",
        "GET",
        "/api/quotes/provider?page=0&size=10",
        auth=True,
        weight=1,
    ),

    # ------------------------------------------------------------------------
    # ADMIN
    # ------------------------------------------------------------------------

    Endpoint(
        "admin-users-summary",
        "GET",
        "/api/v1/admin/users/summary",
        admin_only=True,
        weight=1,
    ),

    Endpoint(
        "admin-providers-summary",
        "GET",
        "/api/v1/admin/providers/summary",
        admin_only=True,
        weight=1,
    ),

    Endpoint(
        "admin-finance-summary",
        "GET",
        "/api/v1/admin/finance/summary",
        admin_only=True,
        weight=1,
    ),

    Endpoint(
        "admin-jobs-summary",
        "GET",
        "/api/v1/admin/jobs/summary",
        admin_only=True,
        weight=1,
    ),

    Endpoint(
        "admin-safety-summary",
        "GET",
        "/api/v1/admin/trust-safety/summary",
        admin_only=True,
        weight=1,
    ),

    Endpoint(
        "admin-audit-logs",
        "GET",
        "/api/v1/admin/audit-logs?size=10",
        admin_only=True,
        weight=1,
    ),
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

        self.p50 = percentile(self.latencies, 0.50)
        self.p95 = percentile(self.latencies, 0.95)
        self.p99 = percentile(self.latencies, 0.99)

        self.avg = statistics.mean(self.latencies)

        self.rps = self.total_requests / max(self.duration_s, 0.01)

        self.success_rate = (
            self.successful / max(self.total_requests, 1)
        )

        threshold = THRESHOLDS.get(self.name, 999999)

        self.threshold_met = self.p95 <= threshold


def percentile(values: list[float], p: float) -> float:

    if not values:
        return 0

    index = int((len(values) - 1) * p)

    return values[index]


# ============================================================================
# ENDPOINT SELECTION
# ============================================================================

def pick_endpoint(
    auth_token: Optional[str],
    admin_token: Optional[str],
) -> Endpoint:

    candidates = []

    for endpoint in ENDPOINTS:

        if endpoint.admin_only and not admin_token:
            continue

        if endpoint.auth and not auth_token:
            continue

        candidates.append(endpoint)

    if not candidates:
        raise RuntimeError(
            "No endpoints available. "
            "Provide --auth-token and/or --admin-token."
        )

    weights = [e.weight for e in candidates]

    return random.choices(
        candidates,
        weights=weights,
        k=1,
    )[0]


# ============================================================================
# VIRTUAL USER
# ============================================================================

async def virtual_user(
    session: aiohttp.ClientSession,
    base_url: str,
    auth_token: Optional[str],
    admin_token: Optional[str],
    results: list[RequestResult],
    stop_event: asyncio.Event,
):

    while not stop_event.is_set():

        endpoint = pick_endpoint(
            auth_token,
            admin_token,
        )

        # ------------------------------------------------------------
        # Select correct token
        # ------------------------------------------------------------

        token = None

        if endpoint.admin_only:

            token = admin_token

        elif endpoint.auth:

            token = auth_token

        # ------------------------------------------------------------
        # Headers
        # ------------------------------------------------------------

        headers = {
            "Accept": "application/json",
            "User-Agent": "TheGuy-LoadTest/1.0",
        }

        if token:

            headers["Authorization"] = f"Bearer {token}"

        url = (
            base_url.rstrip("/")
            + endpoint.path
        )

        start = time.perf_counter()

        try:

            async with session.request(
                endpoint.method,
                url,
                headers=headers,
                timeout=aiohttp.ClientTimeout(
                    total=15
                ),
            ) as response:

                await response.read()

                latency = (
                    time.perf_counter()
                    - start
                ) * 1000

                success = (
                    200 <= response.status < 300
                )

                results.append(
                    RequestResult(
                        status=response.status,
                        latency_ms=latency,
                        success=success,
                        endpoint=endpoint.name,
                        error=None
                        if success
                        else f"HTTP {response.status}",
                    )
                )

        except asyncio.TimeoutError:

            latency = (
                time.perf_counter()
                - start
            ) * 1000

            results.append(
                RequestResult(
                    status=0,
                    latency_ms=latency,
                    success=False,
                    endpoint=endpoint.name,
                    error="timeout",
                )
            )

        except aiohttp.ClientError as error:

            latency = (
                time.perf_counter()
                - start
            ) * 1000

            results.append(
                RequestResult(
                    status=0,
                    latency_ms=latency,
                    success=False,
                    endpoint=endpoint.name,
                    error=type(error).__name__,
                )
            )

        except Exception as error:

            latency = (
                time.perf_counter()
                - start
            ) * 1000

            results.append(
                RequestResult(
                    status=0,
                    latency_ms=latency,
                    success=False,
                    endpoint=endpoint.name,
                    error=str(error)[:100],
                )
            )

        # Small random delay.
        await asyncio.sleep(
            random.uniform(0.02, 0.10)
        )


# ============================================================================
# STAGE
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
    print(
        f"STARTING {name}: "
        f"{vus} VUs / {duration}s / ramp {ramp}s"
    )
    print("=" * 75)

    results: list[RequestResult] = []

    stop_event = asyncio.Event()

    # ------------------------------------------------------------
    # Connection pool
    # ------------------------------------------------------------

    connector = aiohttp.TCPConnector(
        limit=vus + 100,
        limit_per_host=vus + 100,
        keepalive_timeout=30,
        enable_cleanup_closed=True,
    )

    timeout = aiohttp.ClientTimeout(
        total=15,
        connect=10,
    )

    async with aiohttp.ClientSession(
        connector=connector,
        timeout=timeout,
    ) as session:

        tasks = []

        # --------------------------------------------------------
        # Ramp-up
        # --------------------------------------------------------

        delay = (
            ramp / vus
            if vus > 0
            else 0
        )

        for i in range(vus):

            task = asyncio.create_task(
                virtual_user(
                    session,
                    base_url,
                    auth_token,
                    admin_token,
                    results,
                    stop_event,
                )
            )

            tasks.append(task)

            if delay > 0:

                await asyncio.sleep(delay)

        print(
            f"All {vus} VUs started. "
            f"Running for {duration}s..."
        )

        # --------------------------------------------------------
        # Main test
        # --------------------------------------------------------

        await asyncio.sleep(duration)

        stop_event.set()

        await asyncio.gather(
            *tasks,
            return_exceptions=True,
        )

    # ------------------------------------------------------------
    # Calculate
    # ------------------------------------------------------------

    stage_result = StageResult(
        name=name,
        vus=vus,
        duration_s=duration,
    )

    stage_result.total_requests = len(results)

    stage_result.successful = sum(
        1
        for result in results
        if result.success
    )

    stage_result.failed = (
        stage_result.total_requests
        - stage_result.successful
    )

    stage_result.latencies = [
        result.latency_ms
        for result in results
    ]

    # Errors

    for result in results:

        if not result.success:

            error = result.error or "unknown"

            stage_result.errors[error] = (
                stage_result.errors.get(error, 0)
                + 1
            )

        status = str(result.status)

        stage_result.statuses[status] = (
            stage_result.statuses.get(status, 0)
            + 1
        )

    stage_result.calculate()

    return stage_result


# ============================================================================
# PRINT RESULT
# ============================================================================

def print_stage_result(result: StageResult):

    threshold = THRESHOLDS.get(
        result.name,
        999999,
    )

    passed = (
        result.success_rate >= MIN_SUCCESS_RATE
        and result.threshold_met
    )

    print()
    print("-" * 75)

    print(
        f"Stage: {result.name}"
    )

    print(
        f"VUs: {result.vus}"
    )

    print(
        f"Requests: {result.total_requests}"
    )

    print(
        f"Throughput: {result.rps:.1f} req/s"
    )

    print(
        f"Success: "
        f"{result.success_rate * 100:.2f}% "
        f"({result.successful} successful / "
        f"{result.failed} failed)"
    )

    print(
        f"Latency: "
        f"avg={result.avg:.0f}ms "
        f"p50={result.p50:.0f}ms "
        f"p95={result.p95:.0f}ms "
        f"p99={result.p99:.0f}ms"
    )

    print(
        f"Threshold: "
        f"p95 <= {threshold}ms "
        f"→ "
        f"{'MET' if result.threshold_met else 'EXCEEDED'}"
    )

    print(
        f"Result: "
        f"[{'PASS' if passed else 'FAIL'}]"
    )

    if result.errors:

        print(
            "Errors:"
        )

        for error, count in sorted(
            result.errors.items(),
            key=lambda x: -x[1],
        ):

            print(
                f"  {error}: {count}"
            )

    if result.statuses:

        print(
            "HTTP statuses:"
        )

        for status, count in sorted(
            result.statuses.items()
        ):

            print(
                f"  {status}: {count}"
            )

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
    print("THE GUY — PRODUCTION LOAD TEST")
    print("#" * 75)

    print(
        f"Target: {base_url}"
    )

    print(
        f"Customer/provider token: "
        f"{'YES' if auth_token else 'NO'}"
    )

    print(
        f"Admin token: "
        f"{'YES' if admin_token else 'NO'}"
    )

    print(
        "Stages: "
        + ", ".join(
            stage["name"]
            for stage in stages
        )
    )

    print("#" * 75)

    # ------------------------------------------------------------
    # Production safety check
    # ------------------------------------------------------------

    if (
        base_url.startswith("https://api.theguy.co.ke")
        and not auth_token
        and not admin_token
    ):

        print()
        print(
            "ERROR: Production target requires "
            "at least one authentication token."
        )

        return 1

    # ------------------------------------------------------------
    # Connectivity
    # ------------------------------------------------------------

    connector = aiohttp.TCPConnector(
        limit=20,
        keepalive_timeout=30,
    )

    async with aiohttp.ClientSession(
        connector=connector
    ) as session:

        url = (
            base_url.rstrip("/")
            + "/api/platform/stats"
        )

        print()
        print(
            f"Connectivity check: {url}"
        )

        try:

            async with session.get(
                url,
                timeout=aiohttp.ClientTimeout(
                    total=10
                ),
            ) as response:

                print(
                    f"HTTP {response.status}"
                )

                if response.status >= 500:

                    print(
                        "WARNING: API is already "
                        "returning 5xx."
                    )

                    return 1

        except Exception as error:

            print(
                f"ERROR: Cannot reach API: "
                f"{error}"
            )

            return 1

    # ------------------------------------------------------------
    # Run stages
    # ------------------------------------------------------------

    results = []

    for stage in stages:

        result = await run_stage(
            stage,
            base_url,
            auth_token,
            admin_token,
        )

        results.append(result)

        print_stage_result(result)

        # --------------------------------------------------------
        # Stop escalation after severe failure
        # --------------------------------------------------------

        if (
            result.success_rate < 0.90
            or result.p95 > THRESHOLDS.get(
                result.name,
                999999,
            ) * 2
        ):

            print()
            print(
                "!!! SEVERE FAILURE DETECTED !!!"
            )

            print(
                "Stopping before the next stage."
            )

            break

    # ------------------------------------------------------------
    # Final
    # ------------------------------------------------------------

    print()
    print("#" * 75)
    print("FINAL SUMMARY")
    print("#" * 75)

    overall_pass = True

    for result in results:

        passed = (
            result.success_rate
            >= MIN_SUCCESS_RATE
            and result.threshold_met
        )

        if not passed:
            overall_pass = False

        print(
            f"{result.name:>10} | "
            f"{result.vus:>5} VUs | "
            f"{result.total_requests:>7} reqs | "
            f"{result.success_rate * 100:>6.2f}% | "
            f"p95={result.p95:>6.0f}ms | "
            f"[{'PASS' if passed else 'FAIL'}]"
        )

    print("#" * 75)

    print(
        "OVERALL: "
        + (
            "ALL TESTED STAGES PASSED"
            if overall_pass
            else "FAILURES DETECTED"
        )
    )

    print("#" * 75)

    return 0 if overall_pass else 1


# ============================================================================
# ARGUMENTS
# ============================================================================

def parse_args():

    parser = argparse.ArgumentParser(
        description="TheGuy production load test"
    )

    parser.add_argument(
        "--base-url",
        default=os.environ.get(
            "BASE_URL",
            "http://localhost:8080",
        ),
    )

    parser.add_argument(
        "--auth-token",
        default=os.environ.get(
            "AUTH_TOKEN"
        ),
    )

    parser.add_argument(
        "--admin-token",
        default=os.environ.get(
            "ADMIN_TOKEN"
        ),
    )

    parser.add_argument(
        "--skip-stages",
        nargs="*",
        help=(
            "Stages to skip. "
            "Example: 2500-vu 5000-vu"
        ),
    )

    return parser.parse_args()


# ============================================================================
# ENTRY POINT
# ============================================================================

if __name__ == "__main__":

    args = parse_args()

    stages = STAGES

    if args.skip_stages:

        stages = [
            stage
            for stage in STAGES
            if stage["name"]
            not in args.skip_stages
        ]

    exit_code = asyncio.run(
        main(
            args.base_url,
            args.auth_token,
            args.admin_token,
            stages,
        )
    )

    sys.exit(exit_code)