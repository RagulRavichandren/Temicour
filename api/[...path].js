module.exports = async function handler(req, res) {
  const configuredBackend = process.env.BACKEND_URL;
  if (!configuredBackend) {
    res.status(503).json({ message: "The backend is not configured. Set BACKEND_URL in the Vercel project environment." });
    return;
  }

  let backend;
  try {
    backend = new URL(configuredBackend);
  } catch {
    res.status(500).json({ message: "BACKEND_URL must be a valid HTTP or HTTPS URL." });
    return;
  }
  if (backend.protocol !== "https:" && backend.protocol !== "http:") {
    res.status(500).json({ message: "BACKEND_URL must use HTTP or HTTPS." });
    return;
  }

  const incoming = new URL(req.url, "http://vercel.local");
  const target = new URL(`${incoming.pathname}${incoming.search}`, backend.origin);
  const headers = new Headers();
  for (const [name, value] of Object.entries(req.headers)) {
    if (value && !["connection", "content-length", "host", "transfer-encoding"].includes(name.toLowerCase())) {
      headers.set(name, Array.isArray(value) ? value.join(", ") : value);
    }
  }

  let body;
  if (req.method !== "GET" && req.method !== "HEAD" && req.body !== undefined) {
    body = typeof req.body === "string" || Buffer.isBuffer(req.body)
      ? req.body
      : JSON.stringify(req.body);
  }

  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers,
      body,
      redirect: "manual"
    });
    res.status(upstream.status);
    upstream.headers.forEach((value, name) => {
      if (!["connection", "content-encoding", "content-length", "transfer-encoding"].includes(name.toLowerCase())) {
        res.setHeader(name, value);
      }
    });
    res.setHeader("Cache-Control", "no-store");
    const responseBody = Buffer.from(await upstream.arrayBuffer());
    res.send(responseBody);
  } catch (error) {
    console.error("Backend proxy request failed:", error);
    res.status(502).json({ message: "The configured backend could not be reached." });
  }
};
