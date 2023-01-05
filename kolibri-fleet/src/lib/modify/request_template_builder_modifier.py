from typing import Dict, List, Callable

from src.lib.http.client.request_template_builder import RequestTemplateBuilder
from src.lib.utils.function_utils import FunctionUtils


class RequestTemplateBuilderModifier:

    @staticmethod
    def get_request_param_modifier(params: Dict[str, List[str]]) -> Callable[[RequestTemplateBuilder],
                                                                             RequestTemplateBuilder]:
        return lambda x: x.with_params(params)

    @staticmethod
    def get_context_path_modifier(context_path: str) -> Callable[[RequestTemplateBuilder], RequestTemplateBuilder]:
        return lambda x: x.with_context_path(context_path)

    @staticmethod
    def get_header_modifier(headers: Dict[str, str]) -> Callable[[RequestTemplateBuilder], RequestTemplateBuilder]:
        return lambda x: x.with_headers(headers)

    @staticmethod
    def get_body_modifier(body: str) -> Callable[[RequestTemplateBuilder], RequestTemplateBuilder]:
        return lambda x: x.with_body(body)

    @staticmethod
    def get_add_body_replace_values_modifier(replace_values: Dict[str, str]) -> Callable[[RequestTemplateBuilder],
                                                                                         RequestTemplateBuilder]:
        return lambda x: x.add_body_replace_values(replace_values)

    @staticmethod
    def get_http_method_modifier(http_method: str) -> Callable[[RequestTemplateBuilder], RequestTemplateBuilder]:
        return lambda x: x.with_http_method(http_method)

    def __init__(self, modifier: Callable[[RequestTemplateBuilder], RequestTemplateBuilder]):
        self.modifier = modifier

    def and_then(self, other_modifier: Callable[[RequestTemplateBuilder], RequestTemplateBuilder]) \
            -> 'RequestTemplateBuilderModifier':
        return RequestTemplateBuilderModifier(FunctionUtils
                                              .combine_callables(self.modifier, other_modifier))
