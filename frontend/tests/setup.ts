// Node 26's native localStorage getter shadows happy-dom's storage in Vitest workers.
// Use one in-memory browser-style store per test environment instead.
class TestStorage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(String(key)) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(String(key)) }
  setItem(key: string, value: string) { this.values.set(String(key), String(value)) }
}
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: new TestStorage(),
})
