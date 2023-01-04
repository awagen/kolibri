import copy
import logging
from typing import Dict, List, Union

from src.lib.http.client.request_template import RequestTemplate


class RequestTemplateBuilder:

    def __init__(self):
        """
        Builder for request template. Request template used for requesting is created by calling
        the build() function after all values are set
        """
        self._context_path: str = ""
        self._parameters: Dict[str, List[str]] = {}
        self._headers: Dict[str, str] = {}
        self._body_content_type: str = "application/json"
        self._body_string: str = ""
        self._body_value_replacement_dict: Dict[str, str] = {}
        self._http_method: str = "GET"

    def with_context_path(self, path: str) -> 'RequestTemplateBuilder':
        self._context_path = path
        return self

    def with_params(self, params: Dict[str, List[str]]) -> 'RequestTemplateBuilder':
        self._parameters = params
        return self

    def with_headers(self, headers: Dict[str, str]) -> 'RequestTemplateBuilder':
        self._headers = headers
        return self

    def with_body(self, body_string: str) -> 'RequestTemplateBuilder':
        self._body_string = body_string
        return self

    def add_body_replace_values(self, replace_dict: Dict[str, str]) -> 'RequestTemplateBuilder':
        self._body_value_replacement_dict = self._body_value_replacement_dict | replace_dict
        return self

    def with_http_method(self, method: str) -> 'RequestTemplateBuilder':
        self._http_method = method
        return self

    def _apply_body_replacements_and_return_new_value(self) -> Union[str, None]:
        """
        From the set _body_string and _body_value_replacement_dict, replace the substrings corresponding
        to any key of the replacement dict with the value for that key
        :return:
        """
        if self._body_string is None or self._body_string.strip() == "":
            logging.info("No body value set, thus skipping value replacement")
            return self._body_string
        body_value = self._body_string
        for key, value in self._body_value_replacement_dict.items():
            body_value = body_value.replace(key, value)
        return body_value

    def build(self) -> RequestTemplate:
        body = self._apply_body_replacements_and_return_new_value()
        return RequestTemplate(
            context_path=self._context_path,
            parameters=copy.deepcopy(self._parameters),
            headers=copy.deepcopy(self._headers),
            body=body,
            http_method=self._http_method
        )
