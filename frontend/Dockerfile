# ---------- dependency cache ----------
FROM node:20-alpine AS dependencies
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

# ---------- test stage ----------
FROM dependencies AS test
COPY . .
CMD ["sh", "-c", "npm run lint && npm run test && npm run build"]

# ---------- build stage ----------
FROM dependencies AS build
ARG VITE_DEPLOY_ENV
ARG VITE_BUILD_SHA
ENV VITE_DEPLOY_ENV=$VITE_DEPLOY_ENV
ENV VITE_BUILD_SHA=$VITE_BUILD_SHA
COPY . .
RUN npm run build

# ---------- runtime stage ----------
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
