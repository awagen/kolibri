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

from typing import Tuple

import jsonpath_ng
import re


class JsonUtils:
    """
    utils to simplify parsing of data from a json via jsonPath expressions.
    Note that we need to use the escaped variants below due to special meaning of backslash
    Utilizes jsonpath_ng library: https://github.com/h2non/jsonpath-ng
    """

    PLAIN_SELECTOR: str = "\\"
    RECURSIVE_SELECTOR: str = "\\\\"

    @staticmethod
    def get_next_selector_and_remaining_path(path: str) -> Tuple[str, str] | Tuple[None, None]:
        """
        Getting ta full path that starts with arbitrary sequence of "\" or "\\" (escaped "\\" or "\\\\") followed
        by some alphnumeric selector, get the very next json path selector
        :param path: the full path as per the current state
        :param selector: the selector that applies,
        :return:
        """
        path = path.strip()
        selector = None
        if path.startswith(JsonUtils.RECURSIVE_SELECTOR):
            selector = JsonUtils.RECURSIVE_SELECTOR
        elif path.startswith(JsonUtils.PLAIN_SELECTOR):
            selector = JsonUtils.PLAIN_SELECTOR
        if selector is None:
            return None, None
        remaining_path = path.lstrip(selector).strip()
        attribute = re.split("\\s+", remaining_path)[0]
        remaining_path = remaining_path.lstrip(attribute).lstrip()
        json_path_element = "[*].%s" % attribute if selector == JsonUtils.RECURSIVE_SELECTOR else ".%s" % attribute
        return json_path_element, remaining_path

    @staticmethod
    def convert_string_to_json_path(string_exp: str) -> jsonpath_ng.JSONPath:
        """
        Convert a selector expression such as "\ a \ b \\ c" to a json path selector
        :param string_exp: The passed string is supposed to have a shape of "\" for single selector, "\\" for
        recursive selector. Allows arbitrary combination of single and recursive selectors.
        :return:
        """
        json_path = "$"
        while string_exp is not None and string_exp != "":
            json_path_element, string_exp = JsonUtils.get_next_selector_and_remaining_path(string_exp)
            json_path = "%s%s" % (json_path, json_path_element)
        return jsonpath_ng.parse(json_path)


