from urllib.parse import urlsplit

class LearningEngine:
    SECRET_KEYS = {'cookies','cookie','token','session','session_id','password','authorization','headers'}
    def __init__(self, registry): self.registry = registry

    def sanitize(self, diagnostic: dict):
        out = {}
        for k,v in diagnostic.items():
            if k.lower() in self.SECRET_KEYS: continue
            if k == 'url':
                p = urlsplit(str(v)); out['url'] = f'{p.scheme}://{p.netloc}{p.path}'
            else: out[k] = v
        return out

    def observe_failure(self, site_id: str, diagnostic: dict):
        clean = self.sanitize(diagnostic)
        recipe = {'diagnostic': clean, 'selectors': clean.get('selectors',{}), 'learned_from':'structural_failure'}
        return self.registry.save_draft_adapter(site_id, recipe, confidence=0.25)

    def classify_page(self, text: str):
        t = text.lower()
        if any(x in t for x in ['verify you are human','captcha','security check','identity verification']): return 'verification_required'
        return 'readable'

    def validate_adapter(self, adapter_id: int, score: float):
        return self.registry.promote_adapter(adapter_id, score)
