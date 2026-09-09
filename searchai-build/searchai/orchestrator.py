from __future__ import annotations
import uuid
from .browser import BrowserDriver, BrowserUnavailable
from .normalize import dedupe_results

class SearchOrchestrator:
    def __init__(self, store, registry, planner, learning, profile_dir):
        self.store, self.registry, self.planner, self.learning = store, registry, planner, learning
        self.browser = BrowserDriver(profile_dir)

    async def search(self, query: str, requested_sites=None):
        plan = self.planner.plan(query, requested_sites)
        task_id = str(uuid.uuid4())
        source_status=[]; results=[]; overall='complete'
        self.store.save_task(task_id, query, 'running', {'source_status':[], 'results':[]})
        for site_id in plan.site_ids:
            site = self.registry.get_site(site_id)
            if not site or not site.enabled: continue
            try:
                outcome = await self.browser.search_site(site, query)
                status = outcome['status']
                if status == 'verification_required': overall='needs_help'
                results.extend(outcome.get('results',[]))
                source_status.append({'site_id':site_id,'name':site.name,'status':status,'message':outcome.get('message','')})
            except BrowserUnavailable as e:
                overall = 'needs_help' if overall != 'needs_help' else overall
                source_status.append({'site_id':site_id,'name':site.name,'status':'setup_needed','message':str(e)})
            except Exception as e:
                self.learning.observe_failure(site_id, {'reason':type(e).__name__, 'selectors':{}, 'url':site.domain})
                source_status.append({'site_id':site_id,'name':site.name,'status':'learning','message':'This site needs a little learning. SearchAI saved what changed so a validated site update can improve future searches.'})
        payload={'source_status':source_status,'results':dedupe_results(results)}
        self.store.save_task(task_id, query, overall, payload)
        return {'id':task_id,'query':query,'status':overall,**payload}
