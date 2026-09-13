import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

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
const challenge = /captcha|verify you are human|security check|checkpoint|unusual traffic|confirm your identity|access denied/i;
const email = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/ig;
const phone = /(?<!\d)(?:\+?1[-.\s]?)?(?:\(?\d{3}\)?[-.\s]?)\d{3}[-.\s]?\d{4}(?!\d)/g;
const secret = /\b(cookie|session|token|password|authorization|bearer)\b\s*[:=]?\s*[^\s,;]*/ig;
const clean = s => String(s || '').replace(email,'[redacted]').replace(phone,'[redacted]').replace(secret,'[redacted]').replace(/\s+/g,' ').trim().slice(0,180);
const b64 = s => Buffer.from(String(s),'utf8').toString('base64url');
const hostAllowed = (hostname, hosts) => hosts.some(h => hostname === h || hostname.endsWith('.' + h) || h.endsWith('.' + hostname));

function pageType(url,title,labels){
  const t = `${url} ${title} ${labels.join(' ')}`.toLowerCase();
  if (/captcha|checkpoint|security check/.test(t)) return 'CHALLENGE';
  if (/login|sign.?in/.test(t)) return 'LOGIN';
  if (/search|results|listings|inventory|cars for sale/.test(t)) return 'RESULTS';
  if (/detail|vehicle|listing|item|product/.test(t)) return 'DETAIL';
  if (/category|browse|shop by/.test(t)) return 'CATEGORY';
  return 'HOME';
}

function safeLink(label, href, hosts){
  if (!href || dangerous.test(label) || dangerous.test(href)) return false;
  let u; try { u = new URL(href); } catch { return false; }
  if (!['http:','https:'].includes(u.protocol) || !hostAllowed(u.hostname.toLowerCase(), hosts)) return false;
  if (/logout|signout|delete|remove|checkout|cart|payment|messages?|compose|settings|account/i.test(u.pathname + u.search)) return false;
  return true;
}

const browser = await chromium.launch({ headless: true });
const summary = [];
try {
  for (const [key,entry,hosts] of sites) {
    const context = await browser.newContext({ javaScriptEnabled: true, locale: 'en-US' });
    const page = await context.newPage();
    const queue = [{ url: entry, from: null, label: 'ENTRY' }];
    const seen = new Set();
    const nodes = [];
    const edges = [];
    const boundaries = [];
    const maxPages = key === 'facebook_marketplace' ? 3 : 8;

    while (queue.length && seen.size < maxPages) {
      const item = queue.shift();
      let u; try { u = new URL(item.url); } catch { continue; }
      const canonical = `${u.protocol}//${u.hostname}${u.pathname}`.replace(/\/$/,'') || item.url;
      if (seen.has(canonical) || !hostAllowed(u.hostname.toLowerCase(), hosts)) continue;
      seen.add(canonical);
      try {
        await page.goto(item.url, { waitUntil: 'domcontentloaded', timeout: 25000 });
        await page.waitForTimeout(1200);
        const title = clean(await page.title());
        const visible = clean((await page.locator('body').innerText({ timeout: 4000 }).catch(()=>'' )).slice(0,12000));
        if (challenge.test(`${title} ${visible}`)) {
          boundaries.push({ route: u.pathname || '/', reason: 'HUMAN_OR_AUTH_REQUIRED' });
          continue;
        }
        const controls = await page.locator('a[href],button,input,[role="button"],[role="link"],[role="searchbox"]').evaluateAll(els => els.slice(0,350).map((el,i) => ({
          tag: el.tagName.toLowerCase(),
          role: el.getAttribute('role') || '',
          label: (el.innerText || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('name') || '').replace(/\s+/g,' ').trim().slice(0,160),
          href: el.href || el.getAttribute('href') || '',
          type: el.getAttribute('type') || '',
          i
        })));
        const labels = controls.map(c => clean(c.label)).filter(Boolean);
        const id = `n${nodes.length + 1}`;
        nodes.push({ id, pageType: pageType(page.url(), title, labels), route: clean(new URL(page.url()).pathname || '/'), summary: title });
        if (item.from) edges.push({ from: item.from, to: id, action: 'NAVIGATE', label: clean(item.label), confidence: 0.70, verified: true });

        for (const c of controls) {
          const label = clean(c.label);
          const href = c.href;
          if (!safeLink(label, href, hosts)) continue;
          let dest; try { dest = new URL(href, page.url()); } catch { continue; }
          const destCanonical = `${dest.protocol}//${dest.hostname}${dest.pathname}`.replace(/\/$/,'');
          if (!seen.has(destCanonical) && queue.length < 60) queue.push({ url: dest.href, from: id, label: label || 'link' });
        }
      } catch (err) {
        boundaries.push({ route: u.pathname || '/', reason: `UNRESOLVED:${clean(err?.name || 'error')}` });
      }
    }

    const discovered = Math.max(1, nodes.length + boundaries.length);
    const coverage = Math.min(100, Math.round((nodes.length / discovered) * 100));
    const readiness = nodes.length >= 5 ? 'SEARCH_READY' : nodes.length ? 'LEARNING' : 'UNMAPPED';
    const domain = hosts[0];
    const lines = [`SITEBRAIN|1|${b64(domain)}|${Date.now()}|${coverage}|${b64(readiness)}`];
    for (const h of hosts) lines.push(`HOST|${b64(h)}`);
    for (const n of nodes) lines.push(`NODE|${b64(n.id)}|${b64(n.pageType)}|${b64(n.route)}|${b64(n.summary)}`);
    for (const e of edges) lines.push(`EDGE|${b64(e.from)}|${b64(e.to)}|${b64(e.action)}|${b64(e.label)}|${e.confidence}|${e.verified}`);
    for (const b of boundaries) lines.push(`BOUNDARY|${b64(clean(b.route))}|${b64(clean(b.reason))}`);
    fs.writeFileSync(path.join(outDir, `${key}.sbp`), lines.join('\n') + '\n');
    summary.push({ key, entry, pages: nodes.length, verifiedTransitions: edges.length, boundaries: boundaries.length, coverage, readiness });
    await context.close();
    await new Promise(r => setTimeout(r, 1200));
  }
} finally {
  await browser.close();
}
fs.writeFileSync(path.join(outDir,'summary.json'), JSON.stringify({ generatedAt: new Date().toISOString(), sites: summary }, null, 2));
console.log(JSON.stringify(summary, null, 2));
