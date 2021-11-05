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
