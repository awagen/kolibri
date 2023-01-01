import json
from typing import Dict

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.config.app_config import *
from src.config.app_config import AppConfig
from src.lib.sampler.sampler import Sampler

app = FastAPI()

# cors settings: https://fastapi.tiangolo.com/tutorial/cors/
allowed_origins = os.getenv("allowed_origins", "*")

origins = list([x.strip() for x in allowed_origins.strip().split(",")])

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_methods=["*"],
    allow_headers=["*"]
)


@app.on_event("shutdown")
async def shutdown():
    await AppConfig.http_client_session.close()
    AppConfig.event_loop.close()


@app.get("/")
async def root():
    return {"message": "hi"}
