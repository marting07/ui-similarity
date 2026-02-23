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

function inferLayoutPatterns(templateCode, styleCode) {
  const patterns = new Set();
  if (templateCode.includes('<ul')) patterns.add('list-vertical');
  if (styleCode.includes('display: flex')) {
    if (styleCode.includes('flex-direction: column')) patterns.add('flex-col');
    if (styleCode.includes('flex-direction: row') || styleCode.includes('align-items: center')) {
      patterns.add('flex-row-center');
    }
  }
  return Array.from(patterns);
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

  const avgBranching = parentCount > 0 ? totalChildren / parentCount : 0;
  return {
    tagHistogram,
    roleHistogram,
    depth: maxDepth,
    avgBranching,
    layoutPatterns: inferLayoutPatterns(templateCode, styleCode),
  };
}

async function main() {
  try {
    const raw = await readStdin();
    const request = JSON.parse(raw || '{}');
    const templateCode = typeof request.sourceCode === 'string'
      ? request.sourceCode
      : (typeof request.templateCode === 'string' ? request.templateCode : '');
    const styleCode = typeof request.styleCode === 'string' ? request.styleCode : '';
    const features = extract(templateCode, styleCode);

    const response = {
      status: 'ok',
      tagHistogram: features.tagHistogram,
      roleHistogram: features.roleHistogram,
      layoutPatterns: features.layoutPatterns,
      depth: features.depth,
      avgBranching: features.avgBranching,
    };
    process.stdout.write(JSON.stringify(response));
  } catch (error) {
    process.stdout.write(JSON.stringify({
      status: 'error',
      error: error instanceof Error ? error.message : String(error),
    }));
    process.exit(1);
  }
}

main();
