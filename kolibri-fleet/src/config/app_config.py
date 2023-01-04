import asyncio
import os
from concurrent.futures import ThreadPoolExecutor

import aiohttp
import nest_asyncio


class AppConfig:
    SETTING_KEY_NUM_CONNECTIONS = "NUM_CONNECTIONS"

    num_connections: int = int(os.getenv(SETTING_KEY_NUM_CONNECTIONS, 100))

    # setting aiohttp tcp connector, limiting the number of simultaneous connections
    # limit: overall limit of simultaneous connections (setting to 0 means no limit)
    # limit_per_host: limit of simultaneous connections per host
    # ttl_dns_cache: dns resolutions by default cached for 10 seconds. Here we set explicitly to
    # extend the value
    tcp_connector = aiohttp.TCPConnector(limit=0,
                                         limit_per_host=num_connections,
                                         ttl_dns_cache=300)

    # to be able to start multiple processes utilizing one loop
    # (uvicorn is already starting one up, also need to configure it
    # to utilize the asyncio loop to make the nest_asyncio hack work (see readme))
    nest_asyncio.apply()
    event_loop = asyncio.get_event_loop()

    # utilizing single session to utilize connection pools
    # connector_owner must be set to False here, otherwise subsequent requests will fail
    # for settings, see: https://docs.aiohttp.org/en/stable/client_reference.html
    http_client_session = aiohttp.ClientSession(connector=tcp_connector,
                                                loop=event_loop,
                                                connector_owner=False,
                                                version=aiohttp.http.HttpVersion11)

    # executor used to distribute distinct tasks on
    # TODO: due to aggregation need its probably a good idea to keep single batches processed in single batches
    # this should be achievable by passing the execution for a batch which then is not referencing any executor
    executor = ThreadPoolExecutor(max_workers=4)


