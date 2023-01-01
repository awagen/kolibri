import asyncio
import os

import aiohttp
import nest_asyncio


class AppConfig:
    SETTING_KEY_NUM_CONNECTIONS = "NUM_CONNECTIONS"

    num_connections: int = int(os.getenv(SETTING_KEY_NUM_CONNECTIONS, 100))

    # setting aiohttp tcp connector, limiting the number of simultaneous connections
    tcp_connector = aiohttp.TCPConnector(limit=num_connections, limit_per_host=num_connections)

    # to be able to start multiple processes utilizing one loop
    # (uvicorn is already starting one up, also need to configure it
    # to utilize the asyncio loop to make the nest_asyncio hack work (see readme))
    nest_asyncio.apply()
    event_loop = asyncio.get_event_loop()

    # utilizing single session to utilize connection pools
    # connector_owner must be set to False here, otherwise subsequent requests will fail
    http_client_session = aiohttp.ClientSession(connector=tcp_connector, loop=event_loop, connector_owner=False)
