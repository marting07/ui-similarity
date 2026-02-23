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

function countOccurrences(haystack, needle) {
  let count = 0;
  let index = 0;
  while (true) {
    index = haystack.indexOf(needle, index);
    if (index === -1) return count;
    count += 1;
    index += needle.length;
  }
}

function extract(templateCode, styleCode) {
  const tagHistogram = {};
  const roleHistogram = {};

  const tagRegex = /<\s*([a-zA-Z0-9]+)/g;
  const roleRegex = /role\s*=\s*"([^"]+)"/g;

  let tagMatch;
  while ((tagMatch = tagRegex.exec(templateCode)) !== null) {
    const tag = tagMatch[1].toLowerCase();
    tagHistogram[tag] = (tagHistogram[tag] || 0) + 1;
  }

  let roleMatch;
  while ((roleMatch = roleRegex.exec(templateCode)) !== null) {
    const role = roleMatch[1].toLowerCase();
    roleHistogram[role] = (roleHistogram[role] || 0) + 1;
  }

  const layoutPatterns = new Set();
  if (templateCode.includes('<ul')) layoutPatterns.add('list-vertical');
  if (styleCode.includes('display: flex')) {
    if (styleCode.includes('flex-direction: column')) layoutPatterns.add('flex-col');
    if (styleCode.includes('flex-direction: row') || styleCode.includes('align-items: center')) {
      layoutPatterns.add('flex-row-center');
    }
  }

  const lines = templateCode.split(/\r?\n/);
  let currentDepth = 0;
  let maxDepth = 1;
  let totalChildren = 0;
  let parentCount = 0;
  for (const line of lines) {
    const open = countOccurrences(line, '<');
    const close = countOccurrences(line, '</');
    currentDepth += open;
    if (currentDepth > maxDepth) maxDepth = currentDepth;
    if (open > 0) {
      totalChildren += open;
      parentCount += 1;
    }
    currentDepth -= close;
  }

  const avgBranching = parentCount > 0 ? totalChildren / parentCount : 0.0;

  return {
    status: 'ok',
    tagHistogram,
    roleHistogram,
    layoutPatterns: Array.from(layoutPatterns),
    depth: Math.max(1, maxDepth),
    avgBranching,
  };
}

async function main() {
  try {
    const raw = await readStdin();
    const req = JSON.parse(raw || '{}');
    const templateCode = typeof req.templateCode === 'string' ? req.templateCode : '';
    const styleCode = typeof req.styleCode === 'string' ? req.styleCode : '';
    process.stdout.write(JSON.stringify(extract(templateCode, styleCode)));
  } catch (error) {
    process.stdout.write(JSON.stringify({ status: 'error', error: error instanceof Error ? error.message : String(error) }));
    process.exit(1);
  }
}

main();
