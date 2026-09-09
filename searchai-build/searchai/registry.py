from __future__ import annotations
import json
from .models import SiteDefinition, LearnedAdapter

DEFAULT_SITES = [
    ('general_web','General Web','bing.com','https://www.bing.com/search?q={query}','web'),
    ('ksl','KSL','ksl.com','https://www.ksl.com/search?search={query}','classifieds'),
    ('facebook_marketplace','Facebook Marketplace','facebook.com','https://www.facebook.com/marketplace/search/?query={query}','marketplace'),
    ('autotrader','Autotrader','autotrader.com','https://www.autotrader.com/cars-for-sale/all-cars/cars-between-0-and-100000?keywordPhrases={query}','automotive'),
    ('cargurus','CarGurus','cargurus.com','https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?zip=84101&entitySelectingHelper.selectedEntity=&keywords={query}','automotive'),
    ('zillow','Zillow','zillow.com','https://www.zillow.com/homes/{query}_rb/','real_estate'),
    ('redfin','Redfin','redfin.com','https://www.redfin.com/city/30749/UT/Salt-Lake-City/filter/remarks={query}','real_estate'),
    ('offerup','OfferUp','offerup.com','https://offerup.com/search?q={query}','marketplace'),
    ('craigslist','Craigslist','craigslist.org','https://www.craigslist.org/search/sss?query={query}','classifieds'),
    ('ebay','eBay','ebay.com','https://www.ebay.com/sch/i.html?_nkw={query}','commerce'),
    ('mercari','Mercari','mercari.com','https://www.mercari.com/search/?keyword={query}','commerce'),
    ('depop','Depop','depop.com','https://www.depop.com/search/?q={query}','commerce'),
    ('linkedin','LinkedIn','linkedin.com','https://www.linkedin.com/search/results/all/?keywords={query}','professional'),
    ('nextdoor','Nextdoor','nextdoor.com','https://nextdoor.com/search/?query={query}','local'),
    ('stubhub','StubHub','stubhub.com','https://www.stubhub.com/find/s/?q={query}','tickets'),
    ('seatgeek','SeatGeek','seatgeek.com','https://seatgeek.com/search?search={query}','tickets'),
]


class SiteRegistry:
    def __init__(self, store):
        self.store = store
        self._seed()

    def _seed(self):
        with self.store.connect() as c:
            for site_id, name, domain, search_url, category in DEFAULT_SITES:
                c.execute('INSERT OR IGNORE INTO sites(site_id,name,domain,search_url,enabled,category) VALUES(?,?,?,?,1,?)', (site_id,name,domain,search_url,category))

    def upsert_site(self, data: dict):
        with self.store.connect() as c:
            c.execute('''INSERT INTO sites(site_id,name,domain,search_url,enabled,category) VALUES(?,?,?,?,?,?)
            ON CONFLICT(site_id) DO UPDATE SET name=excluded.name,domain=excluded.domain,search_url=excluded.search_url,enabled=excluded.enabled,category=excluded.category''',
            (data['site_id'], data['name'], data['domain'], data['search_url'], int(data.get('enabled', True)), data.get('category','general')))

    def list_sites(self):
        with self.store.connect() as c:
            rows = c.execute('SELECT * FROM sites ORDER BY CASE WHEN site_id="general_web" THEN 0 ELSE 1 END, name').fetchall()
        return [SiteDefinition(r['site_id'],r['name'],r['domain'],r['search_url'],bool(r['enabled']),r['category']) for r in rows]

    def get_site(self, site_id: str):
        with self.store.connect() as c:
            r = c.execute('SELECT * FROM sites WHERE site_id=?', (site_id,)).fetchone()
        if not r: return None
        return SiteDefinition(r['site_id'],r['name'],r['domain'],r['search_url'],bool(r['enabled']),r['category'])

    def save_draft_adapter(self, site_id: str, recipe: dict, confidence: float=0.0):
        with self.store.connect() as c:
            version = c.execute('SELECT COALESCE(MAX(version),0)+1 v FROM adapters WHERE site_id=?', (site_id,)).fetchone()['v']
            cur = c.execute('INSERT INTO adapters(site_id,version,recipe,confidence,status) VALUES(?,?,?,?,?)', (site_id,version,json.dumps(recipe),confidence,'draft'))
            adapter_id = cur.lastrowid
        return self.get_adapter(adapter_id)

    def get_adapter(self, adapter_id: int):
        with self.store.connect() as c:
            r = c.execute('SELECT * FROM adapters WHERE id=?', (adapter_id,)).fetchone()
        return LearnedAdapter(r['id'],r['site_id'],r['version'],json.loads(r['recipe']),r['confidence'],r['status'])

    def promote_adapter(self, adapter_id: int, validation_score: float):
        if validation_score < 0.85:
            return False
        with self.store.connect() as c:
            c.execute('UPDATE adapters SET status="active", confidence=? WHERE id=?', (validation_score, adapter_id))
        return True
