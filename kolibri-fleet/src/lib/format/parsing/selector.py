# Copyright 2023 Andreas Wagenmann
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
from typing import Optional

import jsonpath_ng

from src.lib.utils.json_utils import JsonUtils


class Selector:

    def __init__(self,
                 name: str,
                 path: jsonpath_ng.JSONPath):
        self.name = name
        self.path = path

    @staticmethod
    def selector_string_to_selector(name: str, selector_str: str) -> Optional['Selector']:
        """
        Helper method to convert name and an arbitrary concatenation of
        single value ("\\ a") or recursive selectors ("\\\\ a"), where a is to
        be replaced by the property to select, convert expression
        to json path and create selector. If path can not be converted,
        returns None
        :param name: name of the extracted attribute(s)
        :param selector_str: plain selector string to be converted to json path expression
        :return: Selector instance
        """
        try:
            path: jsonpath_ng.JSONPath = JsonUtils.convert_string_to_json_path(selector_str)
            return Selector(name, path) if path is not None else None
        except Exception as e:
            logging.error("Failed converting path '%s' to json path" % selector_str, e)
            return None
