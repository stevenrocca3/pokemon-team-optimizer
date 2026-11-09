# One image for the whole site: the React app is built and served by Spring from the same origin as the API.
# Build:  docker build -t dexpresso .
# Run:    docker run -p 8080:8080 -e DATABASE_URL=... -e DB_USER=... -e DB_PASSWORD=... \
#           -e GOOGLE_CLIENT_ID=... -e GOOGLE_CLIENT_SECRET=... dexpresso

FROM node:22-alpine AS web
WORKDIR /web
COPY pokemon-optimizer-frontend/package.json pokemon-optimizer-frontend/package-lock.json ./
RUN npm ci
COPY pokemon-optimizer-frontend/ ./
# optional cookieless analytics: --build-arg VITE_GOATCOUNTER_URL=https://<code>.goatcounter.com/count
ARG VITE_GOATCOUNTER_URL=""
ENV VITE_GOATCOUNTER_URL=$VITE_GOATCOUNTER_URL
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY pokemon-optimizer-backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY pokemon-optimizer-backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
# the tests need Postgres and run in development and CI, not here
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=api /api/target/pokemon-optimizer-backend-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# full JIT (no TieredStopAtLevel=1 like spring-boot:run): the Team Builder's battles are 2–3x faster
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
