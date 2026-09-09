from __future__ import annotations
import json, os
from pathlib import Path
from datetime import datetime
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from .store import Store
from .registry import SiteRegistry
from .planner import SearchPlanner
from .learning import LearningEngine
from .updates import UpdateService
from .orchestrator import SearchOrchestrator
from .safety import UnsafeRequestError
from .packaging import resource_root

class SearchRequest(BaseModel):
    query: str
    sites: list[str] | None = None
class SettingsRequest(BaseModel):
    update_policy: str


def create_app():
    base = Path(os.getenv('SEARCHAI_DATA_DIR', Path.cwd()/'data'))
    base.mkdir(parents=True, exist_ok=True)
    store = Store(base/'searchai.db'); registry = SiteRegistry(store)
    planner = SearchPlanner(registry); learning = LearningEngine(registry)
    updates = UpdateService(store, registry)
    catalog_path = resource_root()/'updates'/'catalog.json'
    policy = store.get_setting('update_policy', 'daily')
    last_raw = store.get_setting('last_update_check')
    try:
        last_checked = datetime.fromisoformat(last_raw) if last_raw else None
    except ValueError:
        last_checked = None
    if updates.should_check(policy, last_checked=last_checked):
        catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
        updates.check(catalog)
    orchestrator = SearchOrchestrator(store, registry, planner, learning, base/'profiles')
    app = FastAPI(title='SearchAI')
    static_dir = resource_root()/'static'
    app.mount('/static', StaticFiles(directory=static_dir), name='static')

    @app.get('/')
    def home(): return FileResponse(static_dir/'index.html')
    @app.get('/api/health')
    def health(): return {'status':'ok','name':'SearchAI','catalog_version':store.get_setting('catalog_version','1')}
    @app.get('/api/settings')
    def settings(): return {'update_policy':store.get_setting('update_policy','daily')}
    @app.put('/api/settings')
    def set_settings(req: SettingsRequest):
        if req.update_policy not in {'every_launch','daily','weekly','manual'}: raise HTTPException(400,'Unknown update policy')
        store.set_setting('update_policy', req.update_policy); return {'update_policy':req.update_policy}
    @app.get('/api/sites')
    def sites(): return [s.__dict__ if hasattr(s,'__dict__') else {'site_id':s.site_id,'name':s.name,'domain':s.domain,'search_url':s.search_url,'enabled':s.enabled,'category':s.category} for s in registry.list_sites()]
    @app.post('/api/search')
    async def search(req: SearchRequest):
        try: return await orchestrator.search(req.query, req.sites)
        except UnsafeRequestError as e: raise HTTPException(400,str(e))
    @app.get('/api/tasks/{task_id}')
    def task(task_id: str):
        data=store.get_task(task_id)
        if not data: raise HTTPException(404,'Task not found')
        return data
    @app.post('/api/verification/{task_id}/continue')
    async def cont(task_id: str):
        task=store.get_task(task_id)
        if not task: raise HTTPException(404,'Task not found')
        return await orchestrator.search(task['query'])
    @app.post('/api/updates/check')
    def update_check():
        catalog=json.loads(catalog_path.read_text(encoding='utf-8'))
        return updates.check(catalog)
    return app

app = create_app()
