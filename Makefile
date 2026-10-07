.PHONY: up down test build clean

up:
	docker compose up -d

down:
	docker compose down

test:
	./gradlew test

build:
	./gradlew build

clean:
	./gradlew clean
