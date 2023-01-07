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
