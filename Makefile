# Atrium monorepo — targets no-op gracefully for services not yet present.
.PHONY: dev check test

dev:
	cd web && npm run dev

check:
	cd web && npx tsc -b && npm run lint
	@if [ -f core-api/pom.xml ]; then cd core-api && ./mvnw -q verify -DskipTests; else echo "check: core-api not present yet — skipped"; fi
	@if [ -f office-realtime/package.json ]; then cd office-realtime && npm run lint; else echo "check: office-realtime not present yet — skipped"; fi

test:
	@if [ -f web/package.json ] && node -e "process.exit(require('./web/package.json').scripts?.test ? 0 : 1)"; then cd web && npm test; else echo "test: web has no test script yet — skipped"; fi
	@if [ -f core-api/pom.xml ]; then cd core-api && ./mvnw -q test; else echo "test: core-api not present yet — skipped"; fi
	@if [ -f office-realtime/package.json ]; then cd office-realtime && npm test; else echo "test: office-realtime not present yet — skipped"; fi
