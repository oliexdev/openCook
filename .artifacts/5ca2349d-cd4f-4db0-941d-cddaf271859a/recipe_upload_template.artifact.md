# Recipe Upload JSON Template

This template follows the `RecipeDto` structure used by the openCook server for extracting and importing recipes. It uses standard schema.org properties alongside `openCook` specific extensions.

```json
{
  "@context": "https://schema.org",
  "@type": "Recipe",
  "name": "Spaghetti Carbonara",
  "recipeYield": "4 Personen",
  "openCookServings": 4,
  "openCookCategory": "Pasta",
  "prepTime": "PT15M",
  "cookTime": "PT15M",
  "totalTime": "PT30M",
  "cookbook": "Italienische Klassiker",
  "openCookTags": ["schnell", "einfach", "Klassiker"],
  "openCookNotes": ["Originalrezept ohne Sahne.", "Pecorino Romano verwenden."],
  "openCookIngredients": [
    {
      "quantity": 400,
      "unit": "g",
      "name": "Spaghetti"
    },
    {
      "quantity": 150,
      "unit": "g",
      "name": "Guanciale oder Pancetta"
    },
    {
      "quantity": 4,
      "unit": "",
      "name": "Eier"
    },
    {
      "quantity": 50,
      "unit": "g",
      "name": "Pecorino Romano"
    },
    {
      "quantity": null,
      "unit": "",
      "name": "Schwarzer Pfeffer"
    }
  ],
  "recipeIngredient": [
    "400 g Spaghetti",
    "150 g Guanciale",
    "4 Eier",
    "50 g Pecorino Romano",
    "Frisch gemahlener schwarzer Pfeffer"
  ],
  "recipeInstructions": [
    {
      "@type": "HowToStep",
      "text": "Spaghetti in reichlich Salzwasser al dente kochen."
    },
    {
      "@type": "HowToStep",
      "text": "Guanciale in Streifen schneiden und in einer Pfanne knusprig auslassen."
    },
    {
      "@type": "HowToStep",
      "text": "Eier mit dem geriebenen Käse und viel Pfeffer verquirlen."
    },
    {
      "@type": "HowToStep",
      "text": "Nudeln abgießen (etwas Nudelwasser auffangen) und zum Speck in die Pfanne geben."
    },
    {
      "@type": "HowToStep",
      "text": "Pfanne vom Herd nehmen, Ei-Käse-Mischung unterrühren und ggf. Nudelwasser für die Cremigkeit hinzufügen."
    }
  ],
  "nutrition": {
    "@type": "NutritionInformation",
    "calories": "560 kcal",
    "proteinContent": "25 g",
    "fatContent": "30 g",
    "carbohydrateContent": "65 g",
    "openCookBasis": "pro Portion"
  },
  "image": [
    "carbonara_final.jpg",
    "carbonara_prep.jpg"
  ]
}
```

### Key Fields Explanation

| Field | Type | Description |
| :--- | :--- | :--- |
| `name` | String | The title of the recipe. |
| `openCookServings` | Integer | Numeric number of servings (used for scaling). |
| `openCookCategory` | String | Broad category (e.g., Pasta, Fleisch, Vegetarisch). |
| `prepTime` / `cookTime` | String | ISO 8601 duration format (e.g., `PT15M` for 15 minutes). |
| `openCookIngredients` | Array | **Preferred** structured ingredients with numeric quantities. |
| `recipeIngredient` | Array | Fallback list of plain-text ingredient strings. |
| `recipeInstructions` | Array | List of steps, each with a `text` property. |
| `nutrition` | Object | Nutritional information as display strings (e.g., "560 kcal"). |
| `image` | Array | Filenames of images associated with the recipe. |
