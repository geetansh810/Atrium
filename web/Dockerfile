# PRODUCTION image for the web frontend — static build served by nginx.
#
# OPTIONAL: the default prod frontend host is Netlify (../netlify.toml), which
# builds the static site itself and does NOT use this file. Use this only to run
# the frontend as a container instead — e.g. self-hosting, or deploying web as a
# Render Docker web service rather than a Netlify site.
#
# VITE_* are compile-time: they're baked into the static bundle at `npm run
# build`, so they must be passed as build args, not runtime env.
#   docker build -f web/Dockerfile \
#     --build-arg VITE_API_BASE_URL=https://your-api.example.com/api/v1 \
#     -t atrium-web web

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ARG VITE_USE_MOCKS=0
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_USE_MOCKS=$VITE_USE_MOCKS
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
