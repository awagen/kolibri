FROM python:3.11-slim

COPY requirements.txt /
RUN pip install -r /requirements.txt

EXPOSE ${PORT}

COPY ./src /app/src
COPY main.py /app
COPY log_conf.yaml /app

WORKDIR /app

ENV PYTHONPATH ${PYTHONPATH}:/app

ENTRYPOINT uvicorn main:app --host 0.0.0.0 --port ${PORT} --loop asyncio --log-config /app/log_conf.yaml --log-level info --no-access-log