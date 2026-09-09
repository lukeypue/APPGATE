from .models import SearchPlan
from .safety import SafetyPolicy

class SearchPlanner:
    def __init__(self, registry):
        self.registry = registry
        self.safety = SafetyPolicy()

    def plan(self, query: str, requested_sites: list[str] | None=None):
        self.safety.validate_query(query)
        sites = self.registry.list_sites()
        selected = ['general_web']
        if requested_sites:
            selected += [s for s in requested_sites if s != 'general_web' and self.registry.get_site(s)]
        else:
            q = query.lower()
            for s in sites:
                if s.site_id == 'general_web': continue
                aliases = {s.site_id.replace('_',' '), s.name.lower(), s.domain.split('.')[0]}
                if any(a in q for a in aliases): selected.append(s.site_id)
            if len(selected) == 1:
                selected += [s.site_id for s in sites if s.site_id in {'ksl','autotrader','ebay','craigslist','offerup'}]
        return SearchPlan(query, list(dict.fromkeys(selected)))
