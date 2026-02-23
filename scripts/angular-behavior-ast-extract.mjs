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
  const events = [];
  if (/onClick|\(click\)/i.test(logicCode)) events.push('click');
  if (/onChange|\(change\)/i.test(logicCode)) events.push('change');
  if (/onSubmit|\(submit\)/i.test(logicCode)) events.push('submit');
  if (/onKeyDown|\(keydown\)/i.test(logicCode)) events.push('keydown');

  const interactionPatterns = [];
  if (/useState/i.test(logicCode) && /toggle/i.test(logicCode)) interactionPatterns.push('click-toggle');
  if (/filter\s*\(/i.test(logicCode)) interactionPatterns.push('list-filter');
  if (/form/i.test(logicCode)) interactionPatterns.push('form');

  const statePatterns = [];
  if (/useState\s*\(|useReducer\s*\(/i.test(logicCode)) statePatterns.push('localState');
  if (/NgRx/i.test(logicCode)) statePatterns.push('ngrx');
  if (/Vuex/i.test(logicCode)) statePatterns.push('vuex');

  const apiSignatures = [];
  const fetchRegex = /fetch([A-Za-z0-9_]*)/gi;
  let fm;
  while ((fm = fetchRegex.exec(logicCode)) !== null) {
    const name = `fetch${fm[1] || ''}`;
    if (!apiSignatures.includes(name)) apiSignatures.push(name);
  }
  const axiosRegex = /axios\.([A-Za-z0-9_]+)/gi;
  let am;
  while ((am = axiosRegex.exec(logicCode)) !== null) {
    const name = `axios.${am[1]}`;
    if (!apiSignatures.includes(name)) apiSignatures.push(name);
  }

  const cyclomatic = 1
    + tokenCount(logicCode, /\bif\b/gi)
    + tokenCount(logicCode, /\belse\s+if\b/gi)
    + tokenCount(logicCode, /\bfor\b/gi)
    + tokenCount(logicCode, /\bwhile\b/gi)
    + tokenCount(logicCode, /\bswitch\b/gi);

  const handlerCount = events.length;
  const apiCallCount = apiSignatures.length;
  const conditionalCount = tokenCount(logicCode, /\bif\b/gi) + tokenCount(logicCode, /\bswitch\b/gi);

  return {
    status: 'ok',
    eventTypes: Array.from(new Set(events)),
    interactionPatterns: Array.from(new Set(interactionPatterns)),
    statePatterns: Array.from(new Set(statePatterns)),
    apiSignatures: Array.from(new Set(apiSignatures)),
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

