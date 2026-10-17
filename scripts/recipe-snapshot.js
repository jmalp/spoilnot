#!/usr/bin/env node
// Makes a recipe structure snapshot for app/src/test/resources/recipes/snapshots (docs/import-test-sites.md).
//
// It keeps the shape of a page's JSON-LD (types, nesting, lists, ISO durations, numbers) and replaces every other
// string and every link with synthetic placeholders, so no recipe text is stored in the repository.
//
//   From a saved page:     node scripts/recipe-snapshot.js page.html > snapshot.html
//   From a browser page:   paste `anonymize` below into the console, then run
//                          copy(JSON.stringify(anonymize([...document.scripts]
//                            .filter((s) => s.type === 'application/ld+json').map((s) => s.textContent))))
//                          and save the copied JSON; then: node scripts/recipe-snapshot.js --json saved.json

function anonymize(scripts) {
  const n = { ing: 0, step: 0, sec: 0, txt: 0, url: 0, tag: 0 };
  const DROP = new Set([
    'review', 'video', 'interactionStatistic', 'potentialAction', 'breadcrumb', 'itemReviewed', 'sameAs',
    'publishingPrinciples', 'nutrition', 'reviewedBy', 'logo',
  ]);
  const link = (v) => {
    n.url++;
    const ext = (v.match(/\.(jpe?g|png|webp|gif)(\?|$)/i) || [])[1];
    const frag = v.includes('#') ? '#' + v.split('#').pop().replace(/[^\w-]/g, '') : '';
    return 'https://example.com/' + (ext ? 'img' + n.url + '.' + ext.toLowerCase() : 'page' + n.url) + frag;
  };
  const walk = (v, key, type) => {
    if (Array.isArray(v)) return v.map((x) => walk(x, key, type));
    if (v && typeof v === 'object') {
      const t = [].concat(v['@type'] || []).join(',');
      const o = {};
      for (const k of Object.keys(v)) if (!DROP.has(k)) o[k] = walk(v[k], k, t);
      return o;
    }
    if (typeof v !== 'string') return v;
    if (key === '@type' || key === '@context') return v;
    if (/^P(\d|T)/.test(v) && /time|duration/i.test(key || '')) return v;
    if (/^(https?:)?\/\//.test(v)) return link(v);
    if (key === '@id') return v.startsWith('#') ? v : 'id' + ++n.url;
    if (key === 'recipeIngredient' || key === 'ingredients') {
      n.ing++;
      return n.ing % 3 === 0 ? 'Salt to taste' : (n.ing % 2 ? '2 cups' : '1 tbsp') + ' ingredient ' + n.ing;
    }
    if (key === 'recipeYield' || key === 'yield') {
      const d = v.match(/\d+/);
      return (d ? d[0] : '4') + (/[a-z]/i.test(v) ? ' servings' : '');
    }
    if (key === 'recipeInstructions' || (key === 'text' && /HowTo(Step|Direction|Tip)/.test(type))) {
      return v.split(/\n+/).filter((s) => s.trim()).map(() => 'Synthetic step ' + ++n.step + '.').join('\n');
    }
    if (key === 'name' && /HowToSection|ItemList/.test(type)) return 'Section ' + ++n.sec;
    if (key === 'name' && /HowTo(Step|Direction)/.test(type)) return 'Step name ' + n.step;
    if (key === 'name' && /Recipe/.test(type)) return 'Synthetic recipe title';
    if (/^(keywords|recipeCategory|recipeCuisine)$/.test(key)) return v.split(',').map(() => 'tag' + ++n.tag).join(', ');
    if (/(Content|calories|Value|Count|Rating)$/.test(key)) return v.replace(/[^\d.\s]+/g, 'x');
    if (/^(datePublished|dateModified|uploadDate)$/.test(key)) return '2026-01-01T00:00:00+00:00';
    // Plain-text times ("20 minutes") are a format, not content: keep them.
    if (/time$/i.test(key || '') && /^\d+\s*(min|minutes?|hours?|hrs?)\b/i.test(v)) return v;
    return 'Synthetic text ' + ++n.txt;
  };
  return scripts
    .map((t) => {
      try {
        return walk(JSON.parse(t.trim()), null, '');
      } catch (e) {
        return null;
      }
    })
    .filter(Boolean);
}

function page(ld) {
  const body = ld
    .map((o) => `<script type="application/ld+json">\n${JSON.stringify(o, null, 1)}\n</script>`)
    .join('\n');
  return '<!doctype html>\n<html><head>\n<title>Structure snapshot</title>\n' +
    '<meta property="og:title" content="Synthetic recipe title">\n' +
    '<meta property="og:image" content="https://example.com/og.jpg">\n' +
    `${body}\n</head><body><p>Synthetic page body.</p></body></html>\n`;
}

if (typeof module !== 'undefined' && require.main === module) {
  const fs = require('fs');
  const args = process.argv.slice(2);
  if (args[0] === '--json') {
    process.stdout.write(page(JSON.parse(fs.readFileSync(args[1], 'utf8'))));
  } else if (args[0]) {
    const html = fs.readFileSync(args[0], 'utf8');
    const scripts = [...html.matchAll(/<script[^>]*type=["']?application\/ld\+json["']?[^>]*>([\s\S]*?)<\/script>/gi)]
      .map((m) => m[1]);
    process.stdout.write(page(anonymize(scripts)));
  } else {
    console.error('usage: node scripts/recipe-snapshot.js page.html | --json anonymized.json');
    process.exit(2);
  }
}
