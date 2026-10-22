#!/usr/bin/env node
// Lightweight repository checks; run from any directory with Node.js 20+.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const errors = []
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')
function files(directory) {
  return fs.readdirSync(path.join(root, directory), { withFileTypes: true })
    .flatMap((entry) => entry.isDirectory()
      ? files(`${directory}/${entry.name}`) : [`${directory}/${entry.name}`])
}
const dependencies = {
  common: [], domain: ['common'], application: ['domain'],
  infrastructure: ['application'], api: ['infrastructure'], worker: ['infrastructure'],
}
const imports = {
  common: ['common'], domain: ['common', 'domain'],
  application: ['common', 'domain', 'application'],
  infrastructure: ['common', 'domain', 'application', 'infrastructure'],
  api: ['common', 'domain', 'application', 'infrastructure', 'api'],
  worker: ['common', 'domain', 'application', 'infrastructure', 'worker'],
}
const entryPoints = []
for (const [module, allowed] of Object.entries(dependencies)) {
  const directory = `backend/analysis-${module}`
  for (const dependency of read(`${directory}/pom.xml`).matchAll(/<dependency>[\s\S]*?<\/dependency>/g)) {
    const id = dependency[0].match(/<artifactId>analysis-([^<]+)<\/artifactId>/)?.[1]
    if (id && !allowed.includes(id)) errors.push(`${directory}: forbidden Maven dependency on ${id}`)
  }
  for (const file of files(`${directory}/src/main/java`).filter((file) => file.endsWith('.java'))) {
    const source = read(file)
    for (const match of source.matchAll(/\bimport\s+(?:static\s+)?com\.sunwoda\.evb\.finance\.(?:analysis|amoeba)\.([\w]+)\./g)) {
      if (!imports[module].includes(match[1])) errors.push(`${file}: forbidden import of ${match[1]}`)
    }
    if (module === 'domain' && /\bimport\s+(?:org\.springframework|org\.apache\.poi|java\.sql|javax\.persistence)\./.test(source)) {
      errors.push(`${file}: domain must not depend on framework or persistence implementations`)
    }
    if (/\bpackage com\.sunwoda\.evb\.finance\.amoeba\./.test(source) && module !== 'worker') {
      errors.push(`${file}: obsolete scaffold namespace`)
    }
    if (/@SpringBootApplication\b/.test(source)) entryPoints.push(module)
  }
}
if (entryPoints.sort().join(',') !== 'api,worker') errors.push('Exactly one API and one Worker bootstrap are required')

const main = read('frontend/src/main.ts')
if (!main.includes("from './App.vue'")) errors.push('Frontend main.ts must use App.vue')
for (const file of files('frontend/src/shared').filter((file) => /\.(ts|vue)$/.test(file))) {
  if (/from\s+['"][^'"]*(?:features|App\.vue)/.test(read(file))) errors.push(`${file}: shared code must not depend on application pages`)
}
const migrationNames = fs.readdirSync(path.join(root, 'database/migration'))
const versions = []
for (const name of migrationNames.filter((name) => name.endsWith('.sql'))) {
  const match = name.match(/^V(\d{3,})__[a-z0-9_]+\.sql$/)
  if (!match) errors.push(`database/migration/${name}: invalid migration name`)
  else versions.push(Number(match[1]))
}
versions.sort((a, b) => a - b).forEach((version, index) => {
  if (version !== index + 1) errors.push(`Migration sequence has a duplicate or gap at V${version}`)
})
if (!versions.length) errors.push('No database migrations found')
if (errors.length) {
  console.error(errors.join('\n'))
  process.exitCode = 1
} else {
  console.log(`Architecture checks passed: 6 Maven modules, 2 bootstraps, frontend boundaries, ${versions.length} ordered migrations.`)
}
