## Install dependencies
```npm install```

## Run dev server
```npm run dev```

## Bundle project for production
Prepares resources to be served in dist folder.
```npm run build```

## Run dockerized
- example: ```https://vuejs.org/v2/cookbook/dockerize-vuejs-app.html```, ```https://cli.vuejs.org/guide/deployment.html#docker-nginx```
- ```docker build -t kolibri-watch:0.1.0 .```
- ```docker run -p 8080:80 --rm --name kolibri-watch-1 kolibri-watch:0.1.0``` (within container, nginx listens on port 80)
- or for non-detached: ```docker run -it -p 8080:80 --rm --name kolibri-watch-1 kolibri-watch:0.1.0```
- access app on ```localhost:8080```

## Env var management
- vite is exposing env variables via import.meta.env (https://vitejs.dev/guide/env-and-mode.html#env-files)
- there is priority assigned depending on which mode the app is started. Env variables that already exist when
vue client is executed should have highest prio and not be overwritten