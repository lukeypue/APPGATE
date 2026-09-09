from datetime import datetime, timedelta, timezone

class UpdateService:
    def __init__(self, store, registry): self.store, self.registry = store, registry

    def should_check(self, policy: str, now=None, last_checked=None):
        now = now or datetime.now(timezone.utc)
        if policy == 'every_launch': return True
        if policy == 'manual': return False
        if last_checked is None: return True
        age = now - last_checked
        if policy == 'daily': return age >= timedelta(days=1)
        if policy == 'weekly': return age >= timedelta(days=7)
        return False

    def apply_catalog(self, catalog: dict):
        current = int(self.store.get_setting('catalog_version','0'))
        version = int(catalog.get('version',0))
        if version <= current: return {'applied':False,'version':current,'sites_updated':0}
        for site in catalog.get('sites',[]): self.registry.upsert_site(site)
        self.store.set_setting('catalog_version', str(version))
        self.store.set_setting('last_update_check', datetime.now(timezone.utc).isoformat())
        return {'applied':True,'version':version,'sites_updated':len(catalog.get('sites',[]))}

    def check(self, catalog: dict): return self.apply_catalog(catalog)
