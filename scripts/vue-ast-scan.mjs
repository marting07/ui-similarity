#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

function readStdin() {
  return new Promise((resolve, reject) => {
    let data = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => { data += chunk; });
    process.stdin.on('end', () => resolve(data));
    process.stdin.on('error', reject);
  });
}

function walkFiles(root, out = []) {
  const entries = fs.readdirSync(root, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === 'build') continue;
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) {
      walkFiles(full, out);
      continue;
    }
    if (entry.isFile() && full.endsWith('.vue')) out.push(full);
  }
  return out;
}

function toRelativePosix(root, file) {
  return path.relative(root, file).split(path.sep).join('/');
}

function extractTemplateRegex(text) {
  const match = text.match(/<template(?:\s[^>]*)?>([\s\S]*?)<\/template>/i);
  return match ? match[1].trim() : null;
}

function extractStylesRegex(text) {
  const styles = [];
  const regex = /<style(?:\s[^>]*)?>([\s\S]*?)<\/style>/gi;
  let m;
  while ((m = regex.exec(text)) !== null) {
    const content = (m[1] || '').trim();
    if (content.length > 0) styles.push(content);
  }
  return styles;
}

async function loadVueCompilerSfc() {
  try {
    const mod = await import('@vue/compiler-sfc');
    return mod;
  } catch {
    return null;
  }
}

function parseSfcWithCompiler(compiler, source, filename) {
  const { descriptor } = compiler.parse(source, { filename });

  const template = descriptor.template?.content?.trim() || null;
  const inlineStyles = (descriptor.styles || [])
    .map((block) => (block.content || '').trim())
    .filter((content) => content.length > 0);

  return {
    inlineTemplateCode: template,
    inlineStyleCodes: inlineStyles
  };
}

function parseSfcWithRegex(source) {
  return {
    inlineTemplateCode: extractTemplateRegex(source),
    inlineStyleCodes: extractStylesRegex(source)
  };
}

async function scanVueRepo(repoRoot) {
  const compiler = await loadVueCompilerSfc();
  const files = walkFiles(repoRoot);
  const components = [];

  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8');
    const relativePath = toRelativePosix(repoRoot, file);
    const exportName = path.posix.basename(relativePath, '.vue');

    const parsed = compiler
      ? parseSfcWithCompiler(compiler, source, file)
      : parseSfcWithRegex(source);

    components.push({
      relativePath,
      exportName,
      templatePath: relativePath,
      logicPath: relativePath,
      stylePaths: [],
      inlineTemplateCode: parsed.inlineTemplateCode,
      inlineStyleCodes: parsed.inlineStyleCodes
    });
  }

  return components;
}

async function main() {
  try {
    const raw = await readStdin();
    const request = JSON.parse(raw || '{}');
    if (!request.repoRoot || typeof request.repoRoot !== 'string') {
      console.log(JSON.stringify({ status: 'error', components: [], error: 'Missing repoRoot' }));
      process.exit(1);
      return;
    }
    const components = await scanVueRepo(request.repoRoot);
    console.log(JSON.stringify({ status: 'ok', components }));
  } catch (error) {
    console.log(JSON.stringify({
      status: 'error',
      components: [],
      error: error instanceof Error ? error.message : String(error)
    }));
    process.exit(1);
  }
}

main();
