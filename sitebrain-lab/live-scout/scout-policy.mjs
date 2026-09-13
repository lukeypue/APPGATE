const accessBlocked = /access (?:to this page )?has been denied|access denied|request blocked|forbidden/i;
const humanChallenge = /captcha|verify you are human|security check|checkpoint|unusual traffic|confirm your identity|attention required.*cloudflare/i;
const authRequired = /\b(?:log in|login|sign in|sign-in)\b.*\b(?:continue|marketplace|account)\b|\b(?:marketplace|account)\b.*\b(?:log in|login|sign in)\b/i;
const siteError = /\berror page\b|something went wrong|temporarily unavailable|page (?:is )?unavailable/i;

const irrelevant = /privacy|terms|legal|cookie policy|accessibility|careers?|jobs?|hiring|press|investor|advertis|help center|support|safety|community guidelines|sell my car|financ|insurance|dealer sign.?in/i;
const consequential = /\b(?:buy|bid|checkout|pay|purchase|message|contact|send|post|publish|upload|delete|remove|follow|subscribe|account|profile|sign\s?in|log\s?in|login|register|create account)\b/i;
const strongDiscovery = /search|results?|filter|sort|next|previous|pagination|browse|shop|marketplace|categor|inventory|cars? for sale|vehicles? for sale|listings?|items?|details?/i;

export function classifyPageBoundary(title = '', visibleText = '') {
  const text = `${title} ${visibleText}`.replace(/\s+/g, ' ').trim();
  if (accessBlocked.test(text)) return 'ACCESS_BLOCKED';
  if (humanChallenge.test(text)) return 'HUMAN_VERIFICATION_REQUIRED';
  if (authRequired.test(text)) return 'AUTH_REQUIRED';
  if (siteError.test(text)) return 'SITE_ERROR';
  return null;
}

export function scoreDiscoveryLink(label = '', href = '') {
  const text = `${label} ${href}`;
  if (consequential.test(text) || irrelevant.test(text)) return -100;
  let score = 0;
  if (strongDiscovery.test(label)) score += 8;
  if (strongDiscovery.test(href)) score += 5;
  if (/\/search|\/browse|\/marketplace|\/cars|\/vehicles|\/inventory|\/listing|\/item/i.test(href)) score += 4;
  if (/page=|sort=|filter|make=|model=|category/i.test(href)) score += 3;
  if (!label.trim()) score -= 2;
  return score;
}

export function calculatePublicCoverage({ nodes, verifiedTransitions, boundaries, capabilities }) {
  const unique = new Set(capabilities || []);
  if (nodes <= 0 || verifiedTransitions <= 0 || unique.size === 0) return 0;

  const minimum = ['SEARCH', 'CATEGORY', 'RESULTS', 'DETAIL'];
  const complete = minimum.every(capability => unique.has(capability));
  if (complete && nodes >= 5 && verifiedTransitions >= 4 && boundaries === 0) return 100;

  const capabilityScore = Math.min(60, unique.size * 15);
  const transitionScore = Math.min(25, verifiedTransitions * 5);
  const breadthScore = Math.min(10, Math.max(0, nodes - 1) * 2);
  const boundaryPenalty = Math.min(20, Math.max(0, boundaries) * 5);
  return Math.max(0, Math.min(95, capabilityScore + transitionScore + breadthScore - boundaryPenalty));
}
