import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import {
  classifyPageBoundary,
  classifyRouteBoundary,
  scoreDiscoveryLink,
  calculatePublicCoverage,
  isSafeSearchControl,
  searchProbeForSite
} from './scout-policy.mjs';

const outDir = process.env.SITEBRAIN_OUT || path.resolve('out');
fs.mkdirSync(outDir, { recursive: true });

const sites = [
  ['ksl_cars','https://cars.ksl.com/',['cars.ksl.com','www.ksl.com']],
  ['ebay','https://www.ebay.com/',['ebay.com','www.ebay.com']],
  ['craigslist','https://www.craigslist.org/',['craigslist.org','www.craigslist.org']],
  ['autotrader','https://www.autotrader.com/',['autotrader.com','www.autotrader.com']],
  ['cars_com','https://www.cars.com/',['cars.com','www.cars.com']],
  ['cargurus','https://www.cargurus.com/',['cargurus.com','www.cargurus.com']],
  ['edmunds','https://www.edmunds.com/',['edmunds.com','www.edmunds.com']],
  ['truecar','https://www.truecar.com/',['truecar.com','www.truecar.com']],
  ['carmax','https://www.carmax.com/',['carmax.com','www.carmax.com']],
  ['offerup','https://offerup.com/',['offerup.com','www.offerup.com']],
  ['facebook_marketplace','https://www.facebook.com/marketplace/',['facebook.com','www.facebook.com']]
];

const dangerous = /\b(buy|bid|checkout|pay|purchase|message|contact|send|post|publish|upload|delete|remove|follow|subscribe|account|profile|sign\s?in|log\s?in|login|register|create account)\b/i;
const email = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/ig;
const phone = /(?<!\d)(?:\+?1[-.\s]?)?(?:\(?\d{3}\)?[-.\s]?)\d{3}[-.\s]?\d{4}(?!\d)/g;
const secret = /\b(cookie|session|token|password|authorization|bearer)\b\s*[:=]?\s*[^\s,;]*/ig;
const clean = s => String(s || '').replace(email,'[redacted]').replace(phone,'[redacted]').replace(secret,'[redacted]').replace(/\s+/g,' ').trim().slice(0,180);
const b64 = s => Buffer.from(String(s),'utf8').toString('base64url');
const hostAllowed = (hostname, hosts) => hosts.some(h => hostname === h || hostname.endsWith('.' + h) || h.endsWith('.' + hostname));
const controlsSelector = 'a[href],button,input,[role="button"],[role="link"],[role="searchbox"]';

function pageType(url,title,labels){
  const words = `${title} ${labels.join(' ')}`.toLowerCase();
  const route = url.toLowerCase();
  if (/captcha|checkpoint|security check/.test(words)) return 'CHALLENGE';
  if (/\b(?:login|sign.?in)\b/.test(words)) return 'LOGIN';
  if (/\/search(?:\/|\?|$)|[?&](?:page|sort|filter|make|model)=/.test(route) || /\b(?:results|listings|inventory)\b|cars for sale/.test(words)) return 'RESULTS';
  if (/\/item(?:\/|$)|\/listing(?:\/|$)|\/detail(?:\/|$)/.test(route) || /\b(?:vehicle|listing|product) details?\b/.test(words)) return 'DETAIL';
  if (/\/category(?:\/|$)|\/browse(?:\/|$)|\/marketplace(?:\/|$)/.test(route) || /\b(?:category|browse|shop by|marketplace)\b/.test(words)) return 'CATEGORY';
  return 'HOME';
}

function safeLink(label, href, hosts){
  if (!href || dangerous.test(label) || dangerous.test(href)) return false;
  let u; try { u = new URL(href); } catch { return false; }
  if (!['http:','https:'].includes(u.protocol) || !hostAllowed(u.hostname.toLowerCase(), hosts)) return false;
  if (classifyRouteBoundary(u.href)) return false;
  return scoreDiscoveryLink(label, href) > 0;
}

function addCapabilities(capabilities, type, controls, url){
  if (type === 'RESULTS') capabilities.add('RESULTS');
  if (type === 'DETAIL') capabilities.add('DETAIL');
  if (type === 'CATEGORY') capabilities.add('CATEGORY');
  const combined = controls.map(c => `${c.role} ${c.type} ${c.label} ${c.name || ''}`).join(' ').toLowerCase();
  if (/searchbox|type.?search|\bsearch\b/.test(combined)) capabilities.add('SEARCH');
  if (/\bfilter\b/.test(combined) || /[?&]filter=/i.test(url)) capabilities.add('FILTER');
  if (/\bsort\b/.test(combined) || /[?&]sort=/i.test(url)) capabilities.add('SORT');
  if (/\bnext\b|\bprevious\b|pagination|page\s*\d/.test(combined) || /[?&]page=/i.test(url)) capabilities.add('PAGINATION');
  if (/\bcategory\b|\bcategories\b|\bbrowse\b|shop by|\bmarketplace\b/.test(combined)) capabilities.add('CATEGORY');
}

async function trySafeSearchProbe(context, sourcePage, sourceUrl, controls, key, hosts) {
  const searchControl = controls.find(isSafeSearchControl);
  if (!searchControl) return null;
  const probePage = await context.newPage();
  try {
    await probePage.goto(sourceUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await probePage.waitForTimeout(700);
    const locator = probePage.locator(controlsSelector).nth(searchControl.i);
    if (!(await locator.isVisible().catch(() => false))) return null;
    const query = searchProbeForSite(key);
    await locator.fill(query, { timeout: 3000 });
    await locator.press('Enter', { timeout: 3000 });
    await probePage.waitForLoadState('domcontentloaded', { timeout: 8000 }).catch(() => {});
    await probePage.waitForTimeout(1000);
    const probeUrl = probePage.url();
    let parsed; try { parsed = new URL(probeUrl); } catch { return null; }
    if (!hostAllowed(parsed.hostname.toLowerCase(), hosts)) return null;
    if (classifyRouteBoundary(probeUrl)) return null;
    const title = clean(await probePage.title());
    const visible = clean((await probePage.locator('body').innerText({ timeout: 3000 }).catch(() => '')).slice(0,12000));
    if (classifyPageBoundary(title, visible)) return null;
    const sourceCanonical = new URL(sourcePage.url());
    if (parsed.href === sourceCanonical.href) return null;
    return { url: probeUrl, label: `SEARCH:${query}`, score: 100 };
  } catch {
    return null;
  } finally {
    await probePage.close().catch(() => {});
  }
}

const browser = await chromium.launch({ headless: true });
const summary = [];
try {
  for (const [key,entry,hosts] of sites) {
    const context = await browser.newContext({ javaScriptEnabled: true, locale: 'en-US' });
    const page = await context.newPage();
    const queue = [{ url: entry, from: null, label: 'ENTRY', score: 1000 }];
    const seen = new Set();
    const nodes = [];
    const edges = [];
    const boundaries = [];
    const capabilities = new Set();
    let searchProbeAttempted = false;
    const maxPages = key === 'facebook_marketplace' ? 3 : 14;

    while (queue.length && seen.size < maxPages) {
      queue.sort((a,b) => (b.score || 0) - (a.score || 0));
      const item = queue.shift();
      let u; try { u = new URL(item.url); } catch { continue; }
      const canonical = `${u.protocol}//${u.hostname}${u.pathname}${u.search}`.replace(/\/$/,'') || item.url;
      if (seen.has(canonical) || !hostAllowed(u.hostname.toLowerCase(), hosts)) continue;
      seen.add(canonical);
      try {
        await page.goto(item.url, { waitUntil: 'domcontentloaded', timeout: 25000 });
        await page.waitForTimeout(1200);
        const actualUrl = page.url();
        const routeBoundary = classifyRouteBoundary(actualUrl);
        if (routeBoundary) {
          boundaries.push({ route: new URL(actualUrl).pathname || '/', reason: routeBoundary });
          continue;
        }
        const title = clean(await page.title());
        const visible = clean((await page.locator('body').innerText({ timeout: 4000 }).catch(()=>'' )).slice(0,12000));
        const boundary = classifyPageBoundary(title, visible);
        if (boundary) {
          boundaries.push({ route: new URL(actualUrl).pathname || '/', reason: boundary });
          continue;
        }
        const controls = await page.locator(controlsSelector).evaluateAll(els => els.slice(0,350).map((el,i) => ({
          tag: el.tagName.toLowerCase(),
          role: el.getAttribute('role') || '',
          label: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('name') || '').replace(/\s+/g,' ').trim().slice(0,160),
          href: el.href || el.getAttribute('href') || '',
          type: el.getAttribute('type') || '',
          name: el.getAttribute('name') || '',
          i
        })));
        const labels = controls.map(c => clean(c.label)).filter(Boolean);
        const type = pageType(actualUrl, title, labels);
        addCapabilities(capabilities, type, controls, actualUrl);
        const id = `n${nodes.length + 1}`;
        nodes.push({ id, pageType: type, route: clean(new URL(actualUrl).pathname || '/'), summary: title });
        if (item.from) edges.push({ from: item.from, to: id, action: item.label?.startsWith('SEARCH:') ? 'SEARCH' : 'NAVIGATE', label: clean(item.label), confidence: 0.75, verified: true });

        if (!searchProbeAttempted) {
          searchProbeAttempted = true;
          const probe = await trySafeSearchProbe(context, page, actualUrl, controls, key, hosts);
          if (probe) {
            capabilities.add('SEARCH');
            queue.push({ ...probe, from: id });
          }
        }

        const candidates = [];
        for (const c of controls) {
          const label = clean(c.label);
          const href = c.href;
          if (!safeLink(label, href, hosts)) continue;
          let dest; try { dest = new URL(href, actualUrl); } catch { continue; }
          const destCanonical = `${dest.protocol}//${dest.hostname}${dest.pathname}${dest.search}`.replace(/\/$/,'');
          if (!seen.has(destCanonical)) {
            candidates.push({ url: dest.href, from: id, label: label || 'link', score: scoreDiscoveryLink(label, dest.href) });
          }
        }
        candidates.sort((a,b) => b.score - a.score);
        for (const candidate of candidates.slice(0,14)) {
          if (queue.length >= 80) break;
          queue.push(candidate);
        }
      } catch (err) {
        boundaries.push({ route: u.pathname || '/', reason: `UNRESOLVED:${clean(err?.name || 'error')}` });
      }
    }

    const coverage = calculatePublicCoverage({
      nodes: nodes.length,
      verifiedTransitions: edges.length,
      boundaries: boundaries.length,
      capabilities: [...capabilities]
    });
    const readiness = coverage === 100
      ? 'DEEP_SEARCH_READY'
      : coverage >= 45 && edges.length >= 2
        ? 'SEARCH_READY'
        : nodes.length
          ? 'LEARNING'
          : 'UNMAPPED';
    const domain = hosts[0];
    const lines = [`SITEBRAIN|1|${b64(domain)}|${Date.now()}|${coverage}|${b64(readiness)}`];
    for (const h of hosts) lines.push(`HOST|${b64(h)}`);
    for (const n of nodes) lines.push(`NODE|${b64(n.id)}|${b64(n.pageType)}|${b64(n.route)}|${b64(n.summary)}`);
    for (const e of edges) lines.push(`EDGE|${b64(e.from)}|${b64(e.to)}|${b64(e.action)}|${b64(e.label)}|${e.confidence}|${e.verified}`);
    for (const b of boundaries) lines.push(`BOUNDARY|${b64(clean(b.route))}|${b64(clean(b.reason))}`);
    fs.writeFileSync(path.join(outDir, `${key}.sbp`), lines.join('\n') + '\n');
    summary.push({
      key,
      entry,
      pages: nodes.length,
      verifiedTransitions: edges.length,
      boundaries: boundaries.length,
      capabilities: [...capabilities].sort(),
      coverage,
      readiness
    });
    await context.close();
    await new Promise(r => setTimeout(r, 1200));
  }
} finally {
  await browser.close();
}
fs.writeFileSync(path.join(outDir,'summary.json'), JSON.stringify({ generatedAt: new Date().toISOString(), sites: summary }, null, 2));
console.log(JSON.stringify(summary, null, 2));
