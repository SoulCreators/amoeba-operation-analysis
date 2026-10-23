.PHONY: check backend frontend verify

check:
	node scripts/check-architecture.mjs

backend:
	mvn -B verify

frontend:
	cd frontend && npm ci && npm run test:unit && npm run build

verify: check backend frontend
