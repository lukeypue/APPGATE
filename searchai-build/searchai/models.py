from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
from typing import Any


class UpdatePolicy(str, Enum):
    EVERY_LAUNCH = 'every_launch'
    DAILY = 'daily'
    WEEKLY = 'weekly'
    MANUAL = 'manual'


@dataclass(slots=True)
class SiteDefinition:
    site_id: str
    name: str
    domain: str
    search_url: str
    enabled: bool = True
    category: str = 'general'


@dataclass(slots=True)
class LearnedAdapter:
    id: int
    site_id: str
    version: int
    recipe: dict[str, Any]
    confidence: float
    status: str


@dataclass(slots=True)
class SearchPlan:
    query: str
    site_ids: list[str]
