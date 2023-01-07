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


from typing import TypeVar, Generic, Dict
from collections import OrderedDict

T = TypeVar("T")


class ProcessingState(Generic[T]):

    def __init__(self, data: T, previous_step_results: OrderedDict[str, Dict[str, any]]):
        """
        TODO: bake the context dict above in actual ProcessingResult object and just make the above a sequence
        of those.
        This class encapsulates the current data state along with previous step results (optional) and
        a context map to be able to pass information between steps
        :param data:
        :param previous_step_results:
        """

        self.data = data
