from __future__ import annotations
from pathlib import Path
from urllib.parse import quote_plus
from .normalize import normalize_result

class BrowserUnavailable(RuntimeError): pass

class BrowserDriver:
    def __init__(self, profile_dir: str | Path):
        self.profile_dir = Path(profile_dir)
        self.profile_dir.mkdir(parents=True, exist_ok=True)

    async def search_site(self, site, query: str, max_results: int=12):
        try:
            from playwright.async_api import async_playwright
        except Exception as e:
            raise BrowserUnavailable('Playwright is not installed yet. Run setup.bat once.') from e
        url = site.search_url.format(query=quote_plus(query))
        async with async_playwright() as p:
            try:
                context = await p.chromium.launch_persistent_context(str(self.profile_dir / site.site_id), headless=True)
            except Exception as e:
                raise BrowserUnavailable('The browser engine is not installed yet. Run setup.bat to finish browser setup.') from e
            page = context.pages[0] if context.pages else await context.new_page()
            await page.goto(url, wait_until='domcontentloaded', timeout=30000)
            body_text = (await page.locator('body').inner_text())[:6000]
            lower = body_text.lower()
            if any(x in lower for x in ['verify you are human','captcha','security check']):
                await context.close()
                return {'status':'verification_required','results':[],'url':url,'message':'This site needs a quick human verification before SearchAI can continue.'}
            anchors = await page.locator('a[href]').evaluate_all('''els => els.slice(0,120).map(a => ({title:(a.innerText||a.textContent||'').trim(), url:a.href, snippet:''})).filter(x => x.title && x.url)''')
            await context.close()
            results=[]
            for a in anchors:
                if site.domain in a['url'] or site.site_id == 'general_web':
                    results.append(normalize_result(a, site.site_id))
                if len(results)>=max_results: break
            return {'status':'complete','results':results,'url':url,'message':''}

    async def resume(self, site, query: str):
        return await self.search_site(site, query)
