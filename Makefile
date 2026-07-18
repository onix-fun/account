.PHONY: contracts-check backend-test backend-build frontend-test frontend-build test build

contracts-check:
	test -d .contracts/profile/proto
	test -d .contracts/media/proto

backend-test: contracts-check
	cd backend && ./gradlew --no-daemon test checkModuleBoundaries

backend-build: contracts-check
	cd backend && ./gradlew --no-daemon :app:shadowJar

frontend-test:
	cd frontend && npm test

frontend-build:
	cd frontend && npm run build

test: backend-test frontend-test

build: backend-build frontend-build
