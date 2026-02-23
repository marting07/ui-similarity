#!/usr/bin/env node

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

function tokenCount(text, regex) {
  const m = text.match(regex);
  return m ? m.length : 0;
}

function extract(logicCode) {
  const lower = logicCode.toLowerCase();
  const eventTypes = [];
  if (lower.includes('onclick') || lower.includes('(click)')) eventTypes.push('click');
  if (lower.includes('onchange') || lower.includes('(change)')) eventTypes.push('change');
  if (lower.includes('onsubmit') || lower.includes('(submit)')) eventTypes.push('submit');
  if (lower.includes('onkeydown') || lower.includes('(keydown)')) eventTypes.push('keydown');

  const interactionPatterns = [];
  if (lower.includes('usestate') && lower.includes('toggle')) interactionPatterns.push('click-toggle');
  if (lower.includes('filter(')) interactionPatterns.push('list-filter');
  if (lower.includes('form')) interactionPatterns.push('form');

  const statePatterns = [];
  if (lower.includes('usestate(') || lower.includes('usereducer(')) statePatterns.push('localState');
  if (lower.includes('ngrx')) statePatterns.push('ngrx');
  if (lower.includes('vuex')) statePatterns.push('vuex');

  const apiSignatures = new Set();
  const fetchRegex = /fetch([A-Za-z0-9_]*)/gi;
  let fm;
  while ((fm = fetchRegex.exec(logicCode)) !== null) {
    apiSignatures.add(`fetch${fm[1] || ''}`);
  }
  const axiosRegex = /axios\.([A-Za-z0-9_]+)/gi;
  let am;
  while ((am = axiosRegex.exec(logicCode)) !== null) {
    apiSignatures.add(`axios.${am[1]}`);
  }

  const cyclomatic = 1
    + tokenCount(logicCode, /if /gi)
    + tokenCount(logicCode, /else if/gi)
    + tokenCount(logicCode, /for /gi)
    + tokenCount(logicCode, /while /gi)
    + tokenCount(logicCode, /switch /gi);

  const dedupedEvents = Array.from(new Set(eventTypes));
  const dedupedInteractions = Array.from(new Set(interactionPatterns));
  const dedupedState = Array.from(new Set(statePatterns));
  const dedupedApis = Array.from(apiSignatures);

  const handlerCount = dedupedEvents.length;
  const apiCallCount = dedupedApis.length;
  const conditionalCount = tokenCount(logicCode, /if /gi) + tokenCount(logicCode, /switch /gi);

  return {
    status: 'ok',
    eventTypes: dedupedEvents,
    interactionPatterns: dedupedInteractions,
    statePatterns: dedupedState,
    apiSignatures: dedupedApis,
    cyclomatic,
    handlerCount,
    apiCallCount,
    conditionalCount,
  };
}

async function main() {
  try {
    const raw = await readStdin();
    const req = JSON.parse(raw || '{}');
    const logicCode = typeof req.logicCode === 'string' ? req.logicCode : '';
    process.stdout.write(JSON.stringify(extract(logicCode)));
  } catch (error) {
    process.stdout.write(JSON.stringify({ status: 'error', error: error instanceof Error ? error.message : String(error) }));
    process.exit(1);
  }
}

main();
