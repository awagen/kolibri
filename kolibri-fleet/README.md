What is the project about
=========================
The project provides a lightweight python version of the kolibri backend.
As such, it provides means to evaluate search systems and likely will be extended to other use cases.
It builds on FastAPI.
The project initially is build as single-node application, yet a lean multi-node mode is planned, utilizing
synchronisation via bucket rather than any more involved setup such as DB, Queue or the like.

Creating a python virtual environment
=====================================
- ```python3 -m venv /path/to/new/virtual/environment``` (for python 3.11 you will need to install python3.11-venv)

Start the app
=============
- https://fastapi.tiangolo.com/tutorial/first-steps/
- Arguments:
    - main: file main.py
    - app: the object created inside of main.py with the line app = FastAPI()
    - --reload:  make the server restart after code changes. Only use for development.
    - to start app, cd into main project folder (one level before src) and run:
```shell script
uvicorn main:app --reload  --env-file ./files/local-env.env --loop asyncio
```
Configuring loop to utilize asyncio is required to do utilization of the hack
of nested_asyncio, which effectively allows multiple executions chiming in on
one loop (uvicorn already starts one up and without the hack utilizing it for
executions in our logic wouldnt work due to "loop already running")

- build docker img (project root): ```docker build -t kolibri-fleet .```

- run: 
    -```docker run --env-file ./files/local-env.env -d -p 80:80 kolibri-fleet```
 
Overview of endpoints
=====================

- ```[host]:[port]/```: simple hello response
- ```[host]:[port]/docs```:  swagger endpoint included in FastAPI

