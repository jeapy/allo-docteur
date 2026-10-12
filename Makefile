.PHONY: up down logs test test-backend test-admin build secrets

up:            ## Démarre postgres + backend + admin (nécessite .env)
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f backend

test: test-backend test-admin

test-backend:  ## Nécessite Docker (Testcontainers)
	cd backend && mvn -B verify

test-admin:
	cd admin && npm ci && npm test && npm run build

secrets:       ## Génère des secrets pour .env
	@echo APP_JWT_SECRET=$$(openssl rand -base64 48)
	@echo APP_CRYPTO_KEY=$$(openssl rand -base64 32)
