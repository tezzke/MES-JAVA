import { readFileSync } from 'node:fs';

const packageJson = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
const lock = JSON.parse(readFileSync(new URL('../package-lock.json', import.meta.url), 'utf8'));
const direct = { ...packageJson.dependencies, ...packageJson.devDependencies };
const ranged = Object.entries(direct)
  .filter(([, version]) => !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version))
  .map(([name]) => name);

if (ranged.length > 0) {
  throw new Error(`直接依赖必须固定精确版本: ${ranged.join(', ')}`);
}

const missingIntegrity = Object.entries(lock.packages)
  .filter(([path]) => path.startsWith('node_modules/'))
  .filter(([, metadata]) => !metadata.integrity)
  .map(([path]) => path);

if (missingIntegrity.length > 0) {
  throw new Error(`锁文件缺少 integrity: ${missingIntegrity.slice(0, 10).join(', ')}`);
}

console.log(`依赖锁校验通过: ${Object.keys(lock.packages).length - 1} 个包均包含完整性摘要。`);
