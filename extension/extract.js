// Recipe extractor injected into the page via chrome.scripting.executeScript.
//
// MUST be fully self-contained: it is serialized and run in the page's context, so
// it cannot reference anything outside its own body. Reads schema.org/Recipe from
// <script type="application/ld+json"> blocks (single object, array, or @graph) and
// returns the raw Recipe object plus a best-effort absolute image URL.
function extractRecipeFromPage() {
  function typeMatches(node) {
    const t = node && node["@type"];
    if (!t) return false;
    return t === "Recipe" || (Array.isArray(t) && t.includes("Recipe"));
  }

  // Walk JSON-LD: objects, arrays and @graph containers, collecting Recipe nodes.
  function collectRecipes(node, out) {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node)) {
      for (const item of node) collectRecipes(item, out);
      return;
    }
    if (Array.isArray(node["@graph"])) {
      for (const item of node["@graph"]) collectRecipes(item, out);
    }
    if (typeMatches(node)) out.push(node);
  }

  const recipes = [];
  const scripts = document.querySelectorAll('script[type="application/ld+json"]');
  for (const script of scripts) {
    let data;
    try {
      data = JSON.parse(script.textContent);
    } catch (e) {
      continue; // skip malformed blocks, keep scanning the rest
    }
    collectRecipes(data, recipes);
  }

  if (recipes.length === 0) {
    return { error: "Kein Rezept (schema.org/Recipe) auf dieser Seite gefunden." };
  }
  const recipe = recipes[0];

  // image can be a string, {url}, or an array of either; fall back to og:image.
  function firstImageUrl(img) {
    if (!img) return null;
    if (typeof img === "string") return img;
    if (Array.isArray(img)) {
      for (const entry of img) {
        const url = firstImageUrl(entry);
        if (url) return url;
      }
      return null;
    }
    if (typeof img === "object") return img.url || firstImageUrl(img["@list"]) || null;
    return null;
  }
  let imageUrl = firstImageUrl(recipe.image);
  if (!imageUrl) {
    const og = document.querySelector('meta[property="og:image"]');
    if (og) imageUrl = og.content || null;
  }
  // Resolve protocol-relative / relative URLs against the page.
  if (imageUrl) {
    try {
      imageUrl = new URL(imageUrl, document.baseURI).href;
    } catch (e) {
      // leave as-is
    }
  }

  return {
    recipe: recipe,
    imageUrl: imageUrl,
    title: recipe.name || "(ohne Titel)",
    sourceUrl: location.href,
  };
}
