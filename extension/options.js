const serverEl = document.getElementById("serverUrl");
const codeEl = document.getElementById("householdCode");
const msgEl = document.getElementById("msg");

function setMsg(text, cls) {
  msgEl.textContent = text;
  msgEl.className = cls || "";
}

function cleanUrl(url) {
  return (url || "").trim().replace(/\/+$/, "");
}

async function load() {
  const cfg = await chrome.storage.sync.get(["serverUrl", "householdCode"]);
  serverEl.value = cfg.serverUrl || "";
  codeEl.value = cfg.householdCode || "";
}

document.getElementById("save").addEventListener("click", async () => {
  await chrome.storage.sync.set({
    serverUrl: cleanUrl(serverEl.value),
    householdCode: codeEl.value.trim(),
  });
  setMsg("Gespeichert.", "ok");
});

document.getElementById("test").addEventListener("click", async () => {
  const url = cleanUrl(serverEl.value);
  if (!url) {
    setMsg("Bitte zuerst eine Server-Adresse eingeben.", "err");
    return;
  }
  setMsg("Teste…");
  try {
    // 1. Is the server reachable?
    const res = await fetch(`${url}/health`);
    const data = await res.json().catch(() => null);
    if (!(res.ok && data && data.status === "ok")) {
      setMsg(`Server antwortete unerwartet (HTTP ${res.status}).`, "err");
      return;
    }
    // 2. Does the household code resolve? /imports/pending needs it — this is the
    //    same auth path the import POST uses, so it catches a wrong/stale code here.
    const code = codeEl.value.trim();
    if (!code) {
      setMsg("✓ Server erreichbar. Es fehlt noch der Haushaltscode.", "err");
      return;
    }
    const check = await fetch(`${url}/imports/pending`, {
      headers: { "X-Household-Code": code },
    });
    if (check.ok) {
      setMsg("✓ Server erreichbar und Haushaltscode gültig.", "ok");
    } else if (check.status === 404) {
      setMsg("Server erreichbar, aber dieser Haushaltscode existiert nicht auf dem Server.", "err");
    } else {
      setMsg(`Server erreichbar, aber Haushalts-Prüfung ergab HTTP ${check.status}.`, "err");
    }
  } catch (e) {
    setMsg(`Server nicht erreichbar: ${e && e.message ? e.message : e}`, "err");
  }
});

load();
