# build stage
FROM node:lts-alpine as build-stage
WORKDIR /app
COPY package*.json ./
# install project dependencies
RUN npm install
COPY . .
# build app for production with minification
RUN npm run build

# production stage
FROM nginx:stable-alpine as production-stage
RUN mkdir /app
COPY --from=build-stage /app/dist /app
COPY ./nginx/nginx.conf /etc/nginx/nginx.conf
CMD ["nginx", "-g", "daemon off;"]