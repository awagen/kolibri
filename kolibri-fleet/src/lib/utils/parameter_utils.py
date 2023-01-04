from typing import Dict, List


class ParameterUtils:

    @staticmethod
    def query_string_from_parameter_names_and_values(params: Dict[str, List[str]]):
        key_value_list = []
        for key, values in params.items():
            for value in values:
                key_value_list.append("%s=%s" % (key, value))
        return "&".join(key_value_list)
