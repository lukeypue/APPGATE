from __future__ import annotations
import json
import sqlite3
from pathlib import Path
from typing import Any
from .models import UpdatePolicy


class Store:
    def __init__(self, path: str | Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def connect(self):
        conn = sqlite3.connect(self.path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self):
        with self.connect() as c:
            c.execute('PRAGMA journal_mode=WAL')
            c.executescript('''
            CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS sites(site_id TEXT PRIMARY KEY, name TEXT NOT NULL, domain TEXT NOT NULL, search_url TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, category TEXT NOT NULL DEFAULT 'general');
            CREATE TABLE IF NOT EXISTS adapters(id INTEGER PRIMARY KEY AUTOINCREMENT, site_id TEXT NOT NULL, version INTEGER NOT NULL, recipe TEXT NOT NULL, confidence REAL NOT NULL, status TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY, query TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            ''')
            c.execute('INSERT OR IGNORE INTO settings(key,value) VALUES(?,?)', ('update_policy', UpdatePolicy.DAILY.value))
            c.execute('INSERT OR IGNORE INTO settings(key,value) VALUES(?,?)', ('catalog_version', '1'))

    def get_setting(self, key: str, default: str | None = None):
        with self.connect() as c:
            row = c.execute('SELECT value FROM settings WHERE key=?', (key,)).fetchone()
            return row['value'] if row else default

    def set_setting(self, key: str, value: str):
        with self.connect() as c:
            c.execute('INSERT INTO settings(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value', (key, value))

    def save_task(self, task_id: str, query: str, status: str, payload: dict[str, Any]):
        with self.connect() as c:
            c.execute('INSERT OR REPLACE INTO tasks(id,query,status,payload) VALUES(?,?,?,?)', (task_id, query, status, json.dumps(payload)))

    def get_task(self, task_id: str):
        with self.connect() as c:
            row = c.execute('SELECT * FROM tasks WHERE id=?', (task_id,)).fetchone()
            if not row:
                return None
            payload = json.loads(row['payload'])
            payload.update({'id': row['id'], 'query': row['query'], 'status': row['status']})
            return payload
