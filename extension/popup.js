// Popup: detect the recipe on the active tab, then POST it (+ image bytes) to the
// configured openCook server's /imports inbox. The phone app drains it on next sync.

const titleEl = document.getElementById("title");
const metaEl = document.getElementById("meta");
const imgEl = document.getElementById("preview-img");
const btn = document.getElementById("import-btn");
const statusEl = document.getElementById("status");

let detected = null; // { recipe, imageUrl, title, sourceUrl }

function setStatus(msg, cls) {
  statusEl.textContent = msg;
  statusEl.className = cls || "";
}

async function getConfig() {
  const cfg = await chrome.storage.sync.get(["serverUrl", "householdCode"]);
  return {
    serverUrl: (cfg.serverUrl || "").replace(/\/+$/, ""),
    householdCode: cfg.householdCode || "",
  };
}

function showNeedsSetup() {
  titleEl.textContent = "Noch nicht eingerichtet";
  metaEl.textContent = "Trage zuerst Server-Adresse und Haushaltscode ein.";
  btn.style.display = "none";
  const link = document.createElement("button");
  link.className = "link-btn";
  link.textContent = "Einstellungen öffnen";
  link.onclick = () => chrome.runtime.openOptionsPage();
  statusEl.appendChild(link);
}

// Fetch an image URL and return { dataUrl, name } so the bytes can be queued and
// survive an offline server. Returns null if the image can't be fetched.
async function fetchImageData(imageUrl) {
  try {
    const resp = await fetch(imageUrl);
    if (!resp.ok) {
      console.warn("openCook: image fetch HTTP", resp.status, imageUrl);
      return null;
    }
    const blob = await resp.blob();
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
    const name = (imageUrl.split("/").pop() || "image.jpg").split("?")[0];
    return { dataUrl, name };
  } catch (e) {
    console.warn("openCook: image fetch failed:", e);
    return null;
  }
}

// Turn a data: URL back into a Blob for multipart upload.
function dataUrlToBlob(dataUrl) {
  const comma = dataUrl.indexOf(",");
  const header = dataUrl.slice(0, comma);
  const mime = (header.match(/data:([^;]+)/) || [])[1] || "application/octet-stream";
  const bin = atob(dataUrl.slice(comma + 1));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

// Ensure payload.image holds the fetched bytes (fetch once, reuse for retries).
async function ensureImageData(payload) {
  if (!payload.image && payload.imageUrl) {
    payload.image = await fetchImageData(payload.imageUrl);
  }
}

// Build a multipart body and push one recipe to the server. The image bytes are
// fetched ahead of time (see ensureImageData) so a queued retry never re-fetches.
async function pushImport(serverUrl, householdCode, payload) {
  const form = new FormData();
  form.append("recipe", JSON.stringify(payload.recipe));
  if (payload.sourceUrl) form.append("source_url", payload.sourceUrl);
  if (payload.image && payload.image.dataUrl) {
    form.append("image", dataUrlToBlob(payload.image.dataUrl), payload.image.name || "image.jpg");
  }
  let res;
  try {
    res = await fetch(`${serverUrl}/imports`, {
      method: "POST",
      headers: { "X-Household-Code": householdCode },
      body: form,
    });
  } catch (e) {
    // Network/CORS/private-network failure — the request never got a response.
    console.error("openCook: POST /imports failed", e);
    throw new Error(`Anfrage fehlgeschlagen: ${e && e.message ? e.message : e}`);
  }
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    console.error("openCook: POST /imports HTTP", res.status, body);
    throw new Error(`HTTP ${res.status}${body ? ": " + body.slice(0, 200) : ""}`);
  }
}

// Queue a payload locally when the server is unreachable, to retry later.
async function enqueue(payload) {
  const { queue = [] } = await chrome.storage.local.get("queue");
  queue.push(payload);
  await chrome.storage.local.set({ queue });
}

// Try to flush any queued imports (called on popup open).
async function flushQueue(serverUrl, householdCode) {
  if (!serverUrl || !householdCode) return;
  const { queue = [] } = await chrome.storage.local.get("queue");
  if (queue.length === 0) return;
  const remaining = [];
  for (const payload of queue) {
    try {
      await ensureImageData(payload); // in case an older queue entry has no bytes yet
      await pushImport(serverUrl, householdCode, payload);
    } catch (e) {
      remaining.push(payload); // still unreachable — keep it
    }
  }
  await chrome.storage.local.set({ queue: remaining });
}

async function detectRecipe() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab || !tab.id) {
    setStatus("Kein aktiver Tab.", "err");
    return;
  }
  let results;
  try {
    results = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: extractRecipeFromPage, // from extract.js, loaded into this popup
    });
  } catch (e) {
    titleEl.textContent = "Seite nicht lesbar";
    metaEl.textContent = "Diese Seite erlaubt keine Skripte (z. B. interne Browser-Seite).";
    return;
  }
  const data = results && results[0] && results[0].result;
  if (!data || data.error) {
    titleEl.textContent = "Kein Rezept erkannt";
    metaEl.textContent = (data && data.error) || "Auf dieser Seite wurde kein Rezept gefunden.";
    return;
  }
  detected = data;
  titleEl.textContent = data.title;
  metaEl.textContent = new URL(data.sourceUrl).hostname;
  if (data.imageUrl) {
    imgEl.src = data.imageUrl;
    imgEl.style.display = "block";
  }
  btn.disabled = false;
}

btn.addEventListener("click", async () => {
  if (!detected) return;
  btn.disabled = true;
  setStatus("Sende…");
  const { serverUrl, householdCode } = await getConfig();
  // Fetch the image now, while the source page is still available, so the bytes
  // survive queueing if the server is offline (a later URL re-fetch may fail).
  await ensureImageData(detected);
  try {
    await pushImport(serverUrl, householdCode, detected);
    setStatus("✓ Gesendet. Erscheint nach dem nächsten Sync in der App.", "ok");
  } catch (e) {
    await enqueue(detected);
    // Show the real reason (and full detail in the console) instead of a generic message.
    setStatus(
      `Fehlgeschlagen: ${e && e.message ? e.message : e}\n(gespeichert, wird später erneut gesendet — Details in der Konsole)`,
      "err",
    );
    btn.disabled = false; // allow an immediate retry
  }
});

(async function init() {
  const { serverUrl, householdCode } = await getConfig();
  if (!serverUrl || !householdCode) {
    showNeedsSetup();
    return;
  }
  flushQueue(serverUrl, householdCode); // fire-and-forget
  detectRecipe();
})();
