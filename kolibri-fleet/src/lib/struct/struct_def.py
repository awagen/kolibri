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
import re
from abc import ABC
from itertools import chain
from typing import TypeVar, Generic, Callable, List, Dict, Optional, Tuple, Union

T = TypeVar('T')


class StructDef(ABC, Generic[T]):
    """
    abstract class to represent value formats.
    """

    def cast_value_is_valid(self, el: T) -> bool:
        """
        cast_value_is_valid is the validity check performed on the defined type.
        The function is used by the cast-function
        :param el:
        :return:
        """
        pass

    def cast(self, value: any) -> T | None:
        """
        If the value can be cast to the defined type and passes the check as imposed by cast_value_is_valid,
        return the value, otherwise return None
        :param value: the value to perform casting and validity check on
        :return: value of type T if castable and validation check passes, otherwise None
        """
        try:
            cast_value = T(value)
            is_valid = self.cast_value_is_valid(cast_value)
            return cast_value if is_valid else None
        except Exception as e:
            logging.warning("could not cast value '%s' to class '%s'" % (str(value), str(type(T))))
            return None


class FieldDef:

    def __init__(self,
                 name_format: StructDef[str],
                 value_format: StructDef[any],
                 required: bool,
                 description: str = ""):
        """
        Definition of fields. Only value format mandatory since this covers single fields that come with
        nameFormat and multi-fields that contain multiple single field definitions that need to occur in a nested
        structure (e.g Dict)
        :param name_format: format of the name (can be exact match on specified string or rather some pattern check,
        depending on chose struct def)
        :param value_format: expected format of the value
        :param required: flag whether this field is required or not
        :param description: (optional) description of the field
        """
        self.name_format = name_format
        self.value_format = value_format
        self.required = required
        self.description = description


class BaseStructDef(StructDef[T]):

    def __init__(self, validation_function: Callable[[T], bool]):
        super().__init__()
        self.validation_function = validation_function

    def cast_value_is_valid(self, el: T) -> bool:
        return self.validation_function(el)


class NestedStructDef(StructDef[Dict[str, T]]):
    fields: List[FieldDef]


class RegexStructDef(BaseStructDef[str]):

    def __init__(self, regex: re.Pattern):
        super().__init__(FunctionConversions.matches_regex(regex))
        self.regex = regex


class ListRegexStructDef(BaseStructDef[List[str]]):

    def __init__(self, regex: re.Pattern):
        super().__init__(FunctionConversions.all_match_regex(regex))
        self.regex = regex


class ChoiceStructDef(BaseStructDef[T]):

    def __init__(self, choices: List[T]):
        super().__init__(FunctionConversions.matches_one_of_choices(choices))
        self.choices = choices


class ListChoiceStructDef(BaseStructDef[List[T]]):

    def __init__(self, choices: List[T]):
        super().__init__(FunctionConversions.all_match_one_of_choices(choices))
        self.choices = choices


class MinMaxStructDef(BaseStructDef[Union[int, float]]):

    def __init__(self, min_val: Union[int, float], max_val: Union[int, float]):
        self.min_val = min_val
        self.max_val = max_val
        super().__init__(FunctionConversions.within_min_max(min_val, max_val))


class ListMinMaxStructDef(BaseStructDef[List[Union[int, float]]]):

    def __init__(self, min_val: Union[int, float], max_val: Union[int, float]):
        self.min_val = min_val
        self.max_val = max_val
        super().__init__(FunctionConversions.all_within_min_max(min_val, max_val))


class IntStructDef(BaseStructDef[int]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, int))


class StringStructDef(BaseStructDef[str]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, str))


class FloatStructDef(BaseStructDef[float]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, float))


class BooleanStructDef(BaseStructDef[bool]):

    def __init__(self):
        super.__init__(lambda x: isinstance(x, bool))


class ListIntStructDef(BaseStructDef[List[int]]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, List) and all([isinstance(y, int) for y in x]))


class ListStringStructDef(BaseStructDef[List[str]]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, List) and all([isinstance(y, str) for y in x]))


class ListFloatStructDef(BaseStructDef[float]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, List) and all([isinstance(y, float) for y in x]))


class ListBooleanStructDef(BaseStructDef[List[bool]]):

    def __init__(self):
        super().__init__(lambda x: isinstance(x, List) and all([isinstance(y, bool) for y in x]))


class StringConstantStructDef(BaseStructDef[str]):

    def __init__(self, value: str):
        self.value = value
        super().__init__(lambda x: x == value)


class MapStructDef(BaseStructDef[Dict[str, any]]):

    def __init__(self, key_format: StructDef[str], value_format: StructDef[any]):
        self.key_format = key_format
        self.value_format = value_format
        super().__init__(lambda x: FunctionConversions.all_keys_and_values_fulfill_formats(key_format, value_format, x))


class GenericListStructDef(BaseStructDef[List[any]]):

    def __init__(self, per_element_format: StructDef[any]):
        self.per_element_format = per_element_format
        super().__init__(lambda x: isinstance(x, List) and all([per_element_format.cast_value_is_valid(y) for y in x]))


class EitherOfStructDef(BaseStructDef[T]):

    def __init__(self, formats: List[StructDef[T]]):
        self.formats = formats
        super().__init__(lambda x: not FunctionConversions.get_value_if_any_format_matches(formats, x) is None)


class ConditionalFields:

    def __init__(self, conditional_field_id: str, mapping: Dict[str, List[FieldDef]]):
        self.conditional_field_id: str = conditional_field_id
        self.mapping: Dict[str, List[FieldDef]] = mapping

    def fields_for_conditional_value(self, conditional_field_value: str) -> List[FieldDef]:
        return self.mapping[conditional_field_value]


class NestedFieldListStructDef(NestedStructDef[any]):

    def __init__(self, fields: List[FieldDef], conditional_fields_list: List[ConditionalFields]):
        self.fields = fields
        self.conditional_fields_list = conditional_fields_list

    def _retrieve_conditional_fields(self, condition_values: Dict[str, any]) -> Union[str, List[FieldDef]]:
        fields_per_conditional_fields = list[list[FieldDef]] = []
        missing_fields: list[ConditionalFields] = []
        for index, cond_fields in enumerate(self.conditional_fields_list):
            cond_fields_value = condition_values[cond_fields.conditional_field_id]
            if cond_fields_value is None:
                fields_per_conditional_fields.append([])
                missing_fields.append(self.conditional_fields_list[index])
            fields_for_cond_value: list[FieldDef] = cond_fields.fields_for_conditional_value(str(cond_fields_value))
            fields_per_conditional_fields.append(fields_for_cond_value)

        if len(missing_fields) > 0:
            error_msg = ["current values: %s" % str(condition_values)]
            error_msg.extend(
                ["current values do not match conditions - conditionalField = '%s', conditionalValues = '%s'" %
                 (x.conditional_field_id, " / ".join(x.mapping.keys())) for x in missing_fields]
            )
            error_msg = "\n".join(error_msg)
            return error_msg
        return list(set(chain(*fields_per_conditional_fields)))

    def cast_value_is_valid(self, el: Dict[str, any]) -> bool:
        conditional_fields_or_error: str | list[FieldDef] = self._retrieve_conditional_fields(el)
        if not (isinstance(conditional_fields_or_error, List)
                and (len(conditional_fields_or_error) == 0 or isinstance(conditional_fields_or_error[0], FieldDef))):
            logging.warning("Conditional fields could not be retrieved from values '%s'" % str(el))
            return False
        needed_fields: List[FieldDef] = conditional_fields_or_error
        needed_fields.extend(self.fields)
        for field in needed_fields:
            all_matching_keys: list[str] = FunctionConversions.get_all_matching_keys_for_key_format(field, el)
            has_any_valid_value_match: bool = any([field.value_format.cast_value_is_valid(el[x])
                                                   for x in all_matching_keys])
            field_fulfilled: bool = (len(all_matching_keys) == 0 and not field.required) or has_any_valid_value_match
            if not field_fulfilled:
                return False
        return True

    def cast(self, value: any) -> Dict[str, any] | None:
        """
        Retrieve normal and conditional fields (depending on their condition values)
        and extract their values to a Dict
        :param value:
        :return:
        """
        all_fields: Dict[str, any] = value
        all_condition_values: Dict[str, str] = self._extract_values_for_conditional_fields(
            [x.conditional_field_id for x in self.conditional_fields_list],
            all_fields
        )
        conditional_fields_or_error = self._retrieve_conditional_fields(all_condition_values)
        if not (isinstance(conditional_fields_or_error, List)
                and (len(conditional_fields_or_error) == 0 or isinstance(conditional_fields_or_error[0], FieldDef))):
            logging.warning("could not cast value '%s' to expected conditional fields" % str(value))
            return None
        needed_fields: List[FieldDef] = conditional_fields_or_error
        needed_fields.extend(self.fields)
        return FunctionConversions.find_field_name_value_pairs(needed_fields, all_fields)


class ValidationResult(ABC):
    pass


class ValidationFail(ValidationResult):

    def __init__(self, field_name: str, reason: str):
        self.field_name = field_name
        self.reason = reason


class FunctionConversions:

    @staticmethod
    def to_any_input(func: Callable[[T], any]) -> Callable[[any], bool]:
        return lambda x: func(x) if isinstance(x, type(T)) else False

    @staticmethod
    def matches_regex(regex: re.Pattern) -> Callable[[str], bool]:
        return lambda x: regex.match(x) is not None

    @staticmethod
    def all_match_regex(regex: re.Pattern) -> Callable[[List[str]], bool]:
        return lambda x: all(FunctionConversions.matches_regex(regex)(y) for y in x)

    @staticmethod
    def matches_one_of_choices(choices: List[T]) -> Callable[[T], bool]:
        return lambda x: x in choices

    @staticmethod
    def all_match_one_of_choices(choices: List[T]) -> Callable[[List[T]], bool]:
        return lambda x: all(y in choices for y in x)

    @staticmethod
    def within_min_max(min_value: float | int, max_value: float | int) -> Callable[[float | int], bool]:
        return lambda x: min_value <= x <= max_value

    @staticmethod
    def all_within_min_max(min_value: float | int, max_value: float | int) -> Callable[[List[float | int]], bool]:
        return lambda x: all(FunctionConversions.within_min_max(min_value, max_value)(y) for y in x)

    @staticmethod
    def get_first_matching_key_for_key_format(field: FieldDef, values: Dict[str, any]) -> Optional[str]:
        """
        Given a field definition, if a matching key is found in the passed dict, return that key,
        otherwise None
        :param field: field definition
        :param values: Dict containing the key / value pairs
        :return: the matching str key or None
        """
        for key in values.keys():
            if field.name_format.cast_value_is_valid(key):
                return key
            return None

    @staticmethod
    def get_all_matching_keys_for_key_format(field: FieldDef, values: Dict[str, any]) -> List[str]:
        """
        Given a field definition, return all matching keys in the passed dict. Returns empty list
        if no key matches
        :param field: field definition
        :param values: Dict containing the key / value pairs
        :return: the list of matching str keys
        """
        matches = []
        for key in values.keys():
            if field.name_format.cast_value_is_valid(key):
                matches.append(key)
        return matches

    @staticmethod
    def can_be_cast_and_is_valid_format(struct_def: StructDef, element: any) -> bool:
        try:
            cast_value = struct_def.cast(element)
            return struct_def.cast_value_is_valid(cast_value)
        except Exception as e:
            logging.debug("Could not cast element '%s' with struct def '%s'" % (str(element), str(struct_def)), e)
            return False

    @staticmethod
    def value_can_be_cast(struct_def: StructDef, element: any) -> bool:
        try:
            struct_def.cast(element)
            return True
        except Exception as e:
            logging.debug("Could not cast element '%s' with struct def '%s'" % (str(element), str(struct_def)), e)
            return False

    @staticmethod
    def map_contains_key_with_valid_value(field: FieldDef, mapping: Dict[str, any]) -> bool:
        """
        Given a field definition and mapping, check if mapping contains a key corresponding to the field
        and whether the contained value is valid according to the field def validation
        :param field:
        :param mapping:
        :return:
        """
        found_key: Optional[str] = FunctionConversions.get_first_matching_key_for_key_format(field, mapping)
        if found_key is None:
            return False
        return field.value_format.cast_value_is_valid(mapping[found_key])

    @staticmethod
    def field_def_satisfied(field: FieldDef, mapping: Dict[str, any]) -> bool:
        """
        Given a field definition, check if that is satisfied in the passed mapping.
        If the passed field is marked as not required, the function will return true if the field is either not
        present or the value matches the field definition validation
        :param field: definition of field
        :param mapping: mapping to check for occurrence of field
        :return:
        """
        matching_key = FunctionConversions.get_first_matching_key_for_key_format(field, mapping)
        try:
            if not field.required:
                if matching_key is not None:
                    return field.value_format.cast_value_is_valid(mapping[matching_key])
                else:
                    return True
            else:
                return FunctionConversions.map_contains_key_with_valid_value(field, mapping)
        except Exception as e:
            logging.warning("field '%s' did not pass validation for given object '%s'" % (str(field), str(mapping)), e)
            return False

    @staticmethod
    def matches_value_map(fields: List[FieldDef]) -> Callable[[Dict[str, any]], bool]:
        return lambda x: all([FunctionConversions.field_def_satisfied(field, x) for field in fields])

    @staticmethod
    def failed_key_value_validation_by_result_and_type(key_format: StructDef[str],
                                                       value_format: StructDef[any],
                                                       mapping: Dict[str, any]) -> Tuple[List[str], List[str]]:
        """
        In the passed mapping, validate all keys against the key format and all values against the value format.
        Return those keys and values for which validation failed.
        :param key_format: the format all keys in the passed mapping need to adhere to to be seen as valid
        :param value_format: the format all values in the passed mapping need to adhere to to be seen as valid
        :param mapping: the mapping containing the key / value pairs
        :return: invalid_keys, invalid_values
        """
        invalid_keys = [key for key in mapping.keys() if not key_format.cast_value_is_valid(key)]
        invalid_values = [value for value in mapping.values() if not
            FunctionConversions.can_be_cast_and_is_valid_format(value_format, value)]
        for key in invalid_keys:
            logging.warning("wrong format for key '%s'" % key)
        for value in invalid_values:
            logging.warning("wrong format for value '%s'" % value)

        return invalid_keys, invalid_values

    @staticmethod
    def all_keys_and_values_fulfill_formats(key_format: StructDef[str], value_format: StructDef[any], mapping: Dict[str, any]) -> bool:
        invalid_keys, invalid_values = FunctionConversions.failed_key_value_validation_by_result_and_type(key_format,
                                                                                                          value_format,
                                                                                                          mapping)
        return len(invalid_keys) == 0 and len(invalid_values) == 0

    @staticmethod
    def get_value_if_any_format_matches(formats: List[StructDef[any]], value: any) -> Optional[any]:
        for struct_def in formats:
            is_valid = FunctionConversions.can_be_cast_and_is_valid_format(struct_def, value)
            if is_valid:
                return value
        return None, None

    @staticmethod
    def find_field_name_value_pairs(use_fields: List[FieldDef], data: Dict[str, any]) -> Dict[str, any]:
        value_dict = {}
        for field in use_fields:
            # find where name format of the field matches the key of the entry in data
            matching_key: str | None = FunctionConversions.get_first_matching_key_for_key_format(field, data)
            value_dict[matching_key] = field.value_format.cast(data[matching_key])
        return value_dict

    @staticmethod
    def each_field_def_matches_valid_value(use_fields: List[FieldDef], data: Dict[str, any]) -> Dict[str, any]:
        value_dict = {}
        for field in use_fields:
            # find where name format of the field matches the key of the entry in data
            matching_key: str | None = FunctionConversions.get_first_matching_key_for_key_format(field, data)
            value_dict[matching_key] = field.value_format.cast(data[matching_key])
        return value_dict

    @staticmethod
    def extract_values_for_field_names(field_names: List[str], field_name_value_map: Dict[str, any]) -> Dict[str, str]:
        """
        Extract values for each field_name from field_name_value_map. If key not existing, it will just be missing
        in the extracted result dict
        :param field_names: the keys of the values to extract from field_name_value_map
        :param field_name_value_map: mapping of field_names to values
        :return:
        """
        extracted_values = {}
        for field in field_names:
            field_value = field_name_value_map[field]
            if field_value is not None:
                extracted_values[field] = field_value
        return extracted_values




