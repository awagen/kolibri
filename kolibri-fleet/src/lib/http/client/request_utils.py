import aiohttp

from src.config.app_config import AppConfig
from src.lib.http.client.request_template import RequestTemplate


class RequestUtils:

    @staticmethod
    async def aiohttp_execute_request(host: str,
                                      use_https: bool,
                                      request_template: RequestTemplate) -> aiohttp.ClientResponse | None:
        """
        Helper method to execute request given a request template.
        :param host:
        :param use_https:
        :param request_template:
        :return:
        """
        protocol = "https" if use_https else "http"
        url = "%s://%s" % (protocol, host.rstrip("/"))
        body: bytes = request_template.body.encode("utf-8") if (request_template.body is not None) else None
        if request_template.http_method.upper() == "GET":
            return AppConfig.http_client_session.get(url, params=request_template.parameters)
        if request_template.http_method.upper() == "PUT":
            return AppConfig.http_client_session.put(url, data=body, params=request_template.parameters)
        if request_template.http_method.upper() == "POST":
            return AppConfig.http_client_session.post(url, data=body, params=request_template.parameters)
        if request_template.http_method.upper() == "DELETE":
            return AppConfig.http_client_session.delete(url, params=request_template.parameters)
        return None

