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

from typing import TypeVar, Callable, List, Tuple, Dict

from src.lib.values.generators.indexed_generator import IndexedGenerator
from src.lib.values.value_type import ValueType

T = TypeVar('T')


class SingleParameterValues:

    def __init__(self,
                 name: str,
                 value_type: ValueType,
                 values: Callable[[], IndexedGenerator[str]]):
        """
        Class representing parameter values,
        such as url parameters
        :param name: name of the parameter
        :param value_type: type of the parameter
        :param values: supplier of values for the parameter
        """
        self.name = name
        self.value_type = value_type
        self.values = values


class MappedParameterValues:

    def __init__(self,
                 name: str,
                 value_type: ValueType,
                 values: Callable[[], Dict[str, IndexedGenerator[str]]]):
        """
        Class representing value mappings to represent cases such as when valid parameter values depend on the
        value of another parameter (see ParameterValueMapping)
        :param name: name of the parameter
        :param value_type: type of the parameter
        :param values: supplier of mappings for the parameter
        """
        self.name = name
        self.value_type = value_type
        self.values = values


class ParameterValueMapping:
    # TODO: implement the generator logic based on the (key-source, value-source) assignments specified

    def __init__(self,
                 key_values: SingleParameterValues,
                 mapped_values: List[MappedParameterValues],
                 mapping_key_value_assignments: List[Tuple[int, int]]):
        """
        Class representing value mappings. the key_values represent the single unmapped values, while the valid mapped
        values can either depend on the key_values (source index 0) or any other mapped value that occurs in the
        mapped_values list before itself.
        :param key_values: the standalone key values
        :param mapped_values: definition of valid values per key value
        :param mapping_key_value_assignments: (key-source-index, value-source-index) assignments, where standalone
        key values have index 0, the first MappedParameterValues has index 1 and every next MappedParameterValues
        has index list-index + 1. Thus every mapped values can be either mapped to key values or to any mapped value
        that comes before it in the list
        """
        self.key_values = key_values
        self.mapped_values = mapped_values
        self.mapping_key_value_assignments = mapping_key_value_assignments
