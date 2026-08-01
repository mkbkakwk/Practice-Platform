const baseUrl = (process.env.STAGING_BASE_URL || "http://frontend").replace(/\/$/, "");
const usernamePrefix = process.env.STAGING_SMOKE_USERNAME_PREFIX || "stgsmoke";
const password = process.env.STAGING_SMOKE_PASSWORD;
const expectedSha = process.env.STAGING_GIT_SHA || "";

if (!password || password.startsWith("change-me")) {
  throw new Error("STAGING_SMOKE_PASSWORD is missing or still a placeholder");
}

async function request(path, options = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    redirect: "manual",
    ...options,
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  return { response, text, body };
}

function expectStatus(result, expected, label) {
  if (result.response.status !== expected) {
    throw new Error(`${label}: expected HTTP ${expected}, received ${result.response.status}`);
  }
  console.log(`PASS ${label}`);
}

function expectOk(result, label) {
  if (!result.response.ok) {
    throw new Error(`${label}: expected success, received HTTP ${result.response.status}`);
  }
  console.log(`PASS ${label}`);
}

const homepage = await request("/");
expectStatus(homepage, 200, "frontend homepage");
const assetPath = homepage.text.match(/<script[^>]+src="([^"]+\.js)"/)?.[1];
if (!assetPath) throw new Error("staging frontend asset path was not found");
const frontendAsset = await request(assetPath);
expectStatus(frontendAsset, 200, "frontend JavaScript asset");
if (!frontendAsset.text.includes("STAGING") || (expectedSha && !frontendAsset.text.includes(expectedSha))) {
  throw new Error("staging build marker or Git SHA is missing from the frontend asset");
}
console.log("PASS staging build marker");

const health = await request("/api/health");
expectStatus(health, 200, "backend health through frontend proxy");
if (health.body?.ok !== true) throw new Error("backend health response did not contain ok=true");

const problems = await request("/api/problems");
expectStatus(problems, 200, "anonymous problem list");

const anonymousMe = await request("/api/auth/me");
expectStatus(anonymousMe, 401, "protected endpoint rejects anonymous access");

const suffix = Date.now().toString(36).slice(-8);
const username = `${usernamePrefix}_${suffix}`.slice(0, 20);
const newPassword = `${password}N1`;
const jsonHeaders = { "Content-Type": "application/json" };

const registration = await request("/api/auth/register", {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify({ username, password }),
});
expectOk(registration, "register staging-only user");
if (registration.body?.user?.role !== "USER") {
  throw new Error("public staging registration did not create a USER");
}

const login = await request("/api/auth/login", {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify({ username, password }),
});
expectOk(login, "login with staging-only user");
const oldToken = login.body?.token;
if (!oldToken) throw new Error("login did not return a token");

const authenticatedMe = await request("/api/auth/me", {
  headers: { Authorization: `Bearer ${oldToken}` },
});
expectStatus(authenticatedMe, 200, "authenticated protected request");

const passwordChange = await request("/api/auth/password", {
  method: "PUT",
  headers: {
    ...jsonHeaders,
    Authorization: `Bearer ${oldToken}`,
  },
  body: JSON.stringify({ currentPassword: password, newPassword }),
});
expectOk(passwordChange, "change staging password");

const staleToken = await request("/api/auth/me", {
  headers: { Authorization: `Bearer ${oldToken}` },
});
expectStatus(staleToken, 401, "old token rejected after password change");

const oldPasswordLogin = await request("/api/auth/login", {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify({ username, password }),
});
expectStatus(oldPasswordLogin, 401, "old password rejected");

const newPasswordLogin = await request("/api/auth/login", {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify({ username, password: newPassword }),
});
expectOk(newPasswordLogin, "new password login");
if (!newPasswordLogin.body?.token) throw new Error("new password login did not return a token");

console.log("Staging HTTP smoke tests completed successfully.");
