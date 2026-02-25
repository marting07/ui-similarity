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
    if (entry.isFile() && full.endsWith('.component.ts')) out.push(full);
  }
  return out;
}

function toRelativePosix(root, file) {
  return path.relative(root, file).split(path.sep).join('/');
}

function resolveRelative(baseFileRel, spec) {
  const dir = path.posix.dirname(baseFileRel);
  return path.posix.normalize(path.posix.join(dir, spec));
}

function defaultStyleCandidates(repoRoot, relativePath) {
  const dir = path.posix.dirname(relativePath);
  const file = path.posix.basename(relativePath);
  const stem = file.replace(/\.ts$/, '');
  const candidates = [
    path.posix.join(dir, `${stem}.css`),
    path.posix.join(dir, `${stem}.scss`)
  ];
  return candidates.filter((rel) => fs.existsSync(path.join(repoRoot, rel)));
}

async function loadTypeScript() {
  try {
    const mod = await import('typescript');
    return mod.default || mod;
  } catch {
    return null;
  }
}

function parseClassNamesRegex(text) {
  const names = [];
  const regex = /(?:export\s+)?class\s+([A-Za-z_][A-Za-z0-9_]*)/g;
  let m;
  while ((m = regex.exec(text)) !== null) names.push(m[1]);
  return Array.from(new Set(names));
}

function parseTemplateUrlRegex(text) {
  const m = text.match(/templateUrl\s*:\s*['\"]([^'\"]+)['\"]/);
  return m ? m[1] : null;
}

function parseInlineTemplateRegex(text) {
  const m1 = text.match(/template\s*:\s*`([\s\S]*?)`/);
  if (m1) return m1[1];
  const m2 = text.match(/template\s*:\s*'([^']*)'/);
  if (m2) return m2[1];
  const m3 = text.match(/template\s*:\s*\"([^\"]*)\"/);
  if (m3) return m3[1];
  return null;
}

function parseStyleUrlsRegex(text) {
  const m = text.match(/styleUrls\s*:\s*\[([\s\S]*?)\]/);
  if (!m) return [];
  const arr = m[1];
  const quoteRegex = /['\"]([^'\"]+)['\"]/g;
  const out = [];
  let qm;
  while ((qm = quoteRegex.exec(arr)) !== null) out.push(qm[1]);
  return out;
}

function parseInlineStylesRegex(text) {
  const m = text.match(/styles\s*:\s*\[([\s\S]*?)\]/);
  if (!m) return [];
  const arr = m[1];
  const itemRegex = /`([\s\S]*?)`|'([^']*)'|\"([^\"]*)\"/g;
  const out = [];
  let im;
  while ((im = itemRegex.exec(arr)) !== null) {
    const value = im[1] || im[2] || im[3] || '';
    if (value.length > 0) out.push(value);
  }
  return out;
}

function parseAngularFileRegex(text) {
  return {
    classNames: parseClassNamesRegex(text),
    templateUrl: parseTemplateUrlRegex(text),
    inlineTemplate: parseInlineTemplateRegex(text),
    styleUrls: parseStyleUrlsRegex(text),
    inlineStyles: parseInlineStylesRegex(text)
  };
}

function stringFromExpression(ts, expr) {
  if (!expr) return null;
  if (ts.isStringLiteral(expr) || ts.isNoSubstitutionTemplateLiteral(expr)) {
    return expr.text;
  }
  if (ts.isTemplateExpression(expr)) {
    return expr.getText().slice(1, -1);
  }
  return null;
}

function stringArrayFromExpression(ts, expr) {
  if (!expr || !ts.isArrayLiteralExpression(expr)) return [];
  const out = [];
  for (const el of expr.elements) {
    const value = stringFromExpression(ts, el);
    if (typeof value === 'string' && value.length > 0) out.push(value);
  }
  return out;
}

function getComponentObjectLiteral(ts, classDecl) {
  for (const mod of classDecl.modifiers || []) {
    if (!ts.isDecorator(mod)) continue;
    const expr = mod.expression;
    if (!ts.isCallExpression(expr)) continue;
    if (!ts.isIdentifier(expr.expression) || expr.expression.text !== 'Component') continue;
    const arg = expr.arguments[0];
    if (arg && ts.isObjectLiteralExpression(arg)) return arg;
  }
  return null;
}

function findFirstComponentConfigTs(ts, sourceFile) {
  for (const stmt of sourceFile.statements) {
    if (!ts.isClassDeclaration(stmt)) continue;
    const obj = getComponentObjectLiteral(ts, stmt);
    if (!obj) continue;

    let templateUrl = null;
    let inlineTemplate = null;
    let styleUrls = [];
    let inlineStyles = [];

    for (const prop of obj.properties) {
      if (!ts.isPropertyAssignment(prop)) continue;
      if (!ts.isIdentifier(prop.name)) continue;
      const key = prop.name.text;
      if (key === 'templateUrl') {
        templateUrl = stringFromExpression(ts, prop.initializer);
      } else if (key === 'template') {
        inlineTemplate = stringFromExpression(ts, prop.initializer);
      } else if (key === 'styleUrls') {
        styleUrls = stringArrayFromExpression(ts, prop.initializer);
      } else if (key === 'styles') {
        inlineStyles = stringArrayFromExpression(ts, prop.initializer);
      }
    }

    return { templateUrl, inlineTemplate, styleUrls, inlineStyles };
  }
  return { templateUrl: null, inlineTemplate: null, styleUrls: [], inlineStyles: [] };
}

function parseAngularFileTs(ts, text, filePath) {
  const sourceFile = ts.createSourceFile(
    filePath,
    text,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS
  );

  const classNames = [];
  for (const stmt of sourceFile.statements) {
    if (ts.isClassDeclaration(stmt) && stmt.name) {
      classNames.push(stmt.name.text);
    }
  }

  const cfg = findFirstComponentConfigTs(ts, sourceFile);
  return {
    // Keep scanner parity with legacy regex behavior by merging regex-derived class names.
    classNames: Array.from(new Set([...classNames, ...parseClassNamesRegex(text)])),
    templateUrl: cfg.templateUrl,
    inlineTemplate: cfg.inlineTemplate,
    styleUrls: cfg.styleUrls,
    inlineStyles: cfg.inlineStyles
  };
}

function buildComponentDescriptors(repoRoot, relativePath, parsed) {
  const components = [];
  const templatePath = parsed.templateUrl ? resolveRelative(relativePath, parsed.templateUrl) : relativePath;
  const resolvedStylePaths = parsed.styleUrls.length > 0
    ? parsed.styleUrls.map((s) => resolveRelative(relativePath, s))
    : (parsed.inlineStyles.length === 0 ? defaultStyleCandidates(repoRoot, relativePath) : []);

  for (const className of parsed.classNames) {
    components.push({
      relativePath,
      exportName: className,
      templatePath,
      logicPath: relativePath,
      stylePaths: resolvedStylePaths,
      inlineTemplateCode: parsed.inlineTemplate,
      inlineStyleCodes: parsed.inlineStyles
    });
  }

  return components;
}

async function scanAngularRepo(repoRoot) {
  const ts = await loadTypeScript();
  const files = walkFiles(repoRoot);
  const components = [];

  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    const relativePath = toRelativePosix(repoRoot, file);
    const parsed = ts
      ? parseAngularFileTs(ts, text, file)
      : parseAngularFileRegex(text);

    if (!parsed.classNames || parsed.classNames.length === 0) continue;
    components.push(...buildComponentDescriptors(repoRoot, relativePath, parsed));
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
    const components = await scanAngularRepo(request.repoRoot);
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
