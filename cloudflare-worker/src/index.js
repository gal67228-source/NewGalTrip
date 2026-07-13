const GOOGLE_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "gal-family-trips-routes" });
    }
    if (request.method !== "POST" || url.pathname !== "/route") {
      return json({ ok: false, error: "not_found" }, 404);
    }

    const expected = `Bearer ${env.APP_ROUTES_TOKEN || ""}`;
    if (!env.APP_ROUTES_TOKEN || request.headers.get("Authorization") !== expected) {
      return json({ ok: false, error: "unauthorized" }, 401);
    }
    if (!env.GOOGLE_ROUTES_API_KEY) {
      return json({ ok: false, error: "missing_google_key" }, 500);
    }

    let input;
    try { input = await request.json(); }
    catch { return json({ ok: false, error: "invalid_json" }, 400); }

    const mode = normalizeMode(input.mode);
    const origin = toWaypoint(input.origin);
    const destination = toWaypoint(input.destination);
    if (!origin || !destination) {
      return json({ ok: false, error: "missing_origin_or_destination" }, 400);
    }

    const cacheKey = input.cacheKey || await sha256(JSON.stringify({ origin, destination, mode }));
    const cacheRequest = new Request(`${url.origin}/cache/${cacheKey}/${mode}`, { method: "GET" });
    const cache = caches.default;
    const cached = await cache.match(cacheRequest);
    if (cached) {
      const data = await cached.json();
      return json({ ...data, cached: true });
    }

    const body = {
      origin,
      destination,
      travelMode: googleMode(mode),
      computeAlternativeRoutes: false,
      languageCode: input.languageCode || "he",
      units: "METRIC"
    };

    const fieldMask = [
      "routes.duration",
      "routes.distanceMeters",
      "routes.localizedValues.duration",
      "routes.localizedValues.distance",
      "routes.legs.steps.distanceMeters",
      "routes.legs.steps.staticDuration",
      "routes.legs.steps.travelMode",
      "routes.legs.steps.navigationInstruction.instructions",
      "routes.legs.steps.transitDetails"
    ].join(",");

    const googleResponse = await fetch(GOOGLE_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": env.GOOGLE_ROUTES_API_KEY,
        "X-Goog-FieldMask": fieldMask
      },
      body: JSON.stringify(body)
    });

    const raw = await googleResponse.json();
    if (!googleResponse.ok) {
      return json({
        ok: false,
        error: "google_routes_error",
        status: googleResponse.status,
        message: raw?.error?.message || "Routes API failed"
      }, googleResponse.status);
    }

    const route = raw.routes?.[0];
    if (!route) return json({ ok: false, error: "no_route" }, 404);

    const result = {
      ok: true,
      mode,
      durationSeconds: parseDuration(route.duration),
      distanceMeters: route.distanceMeters || 0,
      durationText: route.localizedValues?.duration?.text || "",
      distanceText: route.localizedValues?.distance?.text || "",
      steps: flattenSteps(route),
      cached: false,
      generatedAt: new Date().toISOString()
    };

    const cacheResponse = json(result);
    cacheResponse.headers.set("Cache-Control", "public, max-age=21600");
    await cache.put(cacheRequest, cacheResponse.clone());
    return cacheResponse;
  }
};

function normalizeMode(value) {
  if (value === "walk" || value === "transit" || value === "drive") return value;
  return "walk";
}
function googleMode(mode) {
  if (mode === "transit") return "TRANSIT";
  if (mode === "drive") return "DRIVE";
  return "WALK";
}
function toWaypoint(value) {
  if (!value || typeof value !== "object") return null;
  if (value.latLng && Number.isFinite(value.latLng.latitude) && Number.isFinite(value.latLng.longitude)) {
    return { location: { latLng: value.latLng } };
  }
  if (typeof value.address === "string" && value.address.trim()) {
    return { address: value.address.trim() };
  }
  return null;
}
function parseDuration(value) {
  if (!value || typeof value !== "string") return 0;
  return Math.ceil(Number(value.replace("s", "")) || 0);
}
function flattenSteps(route) {
  const output = [];
  for (const leg of route.legs || []) {
    for (const step of leg.steps || []) {
      const transit = step.transitDetails;
      output.push({
        travelMode: step.travelMode || "",
        instruction: step.navigationInstruction?.instructions || "",
        distanceMeters: step.distanceMeters || 0,
        durationSeconds: parseDuration(step.staticDuration),
        transit: transit ? {
          departureStop: transit.stopDetails?.departureStop?.name || "",
          arrivalStop: transit.stopDetails?.arrivalStop?.name || "",
          departureTime: transit.localizedValues?.departureTime?.time?.text || "",
          arrivalTime: transit.localizedValues?.arrivalTime?.time?.text || "",
          headsign: transit.headsign || "",
          lineName: transit.transitLine?.name || "",
          lineShort: transit.transitLine?.nameShort || "",
          vehicleName: transit.transitLine?.vehicle?.name?.text || "",
          vehicleType: transit.transitLine?.vehicle?.type || "",
          stopCount: transit.stopCount || 0
        } : null
      });
    }
  }
  return output;
}
async function sha256(text) {
  const data = new TextEncoder().encode(text);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, "0")).join("").slice(0, 24);
}
function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "X-Content-Type-Options": "nosniff"
    }
  });
}
