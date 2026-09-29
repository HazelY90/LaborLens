import { test, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";

// Compile only the service layer, without adding a test framework dependency.
const root = fileURLToPath(new URL("../", import.meta.url));
const output = mkdtempSync(join(tmpdir(), "laborlens-api-"));
after(() => rmSync(output, { recursive: true, force: true }));
execFileSync(process.execPath, [resolve(root, "node_modules/typescript/bin/tsc"),
  "--module", "commonjs", "--moduleResolution", "node", "--target", "ES2022",
  "--lib", "ES2022,DOM", "--strict", "--skipLibCheck", "--outDir", output,
  "services/apiClient.ts", "services/dataService.ts", "services/authService.ts", "utils/charts.ts"], { cwd: root });
const load = createRequire(import.meta.url);
const { createApiClient, apiClient, ApiError } = load(join(output, "services/apiClient.js"));
const data = load(join(output, "services/dataService.js"));
const { observed, groupPolicies, sourceUrl } = load(join(output, "utils/charts.js"));

test("missing observations keep their slots while true zero remains an observation", () => {
  assert.deepEqual(observed([{ period: "2023", value: 0 }, { period: "2024", value: null }, { period: "2025", value: 5 }]),
    [{ period: "2023", value: 0, index: 0 }, { period: "2025", value: 5, index: 2 }]);
});

test("policy periods sort chronologically and preserve distinct overlapping sources", () => {
  const policy = (id, start, end, source) => ({ id, periodStart: start, periodEnd: end, source: { id: source }, policy: "Text", page: null });
  const groups = groupPolicies([policy(1, 2023, 2025, 1), policy(2, 2019, 2025, 2), policy(3, 2023, 2025, 3), policy(4, 2023, 2025, 1)]);
  assert.deepEqual(groups.map((group) => group.items.map((item) => item.id)), [[2], [1, 4], [3]]);
  assert.equal(sourceUrl("javascript:alert(1)"), undefined);
  assert.equal(sourceUrl("https://example.com/policy.pdf"), "https://example.com/policy.pdf");
});
const access = (token = "valid") => ({ accessToken: token, tokenType: "Bearer", expiresIn: 1800,
  user: { id: 1, email: "test@example.com", username: "Test" } });
const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
const deferred = () => {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
};
const client = (fetcher) => createApiClient({ baseUrl: "http://localhost:8080/api", fetcher });

test("default authentication, public requests, query encoding and JSON body", async () => {
  const calls = [];
  const api = client(async (url, options) => { calls.push({ url, ...options }); return json({ ok: true }); });
  await api.startSession(async () => access());
  await api.request("/data/metadata", { query: { label: "a & b", absent: undefined } });
  await api.request("/auth/register", { auth: false, method: "POST", body: { username: "Test" },
    headers: { Authorization: "Bearer ignored" } });
  assert.equal(calls[0].headers.get("Authorization"), "Bearer valid");
  assert.equal(calls[0].url.searchParams.get("label"), "a & b");
  assert.equal(calls[0].url.searchParams.has("absent"), false);
  assert.equal(calls[1].headers.has("Authorization"), false);
  assert.equal(calls[1].credentials, "include");
  assert.equal(calls[1].body, '{"username":"Test"}');
});

test("a page reload restores the session through the refresh cookie", async () => {
  const paths = [];
  const api = client(async (url, options) => {
    paths.push(url.pathname);
    if (url.pathname.endsWith("/refresh")) {
      assert.equal(options.headers.has("Authorization"), false);
      return json(access());
    }
    assert.equal(options.headers.get("Authorization"), "Bearer valid");
    return json({ points: [{ period: "2024", value: null }] });
  });
  const result = await api.request("/data/annual-employment-rate");
  assert.equal(result.points[0].value, null);
  assert.deepEqual(paths, ["/api/auth/refresh", "/api/data/annual-employment-rate"]);
});

test("concurrent 401 responses share a refresh and retry once", async () => {
  const gate = deferred();
  let refreshes = 0;
  let requests = 0;
  const api = client(async (url, options) => {
    if (url.pathname.endsWith("/refresh")) { refreshes++; await gate.promise; return json(access("new")); }
    requests++;
    return options.headers.get("Authorization") === "Bearer old" ? json({}, 401) : json({ ok: true });
  });
  await api.startSession(async () => access("old"));
  const one = api.request("/data/metadata");
  const two = api.request("/data/metadata");
  await new Promise((done) => setImmediate(done));
  assert.equal(refreshes, 1);
  gate.resolve();
  await Promise.all([one, two]);
  assert.equal(requests, 4);
});

test("a second 401 clears the session without a refresh loop", async () => {
  let refreshes = 0;
  const api = client(async (url) => {
    if (url.pathname.endsWith("/refresh")) { refreshes++; return json(access("new")); }
    return json({ code: "UNAUTHENTICATED", message: "Expired" }, 401);
  });
  await api.startSession(async () => access());
  await assert.rejects(api.request("/data/metadata"), { status: 401 });
  assert.equal(refreshes, 1);
  assert.equal(api.getSession(), null);
});

test("logout waits for refresh and prevents stale session restoration", async () => {
  const gate = deferred();
  const calls = [];
  const api = client(async (url) => {
    calls.push(url.pathname);
    if (url.pathname.endsWith("/refresh")) { await gate.promise; return json(access()); }
    return new Response(null, { status: 204 });
  });
  const refresh = api.refresh();
  const rejected = assert.rejects(refresh, { code: "SESSION_CHANGED" });
  const logout = api.endSession(() => api.request("/auth/logout", { method: "POST", auth: false }));
  assert.equal(calls.length, 1);
  gate.resolve();
  await Promise.all([logout, rejected]);
  assert.equal(api.getSession(), null);
  assert.deepEqual(calls, ["/api/auth/refresh", "/api/auth/logout"]);
  await assert.rejects(api.request("/data/metadata"), { status: 401 });
});

test("errors preserve backend fields; public 401 does not refresh", async () => {
  let calls = 0;
  const api = client(async () => {
    calls++;
    return json({ code: "INVALID_CREDENTIALS", message: "Invalid credentials", field: "email" }, 401);
  });
  await assert.rejects(api.request("/auth/login", { auth: false }), (error) =>
    error instanceof ApiError && error.status === 401 && error.field === "email");
  assert.equal(calls, 1);
  const empty = client(async () => new Response(null, { status: 204 }));
  assert.equal(await empty.request("/auth/logout", { auth: false }), undefined);
  const network = client(async () => { throw new TypeError("fetch failed"); });
  await assert.rejects(network.request("/auth/login", { auth: false }), { code: "NETWORK_ERROR" });
});

test("abort, external URL and server-side use are blocked before fetch", async () => {
  let calls = 0;
  const api = client(async () => { calls++; return json({}); });
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(api.request("/data/metadata", { signal: controller.signal }), { name: "AbortError" });
  await assert.rejects(api.request("//example.com", { auth: false }), /API-relative/);
  assert.equal(calls, 0);
  const server = createApiClient({ baseUrl: "http://localhost/api", isBrowserOnly: true });
  assert.throws(() => server.getSession(), /browser-only/);
});

test("data functions send the documented paths and comparison parameters", async () => {
  const original = apiClient.request;
  const calls = [];
  apiClient.request = async (path, options) => { calls.push({ path, ...options }); return {}; };
  try {
    await data.getMetadata();
    await data.getAnnualTrend({ sex: "FEMALE", ageGroup: "AGE_30_34" });
    await data.getAnnualComparison(2025);
    await data.getMonthlyTrend();
    await data.getQuarterlyTrend();
    await data.getQuarterlyComparison(2025, 2);
    await data.getCountTrend();
    await data.getCountComparison(2024, 4);
    await data.getPolicies("SKILLS_DEVELOPMENT");
    assert.equal(calls.length, 9);
    assert.equal(calls.every((call) => call.auth !== false), true);
    assert.deepEqual(calls[1].query, { sex: "FEMALE", ageGroup: "AGE_30_34", view: "trend" });
    assert.deepEqual(calls[5].query, { view: "comparison", year: 2025, quarter: 2 });
    assert.equal(calls[7].path, "/data/quarterly-employment-count");
    assert.deepEqual(calls[8].query, { type: "SKILLS_DEVELOPMENT" });
  } finally { apiClient.request = original; }
});
