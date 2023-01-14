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
import re
from abc import ABC, abstractmethod
from typing import Dict, TypeVar, Generic, List
import json

from src.lib.struct.struct_def import StructDef, IntStructDef, StringStructDef, FloatStructDef, BooleanStructDef, \
    ListIntStructDef, ListStringStructDef, RegexStructDef, StringConstantStructDef, ListRegexStructDef, ChoiceStructDef, \
    ListChoiceStructDef, MinMaxStructDef, ListMinMaxStructDef, NestedFieldListStructDef, MapStructDef, \
    EitherOfStructDef, GenericListStructDef, FieldDef, ConditionalFields


class JsonKeys:
    TYPE_KEY = "type"
    KEY_FORMAT_KEY = "keyFormat"
    VALUE_FORMAT_KEY = "valueFormat"
    DESCRIPTION_KEY = "description"
    REGEX_KEY = "regex"
    VALUE_KEY = "value"
    CHOICES_KEY = "choices"
    MIN_KEY = "min"
    MAX_KEY = "max"
    FIELDS_KEY = "fields"
    CONDITIONAL_FIELDS_SEQ_KEY = "conditionalFieldsSeq"
    NAME_FORMAT_KEY = "nameFormat"
    REQUIRED_KEY = "required"
    FORMATS_KEY = "formats"
    CONDITION_FIELD_ID_KEY = "conditionFieldId"
    CONDITIONAL_MAPPING_KEY = "mapping"
    PER_ELEMENT_FORMAT_KEY = "perElementFormat"


class StructDefTypes:
    INT_TYPE = "INT"
    STRING_TYPE = "STRING"
    DOUBLE_TYPE = "DOUBLE"
    FLOAT_TYPE = "FLOAT"
    BOOLEAN_TYPE = "BOOLEAN"
    INT_SEQ_TYPE = "INT_SEQ"
    STRING_SEQ_TYPE = "STRING_SEQ"
    REGEX_TYPE = "REGEX"
    STRING_CONSTANT_TYPE = "STRING_CONSTANT"
    SEQ_REGEX_TYPE = "SEQ_REGEX"
    CHOICE_INT_TYPE = "CHOICE_INT"
    CHOICE_FLOAT_TYPE = "CHOICE_FLOAT"
    CHOICE_DOUBLE_TYPE = "CHOICE_DOUBLE"
    CHOICE_STRING_TYPE = "CHOICE_STRING"
    SEQ_CHOICE_INT_TYPE = "SEQ_CHOICE_INT"
    SEQ_CHOICE_FLOAT_TYPE = "SEQ_CHOICE_FLOAT"
    SEQ_CHOICE_DOUBLE_TYPE = "SEQ_CHOICE_DOUBLE"
    SEQ_CHOICE_STRING_TYPE = "SEQ_CHOICE_STRING"
    SEQ_MIN_MAX_FLOAT_TYPE = "SEQ_MIN_MAX_FLOAT"
    SEQ_MIN_MAX_DOUBLE_TYPE = "SEQ_MIN_MAX_DOUBLE"
    SEQ_MIN_MAX_INT_TYPE = "SEQ_MIN_MAX_INT"
    NESTED_TYPE = "NESTED"
    MAP_TYPE = "MAP"
    EITHER_OF_TYPE = "EITHER_OF"
    CONDITIONAL_CHOICE_TYPE = "CONDITIONAL_CHOICE"
    GENERIC_SEQ_FORMAT_TYPE = "GENERIC_SEQ_FORMAT"
    MIN_MAX_INT_TYPE = "MIN_MAX_INT"
    MIN_MAX_FLOAT_TYPE = "MIN_MAX_FLOAT"
    MIN_MAX_DOUBLE_TYPE = "MIN_MAX_DOUBLE"


T = TypeVar('T')


class JsonFormat(ABC, Generic[T]):

    @staticmethod
    def dict_from_json_str(json_str: str) -> Dict[str, any]:
        return json.loads(json_str)

    @staticmethod
    def dict_to_json_str(mapping: Dict[str, any]) -> str:
        return json.dumps(mapping)

    @staticmethod
    @abstractmethod
    def from_dict(values: Dict[str, any]) -> T | None:
        pass

    @staticmethod
    @abstractmethod
    def to_dict(obj: T) -> Dict[str, any] | None:
        pass


class StructDefJsonFormat(JsonFormat[StructDef[any]]):

    @staticmethod
    def from_dict(values: Dict[str, any]) -> StructDef[any] | None:
        type_value = values[JsonKeys.TYPE_KEY]
        match type_value:
            case StructDefTypes.INT_TYPE:
                return IntStructDef()
            case StructDefTypes.STRING_TYPE:
                return StringStructDef()
            case StructDefTypes.DOUBLE_TYPE:
                return FloatStructDef()
            case StructDefTypes.FLOAT_TYPE:
                return FloatStructDef()
            case StructDefTypes.BOOLEAN_TYPE:
                return BooleanStructDef()
            case StructDefTypes.INT_SEQ_TYPE:
                return ListIntStructDef()
            case StructDefTypes.STRING_SEQ_TYPE:
                return ListStringStructDef()
            case StructDefTypes.STRING_CONSTANT_TYPE:
                value: str = values[JsonKeys.VALUE_KEY]
                return StringConstantStructDef(value)
            case StructDefTypes.MIN_MAX_INT_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return MinMaxStructDef(min_value, max_value)
            case StructDefTypes.MIN_MAX_FLOAT_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return MinMaxStructDef(min_value, max_value)
            case StructDefTypes.MIN_MAX_DOUBLE_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return MinMaxStructDef(min_value, max_value)
            case StructDefTypes.SEQ_MIN_MAX_INT_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return ListMinMaxStructDef(min_value, max_value)
            case StructDefTypes.SEQ_MIN_MAX_FLOAT_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return ListMinMaxStructDef(min_value, max_value)
            case StructDefTypes.SEQ_MIN_MAX_DOUBLE_TYPE:
                min_value = values[JsonKeys.MIN_KEY]
                max_value = values[JsonKeys.MAX_KEY]
                return ListMinMaxStructDef(min_value, max_value)
            case StructDefTypes.REGEX_TYPE:
                regex = values[JsonKeys.REGEX_KEY]
                return RegexStructDef(regex)
            case StructDefTypes.SEQ_REGEX_TYPE:
                regex = values[JsonKeys.REGEX_KEY]
                return ListRegexStructDef(regex)
            case StructDefTypes.CHOICE_INT_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ChoiceStructDef(choices)
            case StructDefTypes.CHOICE_STRING_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ChoiceStructDef(choices)
            case StructDefTypes.CHOICE_DOUBLE_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ChoiceStructDef(choices)
            case StructDefTypes.CHOICE_FLOAT_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ChoiceStructDef(choices)
            case StructDefTypes.SEQ_CHOICE_INT_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ListChoiceStructDef(choices)
            case StructDefTypes.SEQ_CHOICE_STRING_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ListChoiceStructDef(choices)
            case StructDefTypes.SEQ_CHOICE_FLOAT_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ListChoiceStructDef(choices)
            case StructDefTypes.SEQ_CHOICE_DOUBLE_TYPE:
                choices = values[JsonKeys.CHOICES_KEY]
                return ListChoiceStructDef(choices)
            case StructDefTypes.NESTED_TYPE:
                field_defs = [FieldDefJsonFormat.from_dict(x) for x in values[JsonKeys.FIELDS_KEY]]
                conditional_fields = [ConditionalFieldsJsonFormat.from_dict(x)
                                      for x in values[JsonKeys.CONDITIONAL_FIELDS_SEQ_KEY]]
                return NestedFieldListStructDef(field_defs, conditional_fields)
            case StructDefTypes.MAP_TYPE:
                key_format = StructDefJsonFormat.from_dict(values[JsonKeys.KEY_FORMAT_KEY])
                value_format = StructDefJsonFormat.from_dict(values[JsonKeys.VALUE_FORMAT_KEY])
                return MapStructDef(key_format, value_format)
            case StructDefTypes.EITHER_OF_TYPE:
                formats = values[JsonKeys.FORMATS_KEY]
                struct_defs = [StructDefJsonFormat.from_dict(x) for x in formats]
                return EitherOfStructDef(struct_defs)
            case StructDefTypes.GENERIC_SEQ_FORMAT_TYPE:
                struct_def = StructDefJsonFormat.from_dict(values[JsonKeys.PER_ELEMENT_FORMAT_KEY])
                return GenericListStructDef(struct_def)
            case _:
                return None

    @staticmethod
    def to_dict(obj: StructDef[any]) -> Dict[str, any] | None:
        if isinstance(obj, IntStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.INT_TYPE
            }
        if isinstance(obj, StringStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.STRING_TYPE
            }
        if isinstance(obj, FloatStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.DOUBLE_TYPE
            }
        if isinstance(obj, BooleanStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.BOOLEAN_TYPE
            }
        if isinstance(obj, ListIntStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.INT_SEQ_TYPE
            }
        if isinstance(obj, ListStringStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.STRING_SEQ_TYPE
            }
        if isinstance(obj, RegexStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.REGEX_TYPE,
                JsonKeys.REGEX_KEY: obj.regex
            }
        if isinstance(obj, StringConstantStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.STRING_CONSTANT_TYPE,
                JsonKeys.VALUE_KEY: obj.value
            }
        if isinstance(obj, ListRegexStructDef):
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.SEQ_REGEX_TYPE,
                JsonKeys.REGEX_KEY: obj.regex
            }
        if isinstance(obj, ChoiceStructDef):
            choices = obj.choices
            first_sample = choices[0]
            type_value = None
            if isinstance(first_sample, int):
                type_value = StructDefTypes.CHOICE_INT_TYPE
            elif isinstance(first_sample, float):
                type_value = StructDefTypes.CHOICE_DOUBLE_TYPE
            elif isinstance(first_sample, str):
                type_value = StructDefTypes.CHOICE_STRING_TYPE
            return {
                JsonKeys.TYPE_KEY: type_value,
                JsonKeys.CHOICES_KEY: choices
            }
        if isinstance(obj, ListChoiceStructDef):
            choices = obj.choices
            first_sample = choices[0]
            type_value = None
            if isinstance(first_sample, int):
                type_value = StructDefTypes.SEQ_CHOICE_INT_TYPE
            elif isinstance(first_sample, float):
                type_value = StructDefTypes.SEQ_CHOICE_DOUBLE_TYPE
            elif isinstance(first_sample, str):
                type_value = StructDefTypes.SEQ_CHOICE_STRING_TYPE
            return {
                JsonKeys.TYPE_KEY: type_value,
                JsonKeys.CHOICES_KEY: choices
            }
        if isinstance(obj, MinMaxStructDef):
            min_value = obj.min_val
            max_value = obj.max_val
            type_value = StructDefTypes.MIN_MAX_INT_TYPE
            if isinstance(min_value, float) or isinstance(max_value, float):
                type_value = StructDefTypes.MIN_MAX_DOUBLE_TYPE
            return {
                JsonKeys.TYPE_KEY: type_value,
                JsonKeys.MIN_KEY: min_value,
                JsonKeys.MAX_KEY: max_value
            }
        if isinstance(obj, ListMinMaxStructDef):
            min_value = obj.min_val
            max_value = obj.max_val
            type_value = StructDefTypes.SEQ_MIN_MAX_INT_TYPE
            if isinstance(min_value, float) or isinstance(max_value, float):
                type_value = StructDefTypes.SEQ_MIN_MAX_DOUBLE_TYPE
            return {
                JsonKeys.TYPE_KEY: type_value,
                JsonKeys.MIN_KEY: min_value,
                JsonKeys.MAX_KEY: max_value
            }
        if isinstance(obj, NestedFieldListStructDef):
            fields: list[FieldDef] = obj.fields
            conditional_fields: list[ConditionalFields] = obj.conditional_fields_list
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.NESTED_TYPE,
                JsonKeys.FIELDS_KEY: [FieldDefJsonFormat.to_dict(x) for x in fields],
                JsonKeys.CONDITIONAL_FIELDS_SEQ_KEY: [ConditionalFieldsJsonFormat.to_dict(x) for x in conditional_fields]
            }
        if isinstance(obj, MapStructDef):
            key_format = StructDefJsonFormat.to_dict(obj.key_format)
            value_format = StructDefJsonFormat.to_dict(obj.value_format)
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.MAP_TYPE,
                JsonKeys.KEY_FORMAT_KEY: key_format,
                JsonKeys.VALUE_FORMAT_KEY: value_format
            }
        if isinstance(obj, EitherOfStructDef):
            formats = [StructDefJsonFormat.to_dict(x) for x in obj.formats]
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.EITHER_OF_TYPE,
                JsonKeys.FORMATS_KEY: formats
            }
        if isinstance(obj, GenericListStructDef):
            per_element_format = StructDefJsonFormat.to_dict(obj.per_element_format)
            return {
                JsonKeys.TYPE_KEY: StructDefTypes.GENERIC_SEQ_FORMAT_TYPE,
                JsonKeys.PER_ELEMENT_FORMAT_KEY: per_element_format
            }
        return None


class FieldDefJsonFormat(JsonFormat[FieldDef]):

    @staticmethod
    def from_dict(values: Dict[str, any]) -> FieldDef | None:
        name_format: StructDef[str] = StructDefJsonFormat.from_dict(values[JsonKeys.NAME_FORMAT_KEY])
        value_format: StructDef[any] = StructDefJsonFormat.from_dict(values[JsonKeys.VALUE_FORMAT_KEY])
        required: bool = values[JsonKeys.REQUIRED_KEY]
        description: str = values[JsonKeys.DESCRIPTION_KEY]
        if description is None:
            description = ""
        if name_format is None or value_format is None:
            return None
        return FieldDef(name_format, value_format, required, description)

    @staticmethod
    def to_dict(obj: FieldDef) -> Dict[str, any] | None:
        return {
            JsonKeys.NAME_FORMAT_KEY: StructDefJsonFormat.to_dict(obj.name_format),
            JsonKeys.VALUE_FORMAT_KEY: StructDefJsonFormat.to_dict(obj.value_format),
            JsonKeys.REQUIRED_KEY: obj.required,
            JsonKeys.DESCRIPTION_KEY: obj.description
        }


class ConditionalFieldsJsonFormat(JsonFormat[ConditionalFields]):

    @staticmethod
    def from_dict(values: Dict[str, any]) -> ConditionalFields | None:
        conditional_field_id: str = values[JsonKeys.CONDITION_FIELD_ID_KEY]
        mappings: Dict[str, List[Dict[str, any]]] = values[JsonKeys.CONDITIONAL_MAPPING_KEY]
        field_mapping: Dict[str, List[FieldDef]] = {}
        for key, value in mappings.items():
            field_mapping[key] = [FieldDefJsonFormat.from_dict(x) for x in value]
        return ConditionalFields(conditional_field_id, field_mapping)

    @staticmethod
    def field_def_mapping_to_dict(mapping: Dict[str, List[FieldDef]]):
        result_dict = {}
        for key, value in mapping:
            result_dict[key] = [FieldDefJsonFormat.to_dict(x) for x in value]

    @staticmethod
    def to_dict(obj: ConditionalFields) -> Dict[str, any] | None:
        return {
            JsonKeys.CONDITION_FIELD_ID_KEY: obj.conditional_field_id,
            JsonKeys.CONDITIONAL_MAPPING_KEY: ConditionalFieldsJsonFormat.field_def_mapping_to_dict(obj.mapping)
        }
