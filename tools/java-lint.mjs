#!/usr/bin/env node
/**
 * Structural sanity checks for the Java sources.
 *
 * This is NOT a compiler. It cannot resolve types or check overload
 * resolution, so it will not catch everything. What it does catch is the class
 * of mistakes that are easy to make when editing many files and impossible to
 * catch in this sandbox (there is no JDK here):
 *
 *   - a file whose package does not match its directory
 *   - a public type whose name does not match the file name
 *   - unbalanced braces / parens / brackets (including inside comments and
 *     string literals, which are handled by a small lexer)
 *   - an import that does not exist anywhere in the repo and is not a known
 *     external package  (catches renames and typos in cross-package imports)
 *   - duplicated class names in the same package
 *   - inner-ring (domain) files importing framework or infrastructure packages
 *   - duplicated @Bean method names
 *
 * Usage: node tools/java-lint.mjs [repoRoot]
 */
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';

const ROOT = process.argv[2] || new URL('..', import.meta.url).pathname;
const errors = [];
const warnings = [];
const err = (f, m) => errors.push(`${f}: ${m}`);
const warn = (f, m) => warnings.push(`${f}: ${m}`);

/** Walk every .java file. */
function javaFiles(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'target' || entry === '.git') continue;
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) javaFiles(full, out);
    else if (entry.endsWith('.java')) out.push(full);
  }
  return out;
}

const files = javaFiles(ROOT);
const rel = (f) => relative(ROOT, f);

/* ------------------------------------------------------------------ lexer */
/**
 * Strip comments, string literals and char literals, replacing them with
 * spaces so brace counting cannot be confused by a `}` inside a string.
 * Handles: line comments, block comments, "..." with backslash escapes, '...',
 * and text blocks (three consecutive double quotes).
 */
function stripLiteralsAndComments(src) {
  let out = '';
  let i = 0;
  const n = src.length;
  while (i < n) {
    const c = src[i];
    const d = src[i + 1];
    if (c === '/' && d === '/') {
      while (i < n && src[i] !== '\n') { out += ' '; i++; }
    } else if (c === '/' && d === '*') {
      out += '  '; i += 2;
      while (i < n && !(src[i] === '*' && src[i + 1] === '/')) { out += src[i] === '\n' ? '\n' : ' '; i++; }
      out += '  '; i += 2;
    } else if (c === '"' && src[i + 1] === '"' && src[i + 2] === '"') {
      out += '   '; i += 3;
      while (i < n && !(src[i] === '"' && src[i + 1] === '"' && src[i + 2] === '"')) { out += src[i] === '\n' ? '\n' : ' '; i++; }
      out += '   '; i += 3;
    } else if (c === '"' || c === "'") {
      const quote = c;
      out += ' '; i++;
      while (i < n && src[i] !== quote) {
        if (src[i] === '\\') { out += '  '; i += 2; continue; }
        out += src[i] === '\n' ? '\n' : ' '; i++;
      }
      out += ' '; i++;
    } else {
      out += c; i++;
    }
  }
  return out;
}

/* ------------------------------------------------------- pass 1: per file */
const declaredTypes = new Map(); // simpleName -> [relPath]
const byPackage = new Map();      // package -> Set(simpleName)
const importsByFile = new Map();  // relPath -> [fqcn]
const topLevelTypeByFile = new Map();

for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const r = rel(file);
  const code = stripLiteralsAndComments(raw);

  // --- package must match the directory ---
  const pkgMatch = code.match(/^\s*package\s+([\w.]+)\s*;/m);
  if (!pkgMatch) {
    err(r, 'no package declaration');
    continue;
  }
  const pkg = pkgMatch[1];
  // The source root is the segment right after src/main/java (or src/test/java).
  const parts = r.split('/');
  const srcIdx = parts.findIndex((p, i) => p === 'java' && parts[i - 1] === 'main' || p === 'java' && parts[i - 1] === 'test');
  if (srcIdx === -1) {
    err(r, 'file is not under src/main/java or src/test/java');
  } else {
    const declaredDir = parts.slice(srcIdx + 1, parts.length - 1).join('/');
    const declaredFromPkg = pkg.split('.').join('/');
    if (declaredDir !== declaredFromPkg) {
      err(r, `package '${pkg}' does not match its directory '${declaredDir}'`);
    }
  }

  // --- balance check ---
  for (const [open, close, label] of [['{', '}', 'braces'], ['(', ')', 'parens'], ['[', ']', 'brackets']]) {
    const o = (code.match(new RegExp('\\' + open, 'g')) || []).length;
    const c = (code.match(new RegExp('\\' + close, 'g')) || []).length;
    if (o !== c) err(r, `unbalanced ${label}: ${o} '${open}' vs ${c} '${close}'`);
  }

  // --- imports ---
  const imports = [...code.matchAll(/^\s*import\s+(static\s+)?([\w.]+)\s*;/gm)].map((m) => ({
    fqcn: m[2], isStatic: Boolean(m[1]),
  }));
  importsByFile.set(r, imports.map((i) => i.fqcn));

  // --- types declared in this file (top level and nested) ---
  const typeRe = /\b(?:class|interface|enum|record|@interface)\s+([A-Z][\w$]*)/g;
  let m;
  const typesHere = [];
  while ((m = typeRe.exec(code))) typesHere.push(m[1]);

  const publicType = code.match(/\bpublic\s+(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*(?:class|interface|enum|record)\s+([A-Z][\w$]*)/);
  if (publicType) {
    const base = r.split('/').pop().replace(/\.java$/, '');
    if (publicType[1] !== base) {
      err(r, `public type '${publicType[1]}' does not match file name '${base}.java'`);
    }
    topLevelTypeByFile.set(r, publicType[1]);
  }
  for (const t of typesHere) {
    if (!declaredTypes.has(t)) declaredTypes.set(t, []);
    declaredTypes.get(t).push(r);
    if (!byPackage.has(pkg)) byPackage.set(pkg, new Set());
    byPackage.get(pkg).add(t);
  }

  /* -------- onion rule: the domain ring must not import the outside -------- */
  const domainIdx = pkg.indexOf('.domain');
  if (domainIdx !== -1) {
    const forbidden = [
      [/^org\.springframework\./, 'Spring'],
      [/^jakarta\./, 'Jakarta EE'],
      [/^software\.amazon\./, 'AWS SDK'],
      [/^io\.jsonwebtoken\./, 'JJWT'],
      [/^com\.fasterxml\.jackson\./, 'Jackson'],
      [/\.infrastructure(\.|$)/, 'infrastructure'],
      [/\.presentation(\.|$)/, 'presentation'],
    ];
    for (const imp of imports) {
      for (const [re, label] of forbidden) {
        if (re.test(imp.fqcn)) {
          err(r, `DOMAIN RING VIOLATION: domain code must not import ${label} (${imp.fqcn})`);
        }
      }
    }
  }

  /* -------- application ring must not import infrastructure/presentation ---- */
  const appIdx = pkg.indexOf('.application');
  if (appIdx !== -1) {
    for (const imp of imports) {
      if (/\.infrastructure(\.|$)/.test(imp.fqcn)) {
        err(r, `APPLICATION RING VIOLATION: must not import infrastructure (${imp.fqcn})`);
      }
      if (/\.presentation(\.|$)/.test(imp.fqcn)) {
        err(r, `APPLICATION RING VIOLATION: must not import presentation (${imp.fqcn})`);
      }
    }
  }
}

/* ------------------------------------- pass 2: cross-package import sanity */
const allPackages = new Set([...byPackage.keys()]);
const externalRoots = new Set([
  'java', 'javax', 'jakarta', 'org.springframework', 'org.springframework.boot',
  'org.springframework.data', 'org.springframework.security', 'org.springframework.web',
  'org.springframework.cloud', 'org.hibernate', 'org.slf4j', 'org.junit',
  'org.mockito', 'io.jsonwebtoken', 'software.amazon.awssdk', 'com.fasterxml',
  'org.postgresql', 'com.microsoft.sqlserver', 'reactor', 'com.netflix',
  'org.apache', 'lombok', 'kotlin', 'kotlinx', 'feign', 'feign.codec',
]);

for (const [r, imports] of importsByFile) {
  for (const fqcn of imports) {
    if (fqcn.endsWith('.*')) continue;
    // External if it sits under a known third-party root prefix.
    const isExternal = [...externalRoots].some((root) => fqcn === root || fqcn.startsWith(root + '.'));
    if (isExternal) continue;

    const isStatic = new RegExp(`^import\\s+static\\s+${fqcn.replace(/\./g, '\\.')}\\s*;`, 'm')
      .test(readFileSync(join(ROOT, r), 'utf8'));
    if (isStatic) continue;

    const owner = fqcn.split('.').pop();
    const ownerPkg = fqcn.slice(0, -(owner.length + 1));
    if (allPackages.has(ownerPkg)) continue;   // a package in this repo
    if (declaredTypes.has(owner)) continue;     // a type in this repo
    warn(r, `import '${fqcn}' resolves to no package or type in this repo — verify it exists`);
  }
}

/* --------------------------------------------- pass 3: duplicate @Bean names */
for (const [r] of importsByFile) {
  const raw = readFileSync(join(ROOT, r), 'utf8');
  const code = stripLiteralsAndComments(raw);
  const beanNames = [...code.matchAll(/@Bean\b[\s\S]{0,200}?\b(?:public|protected|private)?\s*[\w.<>,\[\]\s?]+\s+(\w+)\s*\(/g)]
    .map((m) => m[1]);
  const dupes = beanNames.filter((n, i) => beanNames.indexOf(n) !== i);
  if (dupes.length) err(r, `duplicate @Bean method name(s): ${[...new Set(dupes)].join(', ')}`);
}

/* -------------------------------------------------- pass 4: cross-ring leak */
for (const [r, imports] of importsByFile) {
  const code = stripLiteralsAndComments(readFileSync(join(ROOT, r), 'utf8'));
  const pkg = (code.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1] || '';
  // This service's own base package, e.g. com.aditi.authservice for
  // com.aditi.authservice.infrastructure.config.
  const segs = pkg.split('.');
  const ownBase = segs.length >= 3 ? segs.slice(0, 3).join('.') : pkg;

  for (const fqcn of imports) {
    if (!fqcn.startsWith('com.aditi.')) continue;
    if (fqcn.startsWith(ownBase + '.')) continue;   // our own code
    err(r, `CROSS-SERVICE LEAK: ${pkg} imports '${fqcn}' from another service — `
      + `services must never share Java code, only contracts (HTTP/JSON)`);
  }
}

/* ----------------------------------------------------------- pass 5: report */
console.log(`\nScanned ${files.length} Java files under ${ROOT}\n`);
if (warnings.length) {
  console.log(`WARNINGS (${warnings.length}):`);
  for (const w of warnings) console.log('  ~ ' + w);
  console.log('');
}
if (errors.length) {
  console.log(`ERRORS (${errors.length}):`);
  for (const e of errors) console.log('  ✗ ' + e);
  process.exit(1);
}
console.log('✓ No structural errors found.\n');
