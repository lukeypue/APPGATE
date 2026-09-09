import os
from fastapi.testclient import TestClient

os.environ['SEARCHAI_DATA_DIR'] = 'build-test-data'

from searchai.app import create_app


def test_health_and_source_registry():
    client = TestClient(create_app())
    health = client.get('/api/health')
    assert health.status_code == 200
    assert health.json()['status'] == 'ok'
    sites = client.get('/api/sites')
    assert sites.status_code == 200
    assert len(sites.json()) == 16


def test_home_ui_loads():
    client = TestClient(create_app())
    response = client.get('/')
    assert response.status_code == 200
    assert 'SearchAI' in response.text
    assert 'Search Everywhere' in response.text
