import re

class UnsafeRequestError(ValueError): pass

class SafetyPolicy:
    FORBIDDEN = [
        r'\bsubmit\s+payment\b', r'\bmake\s+(?:the\s+)?payment\b', r'\bbuy\s+this\b',
        r'\bplace\s+(?:a\s+)?bid\b', r'\bsend\s+(?:a\s+)?message\b', r'\bdelete\s+(?:my\s+)?account\b'
    ]
    def validate_query(self, query: str):
        q = query.lower()
        for pattern in self.FORBIDDEN:
            if re.search(pattern, q):
                raise UnsafeRequestError('SearchAI can search and compare, but sensitive actions need you to do them yourself.')
        return True
