# ---------- dependency cache ----------
FROM node:20-alpine AS dependencies
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

# ---------- test stage ----------
FROM dependencies AS test
COPY . .
CMD ["sh", "-c", "npm run lint && npm run build"]

# ---------- build stage ----------
FROM dependencies AS build
COPY . .
RUN npm run build

# ---------- runtime stage ----------
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
