from urllib.parse import urlsplit, urlunsplit
import re

def _clean_url(url: str):
    p = urlsplit(url.strip())
    return urlunsplit((p.scheme.lower() or 'https', p.netloc.lower(), p.path.rstrip('/'), '', ''))

def _price(value):
    if value is None: return None
    m = re.search(r'([\d,]+(?:\.\d+)?)', str(value))
    return float(m.group(1).replace(',','')) if m else None

def normalize_result(raw: dict, source: str):
    return {'title': str(raw.get('title','')).strip(), 'url': _clean_url(str(raw.get('url',''))), 'price': _price(raw.get('price')), 'snippet': str(raw.get('snippet','')).strip(), 'sources':[source]}

def dedupe_results(items: list[dict]):
    by = {}
    for item in items:
        key = item.get('url') or item.get('title','').lower()
        if key in by:
            by[key]['sources'] = list(dict.fromkeys(by[key]['sources'] + item.get('sources',[])))
            if by[key].get('price') is None and item.get('price') is not None: by[key]['price'] = item['price']
        else: by[key] = dict(item)
    return list(by.values())
