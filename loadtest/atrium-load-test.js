// M3.5 (10 §6): a realistic-early-concurrency load test, not a stress test —
// the milestone card asks for "load test at realistic early concurrency,"
// and this pilot's actual usage shape is a handful of small companies each
// running their own few agents, not one company at internet scale. Models
// that directly: setup() signs up N independent companies (a burst of
// pre-auth traffic, exactly the kind auth-per-minute rate limiting exists
// for — kept small enough here to stay under the default budget, see
// COMPANY_COUNT below), then each VU drives ONE company's ordinary
// authenticated traffic (roster reads, task list reads, task creation) for
// the sustained phase, so per-company request rates stay realistic instead
// of one company's counter absorbing every VU's load.
//
// Run against a real core-api (local docker-compose by default):
//   docker run --rm -i --add-host=host.docker.internal:host-gateway \
//     -e BASE_URL=http://host.docker.internal:8080/api/v1 \
//     -v "$(pwd)/loadtest:/scripts" grafana/k6 run /scripts/atrium-load-test.js
// or, with k6 installed locally (`brew install k6`):
//   BASE_URL=http://localhost:8080/api/v1 k6 run loadtest/atrium-load-test.js
//
// Reading the results: k6 prints http_req_duration percentiles and
// http_req_failed at the end. The thresholds below are this test's actual
// pass/fail bar — a red threshold is the literal "load test passes" Done-when
// failing, not just a number to eyeball.

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080/api/v1";
const COMPANY_COUNT = Number(__ENV.COMPANY_COUNT || 15); // one per VU — "a handful of pilot companies"
const rateLimited429s = new Counter("rate_limited_429s");

export const options = {
    scenarios: {
        pilot_traffic: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: [
                { duration: "30s", target: COMPANY_COUNT }, // ramp up
                { duration: "2m", target: COMPANY_COUNT },  // hold — the sustained-load phase
                { duration: "20s", target: 0 },             // ramp down
            ],
        },
    },
    thresholds: {
        http_req_duration: ["p(95)<500", "p(99)<1500"],
        // signup/login in setup() are pre-auth and IP-scoped (08 §Security rule
        // 7) — COMPANY_COUNT signups from one load-generator IP inside a single
        // 60s window can legitimately hit ATRIUM_RATE_LIMIT_AUTH; keep
        // COMPANY_COUNT below that budget (default 10/min) or raise the env var
        // for this run. The sustained phase's own requests must not fail at all.
        http_req_failed: ["rate<0.02"],
    },
};

export function setup() {
    const companies = [];
    for (let i = 0; i < COMPANY_COUNT; i++) {
        const slug = `loadtest-${Date.now()}-${i}`;
        const email = `${slug}@loadtest.local`;
        const res = http.post(
            `${BASE_URL}/auth/signup`,
            JSON.stringify({
                companyName: `Load Test Co ${i}`,
                companySlug: slug,
                displayName: "Load Test Admin",
                email,
                password: "correct-horse-battery-staple",
            }),
            { headers: { "Content-Type": "application/json" } }
        );
        if (res.status === 429) {
            rateLimited429s.add(1);
            continue; // realistic client behavior: back off, don't hammer
        }
        check(res, { "signup succeeded": (r) => r.status === 201 });
        const body = res.json();
        const authHeaders = {
            headers: { Authorization: `Bearer ${body.token}`, "Content-Type": "application/json" },
        };

        // Every real pilot company hires at least one agent before creating
        // work (task creation 400s with no agent covering the skill, 03
        // invariant) — hire one "coder" so the sustained phase's task creates
        // are real 201s, not a validation error masquerading as load.
        const hire = http.post(
            `${BASE_URL}/companies/${body.companyId}/agents`,
            JSON.stringify({
                name: "Load Test Coder",
                roleTemplateKey: "coder",
                roleTitle: "Coder",
                skillTags: ["coding"],
                modelProvider: "anthropic",
                modelName: "claude-haiku-4-5",
            }),
            authHeaders
        );
        check(hire, { "agent hire succeeded": (r) => r.status === 201 });

        companies.push({ token: body.token, companyId: body.companyId });
    }
    if (companies.length === 0) {
        throw new Error("setup() could not sign up any companies — is core-api reachable at " + BASE_URL + "?");
    }
    return { companies };
}

export default function (data) {
    const company = data.companies[__VU % data.companies.length];
    const authHeaders = {
        headers: {
            Authorization: `Bearer ${company.token}`,
            "Content-Type": "application/json",
        },
    };

    // Ordinary pilot-company traffic mix: mostly reads, occasional writes —
    // roughly matching how a small team actually uses the dashboard (poll
    // roster/tasks far more often than they create new work).
    const roster = http.get(`${BASE_URL}/companies/${company.companyId}/roster`, authHeaders);
    check(roster, { "roster 200": (r) => r.status === 200 });

    const tasks = http.get(`${BASE_URL}/companies/${company.companyId}/tasks`, authHeaders);
    check(tasks, { "tasks list 200": (r) => r.status === 200 });

    if (Math.random() < 0.2) {
        const created = http.post(
            `${BASE_URL}/companies/${company.companyId}/tasks`,
            JSON.stringify({
                title: `Load test task ${Date.now()}`,
                description: "Created by the M3.5 load test script — safe to ignore/delete.",
                requiredSkill: "coding", // matches the "coder" agent hired for this company in setup()
                priority: 3,
            }),
            authHeaders
        );
        check(created, { "task create 201": (r) => r.status === 201 });
    }

    sleep(1 + Math.random()); // 1-2s think time between actions, not a hammer
}
