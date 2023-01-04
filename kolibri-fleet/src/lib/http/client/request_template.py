from typing import Dict, List


class RequestTemplate:

    def __init__(self,
                 context_path: str,
                 parameters: Dict[str, List[str]],
                 headers: Dict[str, str],
                 body: str,
                 http_method: str):
        """
        Object representing info to compose a request
        :param context_path:
        :param parameters:
        :param headers:
        :param body:
        :param http_method:
        """
        self.context_path = context_path
        self.parameters = parameters
        self.headers = headers
        self.body = body
        self.http_method = http_method
