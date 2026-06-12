.PHONY: setup up down clean reset-db

setup:
	@echo "Checking development keys..."
	cd dev && ./scripts/generate-dev-keys.sh

up: setup
	@echo "Starting backend and gateway containers..."
	cd dev && docker compose up -d --build
	@echo "Cleaning up frontend port (5174)..."
	-lsof -ti:5174 | xargs kill -9 2>/dev/null || true
	@echo "Starting frontend dev server..."
	cd frontend && npm run dev

down:
	@echo "Stopping containers..."
	cd dev && docker compose down

clean: down
	@echo "Cleaning up volumes..."
	cd dev && docker compose down -v

reset-db:
	@echo "Recreating only the development PostgreSQL database..."
	cd dev && ./scripts/reset-dev-db.sh
