import test from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyPageBoundary,
  classifyRouteBoundary,
  classifySemanticPageType,
  scoreDiscoveryLink,
  calculatePublicCoverage,
  isSafeSearchControl,
  searchProbeForSite
} from './scout-policy.mjs';

test('access-denied and challenge pages are protected boundaries', () => {
  assert.equal(classifyPageBoundary('Access to this page has been denied.', ''), 'ACCESS_BLOCKED');
  assert.equal(classifyPageBoundary('Attention Required! | Cloudflare', 'Verify you are human'), 'HUMAN_VERIFICATION_REQUIRED');
  assert.equal(classifyPageBoundary('Error Page | eBay', 'Something went wrong'), 'SITE_ERROR');
});

test('login-only marketplace page is auth required', () => {
  assert.equal(
    classifyPageBoundary('Facebook Marketplace', 'Log in to Facebook to continue to Marketplace'),
    'AUTH_REQUIRED'
  );
  assert.equal(classifyRouteBoundary('https://www.cargurus.com/Cars/myAccount/saved-listings'), 'AUTH_REQUIRED');
});

test('known listing routes are classified as detail pages', () => {
  assert.equal(classifySemanticPageType('https://sfbay.craigslist.org/sfc/cto/d/example/123.html', '2018 Ford Expedition', []), 'DETAIL');
  assert.equal(classifySemanticPageType('https://www.carmax.com/car/26012345', '2021 Ford Expedition', []), 'DETAIL');
  assert.equal(classifySemanticPageType('https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?listingId=12345', 'Ford Expedition', []), 'DETAIL');
  assert.equal(classifySemanticPageType('https://offerup.com/item/detail/abc', 'Bicycle', []), 'DETAIL');
});

test('search result and category routes remain distinct from detail pages', () => {
  assert.equal(classifySemanticPageType('https://www.cargurus.com/search?make=Ford', 'Used Cars', ['Filter', 'Sort']), 'RESULTS');
  assert.equal(classifySemanticPageType('https://www.carmax.com/cars/suvs', 'Used SUVs', ['Shop SUVs']), 'CATEGORY');
});

test('marketplace discovery favors search structure over legal and editorial links', () => {
  const search = scoreDiscoveryLink('Cars for Sale', 'https://example.com/cars');
  const filter = scoreDiscoveryLink('Filter results', 'https://example.com/cars?make=ford');
  const privacy = scoreDiscoveryLink('Privacy Policy', 'https://example.com/privacy');
  const login = scoreDiscoveryLink('Log in', 'https://example.com/login');
  const prohibited = scoreDiscoveryLink('prohibited items', 'https://example.com/about/prohibited');
  const research = scoreDiscoveryLink('Research', 'https://example.com/research');
  assert.ok(search > privacy);
  assert.ok(filter > privacy);
  assert.ok(privacy <= 0);
  assert.ok(login <= 0);
  assert.ok(prohibited <= 0);
  assert.ok(research <= 0);
});

test('listing-looking links get strong discovery priority', () => {
  const carmax = scoreDiscoveryLink('2021 Ford Expedition', 'https://www.carmax.com/car/26012345');
  const cargurus = scoreDiscoveryLink('2020 Ford Expedition XLT', 'https://www.cargurus.com/Cars/inventorylisting/viewDetailsFilterViewInventoryListing.action?listingId=12345');
  const craigslist = scoreDiscoveryLink('2018 Ford Expedition', 'https://sfbay.craigslist.org/sfc/cto/d/example/123.html');
  assert.ok(carmax >= 10);
  assert.ok(cargurus >= 10);
  assert.ok(craigslist >= 10);
});

test('only real search controls may be submitted by the scout', () => {
  assert.equal(isSafeSearchControl({ tag: 'input', type: 'search', role: '', label: 'Search cars', name: 'q' }), true);
  assert.equal(isSafeSearchControl({ tag: 'input', type: 'text', role: 'searchbox', label: 'Search', name: 'query' }), true);
  assert.equal(isSafeSearchControl({ tag: 'input', type: 'password', role: '', label: 'Password', name: 'password' }), false);
  assert.equal(isSafeSearchControl({ tag: 'input', type: 'email', role: '', label: 'Email', name: 'email' }), false);
  assert.equal(isSafeSearchControl({ tag: 'textarea', type: '', role: '', label: 'Message seller', name: 'message' }), false);
});

test('probe queries are benign and site appropriate', () => {
  assert.equal(searchProbeForSite('carmax'), 'Ford Expedition');
  assert.equal(searchProbeForSite('cargurus'), 'Ford Expedition');
  assert.equal(searchProbeForSite('offerup'), 'bicycle');
  assert.equal(searchProbeForSite('craigslist'), 'car');
});

test('blocked or empty sites cannot report one hundred percent learned', () => {
  assert.equal(calculatePublicCoverage({ nodes: 0, verifiedTransitions: 0, boundaries: 1, capabilities: [] }), 0);
  assert.equal(calculatePublicCoverage({ nodes: 1, verifiedTransitions: 0, boundaries: 0, capabilities: [] }), 0);
});

test('one hundred percent requires a minimum viable search structure', () => {
  const incomplete = calculatePublicCoverage({
    nodes: 4,
    verifiedTransitions: 3,
    boundaries: 0,
    capabilities: ['CATEGORY', 'RESULTS']
  });
  assert.ok(incomplete < 100);

  const complete = calculatePublicCoverage({
    nodes: 5,
    verifiedTransitions: 5,
    boundaries: 0,
    capabilities: ['SEARCH', 'CATEGORY', 'RESULTS', 'DETAIL']
  });
  assert.equal(complete, 100);
});
